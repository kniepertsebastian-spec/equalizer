package com.auracurve.app.remote.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * When the phone last had internet and when the web remote was last checked (Pixel-Car module
 * 08). The car phone has no SIM: it is online only at home, in the Wi-Fi. Spotify's downloads
 * are believed to need it online about once in 30 days (assumption, E-014), so the web remote
 * shows how long it has been. Kept in [file] so a restart does not forget it. Thread-safe.
 */
class SyncTimes(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Times(
        val lastOnlineMs: Long? = null,
        val webCheckedMs: Long? = null,
    )

    @Volatile
    private var times: Times = load()

    fun current(): Times = times

    /** Called whenever the phone is seen online; written at most once a minute. */
    @Synchronized
    fun markOnline() {
        val now = clock()
        val last = times.lastOnlineMs
        if (last != null && now - last < WRITE_GAP_MS && now >= last) return
        save(times.copy(lastOnlineMs = now))
    }

    /** The release repository answered (newer bundle or not). */
    @Synchronized
    fun markWebChecked() {
        save(times.copy(webCheckedMs = clock()))
    }

    private fun save(updated: Times) {
        times = updated
        runCatching {
            val json =
                buildJsonObject {
                    updated.lastOnlineMs?.let { put("lastOnlineMs", it) }
                    updated.webCheckedMs?.let { put("webCheckedMs", it) }
                }
            file.parentFile?.mkdirs()
            val temporary = File(file.path + ".tmp")
            temporary.writeText(json.toString())
            temporary.renameTo(file)
        }
    }

    private fun load(): Times =
        runCatching {
            val o = Json.parseToJsonElement(file.readText()).jsonObject
            Times(o["lastOnlineMs"]?.jsonPrimitive?.longOrNull, o["webCheckedMs"]?.jsonPrimitive?.longOrNull)
        }.getOrDefault(Times())

    private companion object {
        const val WRITE_GAP_MS = 60_000L
    }
}
