package com.auracurve.app.ui.components.cyber

import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.equalizer.CurveGeometry
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

// The moving pictures of the Cyber look. None of them is a measurement: the app has no level or
// spectrum data for what is playing and records nothing (no RECORD_AUDIO). They are shaped by
// what is known - the EQ curve, the track - and moved by a clock, deterministically, so a
// screenshot test sees the same frame every time. The labels never call them a measurement.

/** A small, fast, seeded random source (SplitMix64), the same on every device and run. */
internal class SeededRandom(
    seed: Long,
) {
    private var state = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in 0 until 1. */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24).toFloat()
}

private const val TWO_PI = (2.0 * PI).toFloat()

/** Beats per second of the made-up groove behind the bars and rays: 150 BPM. */
private const val BEATS_PER_SECOND = 150f / 60f

/**
 * Spectrum bars behind the EQ curve (decision E2). At rest each bar's height follows the EQ
 * curve at its frequency over a falling, music-like tilt, so a boost visibly lifts its bars. While
 * the EQ is active and a track plays, [animate] moves them around that rest height: a slow
 * per-bar wobble, a kick on the low bars and a lighter hat on the high ones.
 */
internal class SyntheticSpectrum(
    val barCount: Int = 48,
    seed: Long = 0x5EED_BA25L,
) {
    /** The bars' fraction of the log axis, at the middle of each bar. */
    val fractions = FloatArray(barCount) { (it + 0.5f) / barCount }

    /** The bars' center frequencies, ascending from 20 Hz to 20 kHz. */
    val frequencies = FloatArray(barCount) { CurveGeometry.frequencyAt(fractions[it]) }

    private val grain: FloatArray
    private val phases: FloatArray
    private val speeds: FloatArray

    init {
        val random = SeededRandom(seed)
        grain = FloatArray(barCount) { random.nextFloat() }
        phases = FloatArray(barCount) { random.nextFloat() * TWO_PI }
        speeds = FloatArray(barCount) { 1.6f + random.nextFloat() * 2.4f }
    }

    /**
     * Rest heights 0..1 into [out] from the EQ [gains] at [frequencies] on a ±[scaleDb] scale.
     * 0 dB is half the level; the tilt makes highs lower than lows, like most music.
     */
    fun shape(
        gains: FloatArray,
        scaleDb: Float,
        out: FloatArray,
    ) {
        val scale = scaleDb.coerceAtLeast(1f)
        for (i in 0 until barCount) {
            val level = ((gains[i] + scale) / (2f * scale)).coerceIn(0f, 1f)
            val tilt = 0.45f + 0.4f * (1f - fractions[i])
            out[i] = (level * tilt * (0.85f + 0.15f * grain[i])).coerceIn(0f, 1f)
        }
    }

    /** Rest heights for an EQ curve. Allocates; call it when the curve changes, not per frame. */
    fun rest(
        points: List<TargetPoint>,
        scaleDb: Float,
    ): FloatArray {
        val gains = FloatArray(barCount) { if (points.isEmpty()) 0f else CurveGeometry.gainAt(points, frequencies[it]) }
        return FloatArray(barCount).also { shape(gains, scaleDb, it) }
    }

    /** The bars at [seconds] around their [rest] heights into [out]. No allocation. */
    fun animate(
        rest: FloatArray,
        seconds: Float,
        out: FloatArray,
    ) {
        val beat = seconds * BEATS_PER_SECOND
        val kick = exp(-(beat - floor(beat)) * 6f)
        val offBeat = beat + 0.5f
        val hat = exp(-(offBeat - floor(offBeat)) * 10f)
        for (i in 0 until barCount) {
            val f = fractions[i]
            val wobble = 0.14f * sin(seconds * speeds[i] + phases[i]) + 0.06f * sin(seconds * 0.7f + f * 9f)
            val bass = (1f - f / 0.25f).coerceAtLeast(0f)
            val treble = ((f - 0.7f) / 0.3f).coerceIn(0f, 1f)
            val r = rest[i]
            out[i] = (r * (0.8f + wobble) + r * (0.45f * kick * bass + 0.22f * hat * treble)).coerceIn(0f, 1f)
        }
    }
}

