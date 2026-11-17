package com.auracurve.app.remote.playback

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Where the playback endpoints act (contract 3.1 `activeSource`). SPOTIFY_SECOND is refused in v0. */
enum class RemoteSource { PLAYER, SPOTIFY_MAIN, SPOTIFY_SECOND }

/** STREAM_MUSIC as the contract has it: 0 … [max], the maximum comes from the device. */
data class VolumeLevel(
    val level: Int,
    val max: Int,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("level", level)
            put("max", max)
        }

    /** The level `{"level":n}` or `{"step":n}` asks for, clamped to 0 … [max] like the devserver. */
    fun target(
        level: Int?,
        step: Int?,
    ): Int = (level ?: (this.level + (step ?: 0))).coerceIn(0, max)
}

/**
 * `GET /api/v0/playback/now-playing` (contract 3.2). [shuffle] and [repeat] are null where the
 * source cannot report or change them (Spotify without the custom actions): they are left out then.
 */
data class NowPlayingSnapshot(
    val source: RemoteSource,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val shuffle: Boolean?,
    val repeat: String?,
    val volume: VolumeLevel,
    val coverUrl: String? = null,
) {
    /** What playback.changed is sent for: a new track or play/pause, not every position step. */
    val changeKey: List<Any?> get() = listOf(source, title, artist, isPlaying, shuffle, repeat, durationMs)

    fun toJson(): JsonObject =
        buildJsonObject {
            put("source", source.name)
            put("title", title)
            put("artist", artist)
            put("isPlaying", isPlaying)
            put("positionMs", positionMs)
            put("durationMs", durationMs)
            shuffle?.let { put("shuffle", it) }
            repeat?.let { put("repeat", it) }
            putJsonObject("volume") {
                put("level", volume.level)
                put("max", volume.max)
            }
            coverUrl?.let { put("coverUrl", it) }
        }

    companion object {
        /**
         * The position of a MediaSession right now: Android reports it at [updateTimeMs]
         * (elapsedRealtime) and lets it run at [speed] while playing.
         */
        fun sessionPosition(
            reportedMs: Long,
            updateTimeMs: Long,
            speed: Float,
            playing: Boolean,
            nowMs: Long,
            durationMs: Long,
        ): Long {
            if (reportedMs < 0) return 0L
            val advanced = if (playing && updateTimeMs > 0) reportedMs + ((nowMs - updateTimeMs) * speed).toLong() else reportedMs
            return if (durationMs > 0) advanced.coerceIn(0L, durationMs) else advanced.coerceAtLeast(0L)
        }

        /** PlaybackState.STATE_* that count as playing (as the devserver: playing, buffering, connecting). */
        fun isPlayingState(state: Int): Boolean = state == STATE_PLAYING || state == STATE_BUFFERING || state == STATE_CONNECTING

        // android.media.session.PlaybackState values, kept here so the mapping is testable on the JVM.
        const val STATE_PLAYING = 3
        const val STATE_BUFFERING = 6
        const val STATE_CONNECTING = 8
    }
}
