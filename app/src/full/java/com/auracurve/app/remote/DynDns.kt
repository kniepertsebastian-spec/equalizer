package com.auracurve.app.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fixed address via DuckDNS (19 Nov 2026). In the car the Pixel is a client in the hotspot of the
 * driver's phone, which picks a new address every time; `<name>.local` does not help there,
 * because the phone that is the hotspot does not ask its own clients. The browser keeps the
 * pairing per address, so the web remote needs a name that ordinary DNS resolves: the Pixel
 * writes its private address to `<name>.duckdns.org`, and the address in the browser stays the same.
 */
object DuckDns {
    const val SUFFIX = ".duckdns.org"
    private val NAME = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
    private val TOKEN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    /** "basti-auto", "basti-auto.duckdns.org" or a pasted link → "basti-auto"; null if it cannot be a subdomain. */
    fun normalizeName(raw: String): String? =
        raw
            .trim()
            .lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
            .removeSuffix(SUFFIX)
            .takeIf { NAME.matches(it) }

    /** The account token as DuckDNS shows it (a UUID); null otherwise. */
    fun normalizeToken(raw: String): String? = raw.trim().lowercase().takeIf { TOKEN.matches(it) }

    fun host(name: String): String = name + SUFFIX

    fun updateUrl(
        config: DynDnsConfig,
        ip: String,
    ): String = "https://www.duckdns.org/update?domains=${config.name}&token=${config.token}&ip=$ip"
}

/** The DuckDNS subdomain and its token, both already checked. */
data class DynDnsConfig(
    val name: String,
    val token: String,
) {
    val host: String get() = DuckDns.host(name)

    // The token must not end up in a log line or a crash report.
    override fun toString(): String = "DynDnsConfig($name)"
}

/** What the card shows: the address DuckDNS last confirmed, and whether the last try failed. */
data class DynDnsStatus(
    val ip: String? = null,
    val atMs: Long = 0L,
    val failed: Boolean = false,
)

/**
 * Writes the phone's private address to DuckDNS while the remote server runs: at once when the
 * address or the settings change, then again every hour (cheap, and it repairs a lost update).
 * Without internet a try fails and is repeated with growing pauses up to [RETRY_MAX_MS].
 */
@Singleton
class DynDnsUpdater internal constructor(
    private val fetch: suspend (String) -> String,
    private val clock: () -> Long,
) {
    @Inject
    constructor() : this(::httpGet, System::currentTimeMillis)

    private val _status = MutableStateFlow(DynDnsStatus())
    val status: StateFlow<DynDnsStatus> = _status.asStateFlow()

    /** One update; true if DuckDNS answered OK. */
    suspend fun update(
        config: DynDnsConfig,
        ip: String,
    ): Boolean {
        val ok = runCatching { fetch(DuckDns.updateUrl(config, ip)).trim() == "OK" }.getOrDefault(false)
        _status.value = if (ok) DynDnsStatus(ip, clock(), failed = false) else _status.value.copy(failed = true)
        return ok
    }

    /** Runs until cancelled (the server stops). */
    suspend fun keepUpdated(
        configs: Flow<DynDnsConfig?>,
        addresses: Flow<String?>,
    ) {
        combine(configs, addresses) { config, address -> config to address }
            .distinctUntilChanged()
            .collectLatest { (config, address) ->
                if (config == null) {
                    _status.value = DynDnsStatus()
                    return@collectLatest
                }
                if (address == null) return@collectLatest
                var wait = RETRY_FIRST_MS
                while (true) {
                    if (update(config, address)) {
                        wait = RETRY_FIRST_MS
                        delay(REFRESH_MS)
                    } else {
                        delay(wait)
                        wait = (wait * 2).coerceAtMost(RETRY_MAX_MS)
                    }
                }
            }
    }

    companion object {
        const val RETRY_FIRST_MS = 15_000L
        const val RETRY_MAX_MS = 5 * 60_000L
        const val REFRESH_MS = 60 * 60_000L
        private const val TIMEOUT_MS = 10_000
        private const val MAX_ANSWER_BYTES = 64

        private suspend fun httpGet(url: String): String =
            withContext(Dispatchers.IO) {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.instanceFollowRedirects = false
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext ""
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(MAX_ANSWER_BYTES)
                        val read = input.read(buffer)
                        if (read > 0) String(buffer, 0, read, Charsets.UTF_8) else ""
                    }
                } catch (e: Exception) {
                    // Only the type: the URL holds the token.
                    Log.w("DynDns", "Update failed: ${e.javaClass.simpleName}")
                    ""
                } finally {
                    connection.disconnect()
                }
            }
    }
}