/**
 * Rays around the cover in Now Playing (decision E3's companion): seeded per track so each track
 * has its own crown, moved by the same groove as the spectrum while it plays.
 */
internal class SyntheticRays(
    val count: Int = 96,
    seed: Long,
) {
    private val base: FloatArray
    private val phases: FloatArray
    private val speeds: FloatArray

    init {
        val random = SeededRandom(seed)
        // A few long spikes over a field of short rays, like the mockup's crown.
        base =
            FloatArray(count) {
                val r = random.nextFloat()
                if (r > 0.9f) 0.75f + random.nextFloat() * 0.25f else 0.18f + r * 0.4f
            }
        phases = FloatArray(count) { random.nextFloat() * TWO_PI }
        speeds = FloatArray(count) { 1.2f + random.nextFloat() * 2.8f }
    }

    /** Ray lengths 0..1 at [seconds] into [out]. A frozen frame is [seconds] = 0. No allocation. */
    fun lengths(
        seconds: Float,
        out: FloatArray,
    ) {
        val beat = seconds * BEATS_PER_SECOND
        val kick = exp(-(beat - floor(beat)) * 5f)
        for (i in 0 until count) {
            // The kick is strongest at the top and bottom of the circle, weakest at the sides.
            val angle = i / count.toFloat() * TWO_PI
            val weight = 0.5f + 0.5f * sin(angle * 2f + PI.toFloat() / 2f)
            val wobble = 0.75f + 0.25f * sin(seconds * speeds[i] + phases[i])
            out[i] = (base[i] * wobble + 0.3f * kick * weight * base[i]).coerceIn(0f, 1f)
        }
    }
}

/** The stylized waveform of the seek bar (decision E3): a picture of a track, not its audio. */
internal object WaveformShape {
    /** A seed from a track's id; the same id always gives the same waveform. */
    fun seedOf(id: String): Long = id.hashCode().toLong() * 0x9E3779B1L + id.length

    /**
     * [count] amplitudes 0.08..1: loud and quiet sections, a few transients, a fade in and out.
     * The same [seed] and [count] always give the same shape.
     */
    fun amplitudes(
        seed: Long,
        count: Int,
    ): FloatArray {
        if (count <= 0) return FloatArray(0)
        val random = SeededRandom(seed)
        val raw = FloatArray(count)
        var i = 0
        var first = true
        while (i < count) {
            val length = (count * (0.06f + random.nextFloat() * 0.16f)).toInt().coerceAtLeast(1)
            // The intro is quieter; later sections range from calm to full.
            val loudness = if (first) 0.3f + random.nextFloat() * 0.2f else 0.4f + random.nextFloat() * 0.6f
            first = false
            val end = minOf(count, i + length)
            while (i < end) {
                raw[i] = loudness * (0.45f + 0.55f * random.nextFloat())
                i++
            }
        }
        // Transients: a few sharp peaks, as drums would make.
        val spikes = (count / 24).coerceAtLeast(1)
        repeat(spikes) {
            val at = (random.nextFloat() * count).toInt().coerceIn(0, count - 1)
            raw[at] = (raw[at] + 0.35f + random.nextFloat() * 0.3f).coerceAtMost(1f)
        }
        val fade = (count * 0.04f).coerceAtLeast(1f)
        return FloatArray(count) { k ->
            // A light 3-tap smoothing keeps neighbors related without losing the spikes.
            val left = raw[(k - 1).coerceAtLeast(0)]
            val right = raw[(k + 1).coerceAtMost(count - 1)]
            val smooth = maxOf(raw[k], 0.5f * raw[k] + 0.25f * (left + right))
            val envelope = minOf(1f, (k + 1) / fade, (count - k) / fade)
            (smooth * envelope).coerceIn(0.08f, 1f)
        }
    }
}
