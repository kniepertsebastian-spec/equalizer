package com.hardbasseq.eq.data.preset

import com.hardbasseq.eq.preset.Preset
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Turns a preset into a short link (`hardbasseq://preset?v=1&d=...`) and back, so it can be sent in
 * a message or shown as a QR code (roadmap 06, P5-4).
 *
 * The payload is the preset's JSON, deflated and base64url-encoded. Decoding is the untrusted side:
 * the inflated size is capped before anything is parsed, and the result goes through the same
 * validation as a preset imported from a file ([PresetJsonSerializer.importFromJson]).
 */
object PresetShareCodec {
    const val SCHEME = "hardbasseq"
    const val HOST = "preset"
    private const val VERSION = 1
    private const val MAX_INFLATED_BYTES = 100 * 1024

    // A QR code at its practical scan limit holds roughly 1.2 kB; longer links are still valid text.
    const val QR_FRIENDLY_MAX_CHARS = 1_200

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    fun isShareLink(text: String): Boolean = text.trim().startsWith("$SCHEME://$HOST", ignoreCase = true)

    fun encode(preset: Preset): String {
        // Built-in status and identity are the receiver's business, not part of what is shared.
        val shared = preset.copy(id = "", metadata = preset.metadata.copy(builtIn = false))
        val raw = json.encodeToString(Preset.serializer(), shared).toByteArray(Charsets.UTF_8)
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(raw))
        return "$SCHEME://$HOST?v=$VERSION&d=$payload"
    }

    /** A fresh, user-owned copy of the shared preset (new id, never built-in), or the reason it is not one. */
    fun decode(link: String): Result<Preset> =
        runCatching {
            val trimmed = link.trim()
            require(isShareLink(trimmed)) { "Not a HardBass EQ preset link" }
            val query = trimmed.substringAfter('?', "")
            val params =
                query
                    .split('&')
                    .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                    .toMap()
            val version = params["v"]?.toIntOrNull() ?: throw IllegalArgumentException("Preset link has no version")
            require(version == VERSION) { "Unsupported preset link version: $version" }
            val payload = params["d"] ?: throw IllegalArgumentException("Preset link has no data")
            val raw = inflate(Base64.getUrlDecoder().decode(payload))
            val preset = PresetJsonSerializer.importFromJson(raw.toString(Charsets.UTF_8)).getOrThrow()
            preset.copy(id = UUID.randomUUID().toString(), metadata = preset.metadata.copy(builtIn = false))
        }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer))
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count =
                    try {
                        inflater.inflate(buffer)
                    } catch (e: DataFormatException) {
                        throw IllegalArgumentException("Preset link data is corrupted", e)
                    }
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw IllegalArgumentException("Preset link data is truncated")
                }
                out.write(buffer, 0, count)
                require(out.size() <= MAX_INFLATED_BYTES) { "Preset link data is too large" }
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
