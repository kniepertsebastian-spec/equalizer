package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class BassMonoSummerTest {
    private val sampleRateHz = 48000f

    @Test
    fun disabled_isBypass() {
        val summer = BassMonoSummer(BassMonoSummerSettings(enabled = false))

        val result = summer.process(left = 0.3f, right = -0.2f, sampleRateHz = sampleRateHz)

        assertEquals(0.3f, result.left, 0.0001f)
        assertEquals(-0.2f, result.right, 0.0001f)
    }

    @Test
    fun subBassContent_convergesToTheSameMonoSignalOnBothChannels() {
        // Same low frequency, same phase, different level - a common real-world
        // imbalance rather than the degenerate exactly-out-of-phase case (which
        // would legitimately cancel to silence when summed). 20 Hz against a
        // 120 Hz cutoff is a good 2.5 octaves into the highpass's stopband (a
        // 2nd-order/12 dB-per-octave filter only fully rejects content well
        // below cutoff, not immediately below it).
        val freqHz = 20f
        val sampleCount = 4096
        val summer = BassMonoSummer(BassMonoSummerSettings(enabled = true, cutoffHz = 120f))

        var maxDifference = 0f
        for (i in 0 until sampleCount) {
            val phase = 2.0 * PI * freqHz * i / sampleRateHz
            val left = (0.5 * sin(phase)).toFloat()
            val right = (0.3 * sin(phase)).toFloat()
            val result = summer.process(left, right, sampleRateHz)
            // Skip the filter's brief settling window at the very start.
            if (i > 500) {
                maxDifference = maxOf(maxDifference, abs(result.left - result.right))
            }
        }

        assertTrue("expected left/right to converge once mono-summed, got maxDifference=$maxDifference", maxDifference < 0.01f)
    }

    @Test
    fun outOfBandStereoContent_staysSeparated() {
        // 5 kHz sits far above any reasonable bass cutoff - stereo separation
        // there must survive untouched.
        val freqHz = 5000f
        val sampleCount = 2048
        val summer = BassMonoSummer(BassMonoSummerSettings(enabled = true, cutoffHz = 120f))

        var sumSquaredErrorLeft = 0.0
        var sumSquaredErrorRight = 0.0
        for (i in 0 until sampleCount) {
            val phase = 2.0 * PI * freqHz * i / sampleRateHz
            val left = (0.3 * sin(phase)).toFloat()
            val right = (-0.3 * sin(phase)).toFloat()
            val result = summer.process(left, right, sampleRateHz)
            sumSquaredErrorLeft += (result.left - left).let { it * it }
            sumSquaredErrorRight += (result.right - right).let { it * it }
        }

        val rmsErrorLeft = sqrt(sumSquaredErrorLeft / sampleCount)
        val rmsErrorRight = sqrt(sumSquaredErrorRight / sampleCount)
        assertTrue("expected left channel to stay near-original, got rmsError=$rmsErrorLeft", rmsErrorLeft < 0.05)
        assertTrue("expected right channel to stay near-original, got rmsError=$rmsErrorRight", rmsErrorRight < 0.05)
    }

    @Test
    fun output_neverExceedsFullScale() {
        val summer = BassMonoSummer(BassMonoSummerSettings(enabled = true, cutoffHz = 120f))

        for (i in 0 until 2048) {
            val phase = 2.0 * PI * 50.0 * i / sampleRateHz
            val result = summer.process((sin(phase)).toFloat(), (sin(phase)).toFloat(), sampleRateHz)
            assertTrue(result.left in -1f..1f)
            assertTrue(result.right in -1f..1f)
        }
    }

    @Test
    fun settings_clampCutoffToSafeRange() {
        val settings = BassMonoSummerSettings(cutoffHz = 10000f).clamped()
        assertEquals(BassMonoSummerSettings.MAX_CUTOFF_HZ, settings.cutoffHz, 0.001f)

        val tooLow = BassMonoSummerSettings(cutoffHz = 1f).clamped()
        assertEquals(BassMonoSummerSettings.MIN_CUTOFF_HZ, tooLow.cutoffHz, 0.001f)
    }
}
