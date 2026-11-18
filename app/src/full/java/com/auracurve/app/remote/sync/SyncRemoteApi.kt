package com.auracurve.app.remote.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `GET /api/v0/system/sync` (Pixel-Car module 08): is the phone online now, when was it last,
 * and when were the library, the feed and the web remote last brought up to date. The web remote
 * shows this instead of pretending the car phone is always current.
 */
class SyncRemoteApi(
    private val times: SyncTimes,
    private val online: () -> Boolean,
    private val librarySyncedAtMs: suspend () -> Long?,
    private val feedUpdatedAtMs: suspend () -> Long?,
    private val webVersion: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun status(): JsonObject {
        val now = clock()
        val isOnline = online()
        if (isOnline) times.markOnline()
        val t = times.current()
        val lastOnline = if (isOnline) now else t.lastOnlineMs
        val offlineDays = if (isOnline || lastOnline == null) null else ((now - lastOnline) / DAY_MS).toInt()
        return buildJsonObject {
            put("online", isOnline)
            put("lastOnlineAt", iso(lastOnline))
            put("offlineDays", offlineDays)
            put("librarySyncedAt", iso(librarySyncedAtMs()))
            put("feedUpdatedAt", iso(feedUpdatedAtMs()))
            put("webCheckedAt", iso(t.webCheckedMs))
            put("webVersion", webVersion())
            // Assumption (E-014): Spotify wants to be online about every 30 days for downloads.
            put("warning", if (offlineDays != null && offlineDays >= WARN_OFFLINE_DAYS) "OFFLINE_LONG" else null)
        }
    }

    private fun iso(ms: Long?): String? = ms?.takeIf { it > 0 }?.let { ISO.format(Instant.ofEpochMilli(it).atZone(zone).withNano(0)) }

    companion object {
        const val WARN_OFFLINE_DAYS = 21
        private const val DAY_MS = 24 * 60 * 60_000L
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
