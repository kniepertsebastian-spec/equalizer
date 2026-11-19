package com.auracurve.app.dsp

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pixel-Car module 11, Z1: Android's Visualizer FFT in a few log-spaced bars for the web remote.
 * Pure: bytes in, levels 0-100 out.
 */
object SpectrumBands {
    const val COUNT = 32
    const val MIN_HZ = 40f
    const val MAX_HZ = 16_000f

    /** Quieter than this shows as an empty bar. */
    const val FLOOR_DB = -60f

    /** [count] + 1 edges from [MIN_HZ] to [MAX_HZ], evenly spaced on a log scale. */
    fun edges(count: Int = COUNT): FloatArray {
        val ratio = MAX_HZ / MIN_HZ
        return FloatArray(count + 1) { i -> MIN_HZ * ratio.pow(i.toFloat() / count) }
    }

    /**
     * @param fft Visualizer.getFft() data: [0] DC, [1] Nyquist (both real), then re/im pairs
     *   for bins 1 .. n/2-1, each a signed byte.
     * @param samplingRateHz the output rate (Visualizer reports it in milli-hertz)
     * @return one level per bar, 0 (at or below [FLOOR_DB]) to 100 (full scale)
     */
    fun fromFft(
        fft: ByteArray,
        samplingRateHz: Int,
        count: Int = COUNT,
    ): IntArray {
        val n = fft.size
        if (n < 4 || samplingRateHz <= 0) return IntArray(count)
        val binHz = samplingRateHz.toFloat() / n
        val lastBin = n / 2 - 1
        val magnitude = FloatArray(lastBin + 1)
        for (k in 1..lastBin) magnitude[k] = hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
        val e = edges(count)
        return IntArray(count) { b ->
            // The bins inside the bar; a bar narrower than one bin takes its nearest bin.
            var lo = ceil(e[b] / binHz).toInt()
            var hi = ceil(e[b + 1] / binHz).toInt() - 1
            if (hi < lo) {
                lo = (sqrt(e[b] * e[b + 1]) / binHz).roundToInt()
                hi = lo
            }
            lo = lo.coerceIn(1, lastBin)
            hi = hi.coerceIn(lo, lastBin)
            var peak = 0f
            for (k in lo..hi) if (magnitude[k] > peak) peak = magnitude[k]
            level(peak)
        }
    }

    /** A byte FFT's magnitude (full scale about 128) as 0-100 on a [FLOOR_DB] scale. */
    fun level(magnitude: Float): Int {
        if (magnitude <= 0f) return 0
        val db = 20f * log10(magnitude / FULL_SCALE)
        return (((db - FLOOR_DB) / -FLOOR_DB) * 100f).roundToInt().coerceIn(0, 100)
    }

    private const val FULL_SCALE = 128f
}
