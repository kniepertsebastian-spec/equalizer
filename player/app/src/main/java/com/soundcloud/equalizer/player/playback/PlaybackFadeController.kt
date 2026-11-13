package com.soundcloud.equalizer.player.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.hardbasseq.eq.playback.PlaybackFade
import com.hardbasseq.eq.playback.SleepTimerSetting
import com.hardbasseq.eq.playback.SleepTimerStatus

/**
 * The sleep timer and the fades at the edges of tracks (roadmap 06, P5-6). Both only ever change
 * the player's own volume ([ExoPlayer.setVolume], relative to the system volume) and pause it; the
 * service calls [tick] while [needsTicks] and does nothing else with it.
 */
@UnstableApi
class PlaybackFadeController(
    private val player: ExoPlayer,
    private val elapsedRealtimeMs: () -> Long,
    private val publish: (SleepTimerStatus?) -> Unit,
    private val pause: () -> Unit,
) {
    /** Fade length at both edges of every track in ms, 0 = off. */
    var trackFadeMs: Long = 0L

    private var sleepDeadlineMs: Long? = null
    private var sleepEndOfTrack = false
    private var lastPublishedSecond = Long.MIN_VALUE
    private var volumeTouched = false

    val sleepTimerActive: Boolean get() = sleepDeadlineMs != null || sleepEndOfTrack

    /** True while the owner has to keep calling [tick]. */
    val needsTicks: Boolean get() = sleepTimerActive || (trackFadeMs > 0L && player.isPlaying)

    fun startSleepTimer(setting: SleepTimerSetting) {
        cancelSleepTimer()
        when (setting) {
            is SleepTimerSetting.AfterMinutes -> {
                val minutes = setting.minutes.coerceIn(1, SleepTimerSetting.MAX_MINUTES)
                sleepDeadlineMs = elapsedRealtimeMs() + minutes * 60_000L
            }
            SleepTimerSetting.EndOfTrack -> {
                sleepEndOfTrack = true
                // ExoPlayer pauses by itself exactly at the end of the item, without a gap in front.
                player.pauseAtEndOfMediaItems = true
            }
        }
        tick()
    }

    fun cancelSleepTimer() {
        sleepDeadlineMs = null
        if (sleepEndOfTrack) player.pauseAtEndOfMediaItems = false
        sleepEndOfTrack = false
        lastPublishedSecond = Long.MIN_VALUE
        publish(null)
        applyVolume()
    }

    /** ExoPlayer paused itself at the end of a track: that was the timer if it was an end-of-track one. */
    fun onPausedAtEndOfItem() {
        if (sleepEndOfTrack) finishSleepTimer(pausePlayer = false)
    }

    fun tick() {
        val deadline = sleepDeadlineMs
        if (deadline != null && deadline - elapsedRealtimeMs() <= 0L) {
            finishSleepTimer(pausePlayer = true)
            return
        }
        publishStatus()
        applyVolume()
    }

    /** Back to normal volume, for example when the player is released or playback is stopped. */
    fun release() {
        sleepDeadlineMs = null
        sleepEndOfTrack = false
        publish(null)
        player.volume = 1f
    }

    private fun finishSleepTimer(pausePlayer: Boolean) {
        sleepDeadlineMs = null
        if (sleepEndOfTrack) player.pauseAtEndOfMediaItems = false
        sleepEndOfTrack = false
        lastPublishedSecond = Long.MIN_VALUE
        if (pausePlayer) pause()
        publish(null)
        // Full volume again before the next play, so it does not start silent.
        player.volume = 1f
        volumeTouched = false
    }

    private fun remainingMs(): Long? {
        sleepDeadlineMs?.let { return (it - elapsedRealtimeMs()).coerceAtLeast(0L) }
        if (!sleepEndOfTrack) return null
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return null
        return (duration - player.currentPosition).coerceAtLeast(0L)
    }

    private fun publishStatus() {
        if (!sleepTimerActive) return
        val shown = if (sleepEndOfTrack) null else remainingMs()
        // Once a second is enough for a countdown; the value only matters to the person reading it.
        val second = shown?.div(1000L) ?: -1L
        if (second == lastPublishedSecond) return
        lastPublishedSecond = second
        publish(SleepTimerStatus(remainingMs = shown, endOfTrack = sleepEndOfTrack))
    }

    private fun applyVolume() {
        val edge =
            if (trackFadeMs > 0L) {
                PlaybackFade.trackEdgeGain(player.currentPosition, player.duration.takeIf { it != C.TIME_UNSET } ?: 0L, trackFadeMs)
            } else {
                1f
            }
        val sleep = remainingMs()?.let { PlaybackFade.sleepGain(it) } ?: 1f
        val gain = minOf(edge, sleep)
        if (gain < 1f) {
            volumeTouched = true
            player.volume = gain
        } else if (volumeTouched) {
            volumeTouched = false
            player.volume = 1f
        }
    }
}
