package com.hardbasseq.eq.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Volume factors (0..1) for the built-in player's fades (roadmap 06, P5-6). Pure arithmetic so the
 * service only has to apply the number it gets.
 *
 * The curves are equal-power quarter sine/cosine waves: a linear ramp sounds like it drops away
 * fast at the start of a fade-out and arrives late at the end of a fade-in.
 */
object PlaybackFade {
    /** Longest fade between tracks offered to the user. */
    const val MAX_TRACK_FADE_MS = 12_000L

    /** How long the volume is lowered before the sleep timer pauses playback. */
    const val SLEEP_FADE_MS = 15_000L

    /** Fade-in of a rising edge, [progress] 0..1. */
    fun fadeIn(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * (PI / 2).toFloat())

    /** Fade-out of a falling edge, [progress] 0..1. */
    fun fadeOut(progress: Float): Float = cos(progress.coerceIn(0f, 1f) * (PI / 2).toFloat())

    /**
     * The factor for a track at [positionMs] of [durationMs] when each edge fades over [fadeMs].
     * 1 in the middle. A track shorter than two fades gets half of its length per edge, so the two
     * ramps never overlap; an unknown duration ([durationMs] <= 0) only fades in.
     */
    fun trackEdgeGain(
        positionMs: Long,
        durationMs: Long,
        fadeMs: Long,
    ): Float {
        if (fadeMs <= 0L) return 1f
        val known = durationMs > 0L
        val fade = if (known) min(fadeMs, durationMs / 2) else fadeMs
        if (fade <= 0L) return 1f
        val position = positionMs.coerceAtLeast(0L)
        val inGain = if (position < fade) fadeIn(position.toFloat() / fade) else 1f
        val untilEnd = durationMs - position
        val outGain = if (known && untilEnd < fade) fadeOut(1f - untilEnd.toFloat() / fade) else 1f
        return min(inGain, outGain)
    }

    /** The factor while a sleep timer has [remainingMs] left: 1 until the last [fadeMs], then down to 0. */
    fun sleepGain(
        remainingMs: Long,
        fadeMs: Long = SLEEP_FADE_MS,
    ): Float {
        if (remainingMs <= 0L) return 0f
        if (fadeMs <= 0L || remainingMs >= fadeMs) return 1f
        return fadeOut(1f - remainingMs.toFloat() / fadeMs)
    }
}

/** What the sleep timer is set to. */
sealed interface SleepTimerSetting {
    data class AfterMinutes(
        val minutes: Int,
    ) : SleepTimerSetting

    /** Pauses when the track that plays now ends. */
    data object EndOfTrack : SleepTimerSetting

    companion object {
        val PRESET_MINUTES = listOf(15, 30, 45, 60, 90)
        const val MAX_MINUTES = 12 * 60
    }
}

/** What a screen shows about a running sleep timer. */
data class SleepTimerStatus(
    /** Time left, or null for [SleepTimerSetting.EndOfTrack]. */
    val remainingMs: Long?,
    val endOfTrack: Boolean,
)
