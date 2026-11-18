package com.auracurve.app.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/** A paired phone. The app keeps only a hash of its token; the token itself exists only on that phone. */
data class RemoteDevice(
    val id: String,
    val name: String,
    val tokenHash: String,
    val pairedAtMs: Long,
)

/** The code shown in the app for pairing one phone: valid until [expiresAtMs] and only once. */
data class PairingCode(
    val code: String,
    val expiresAtMs: Long,
) {
    /** "ABCD-EFGH", easier to read and type. */
    val display: String get() = code.chunked(4).joinToString("-")
}

class Paired(
    val device: RemoteDevice,
    val token: String,
)

/**
 * The paired devices of the remote control (Pixel-Car module 05, E-017): every phone gets its own
 * token by entering a one-time code shown in the app, and every one can be removed on its own.
 * Removing one ends its open WebSocket too ([revoked]).
 *
 * Kept as JSON in [file] (the no-backup directory: a restored phone answers to no old token).
 * Thread-safe; [clock] and the random sources are injectable for tests.
 */
class RemoteDevices(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newToken: () -> String = RemoteToken::generate,
    private val newCode: () -> String = ::randomCode,
    private val newId: () -> String = { RemoteToken.randomUrlSafe(6) },
) {
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<RemoteDevice>> = _devices.asStateFlow()

    private val _pairing = MutableStateFlow<PairingCode?>(null)

    /** The code currently shown, or null. Expired codes are dropped on the next call. */
    val pairing: StateFlow<PairingCode?> = _pairing.asStateFlow()

    private val _revoked = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Ids of removed devices ([ALL] for all at once), so their open connections can be closed. */
    val revoked: SharedFlow<String> = _revoked

    /** The device a bearer token belongs to, or null. Compares every hash, in constant time each. */
    fun authenticate(token: String?): RemoteDevice? {
        if (token.isNullOrEmpty() || token.length > MAX_TOKEN_LENGTH) return null
        val hash = hash(token).toByteArray()
        var found: RemoteDevice? = null
        for (device in _devices.value) {
            if (MessageDigest.isEqual(hash, device.tokenHash.toByteArray()) && found == null) found = device
        }
        return found
    }

    /** A new code; an older one stops working. */
    @Synchronized
    fun startPairing(): PairingCode = PairingCode(newCode(), clock() + CODE_TTL_MS).also { _pairing.value = it }

    @Synchronized
    fun cancelPairing() {
        _pairing.value = null
    }

    /** Drops the code once it has expired (the app's dialog follows [pairing]). */
    @Synchronized
    fun expirePairing() {
        val current = _pairing.value ?: return
        if (clock() >= current.expiresAtMs) _pairing.value = null
    }

    /**
     * A new device for the right code, or null for a wrong, used or expired one. The code is used
     * up either way once it matched. Throws [TooManyDevices] when [MAX_DEVICES] are paired.
     */
    @Synchronized
    fun pair(
        code: String?,
        name: String?,
    ): Paired? {
        expirePairing()
        val current = _pairing.value ?: return null
        val sent = normalizeCode(code) ?: return null
        if (!MessageDigest.isEqual(sent.toByteArray(), current.code.toByteArray())) return null
        _pairing.value = null
        if (_devices.value.size >= MAX_DEVICES) throw TooManyDevices()
        val token = newToken()
        val device = RemoteDevice(newId(), cleanName(name), hash(token), clock())
        save(_devices.value + device)
        return Paired(device, token)
    }

    /** Takes over the single token of app versions before module 05, so the phone paired with it keeps working. */
    @Synchronized
    fun importLegacyToken(
        token: String,
        name: String,
    ) {
        if (authenticate(token) != null) return
        save(_devices.value + RemoteDevice(newId(), cleanName(name), hash(token), clock()))
    }

    @Synchronized
    fun revoke(id: String) {
        if (_devices.value.none { it.id == id }) return
        save(_devices.value.filterNot { it.id == id })
        _revoked.tryEmit(id)
    }

    @Synchronized
    fun revokeAll() {
        save(emptyList())
        _pairing.value = null
        _revoked.tryEmit(ALL)
    }

    private fun save(list: List<RemoteDevice>) {
        val json =
            buildJsonObject {
                put(
                    "devices",
                    buildJsonArray {
                        list.forEach { d ->
                            add(
                                buildJsonObject {
                                    put("id", d.id)
                                    put("name", d.name)
                                    put("tokenSha256", d.tokenHash)
                                    put("pairedAtMs", d.pairedAtMs)
                                },
                            )
                        }
                    },
                )
            }
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        temporary.writeText(json.toString())
        check(temporary.renameTo(file)) { "Geräteliste nicht gespeichert" }
        _devices.value = list
    }

    private fun load(): List<RemoteDevice> =
        runCatching {
            Json
                .parseToJsonElement(file.readText())
                .jsonObject["devices"]!!
                .jsonArray
                .mapNotNull { element ->
                    val o = element as? JsonObject ?: return@mapNotNull null

                    fun text(key: String) = o[key]?.jsonPrimitive?.contentOrNull
                    RemoteDevice(
                        id = text("id") ?: return@mapNotNull null,
                        name = text("name") ?: DEFAULT_NAME,
                        tokenHash = text("tokenSha256") ?: return@mapNotNull null,
                        pairedAtMs = o["pairedAtMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                    )
                }
        }.getOrDefault(emptyList())

    class TooManyDevices : Exception()

    companion object {
        const val ALL = "*"
        const val CODE_TTL_MS = 5 * 60_000L
        const val MAX_DEVICES = 10
        const val CODE_LENGTH = 8
        const val MAX_NAME_LENGTH = 40
        const val DEFAULT_NAME = "Gerät"
        private const val MAX_TOKEN_LENGTH = 200

        // No 0/O, 1/I/L: the code is read off one screen and typed on another. 31 signs, 8 of them: about 40 bits,
        // against at most 10 guesses a minute per address (AuthRateLimiter) for 5 minutes.
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun randomCode(): String = (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

        /** Upper case without dashes and spaces; null if it cannot be a code. */
        fun normalizeCode(code: String?): String? {
            val clean = code?.uppercase()?.filterNot { it == '-' || it == ' ' } ?: return null
            return clean.takeIf { it.length == CODE_LENGTH && it.all { c -> c in ALPHABET } }
        }

        /** Printable, at most [MAX_NAME_LENGTH] characters; empty becomes [DEFAULT_NAME]. */
        fun cleanName(name: String?): String =
            name
                ?.filterNot { it.isISOControl() }
                ?.trim()
                ?.take(MAX_NAME_LENGTH)
                ?.takeIf { it.isNotEmpty() } ?: DEFAULT_NAME

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
