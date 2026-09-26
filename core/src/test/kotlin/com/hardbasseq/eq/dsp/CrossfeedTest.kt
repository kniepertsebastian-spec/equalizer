package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class CrossfeedTest {
    private val sampleRateHz = 48000f

    @Test
    fun disabled_isBypass() {
        val crossfeed = Crossfeed(CrossfeedSettings(enabled = false))

        val result = crossfeed.process(left = 0.5f, right = -0.3f, sampleRateHz = sampleRateHz)

        assertEquals(0.5f, result.left, 0.0001f)
        assertEquals(-0.3f, result.right, 0.0001f)
    }

    @Test
    fun zeroAmount_isBypass() {
        val crossfeed = Crossfeed(CrossfeedSettings(enabled = true, amount = 0f))

        val result = crossfeed.process(left = 0.5f, right = -0.3f, sampleRateHz = sampleRateHz)

        assertEquals(0.5f, result.left, 0.0001f)
        assertEquals(-0.3f, result.right, 0.0001f)
    }

    @Test
    fun monoSignal_staysAtRoughlyTheSameRmsLevelOnBothChannels() {
        // left == right at every sample - a real mono signal. Crossfeed must
        // not make already-mono content noticeably louder (or quieter) as a
        // side effect. Compared via RMS level rather than per-sample value:
        // the lowpass in the cross-channel path shifts phase near the
        // cutoff, so a mono signal fed back through itself doesn't reproduce
        // its own waveform exactly sample-for-sample even though its overall
        // level is preserved - phase, not level, is what changes there.
        val freqHz = 300f
        val crossfeed = Crossfeed(CrossfeedSettings(enabled = true, cutoffHz = 700f, amount = 1f))

        var sumSquaredOriginal = 0.0
        var sumSquaredLeft = 0.0
        var sumSquaredRight = 0.0
        var count = 0
        for (i in 0 until 4096) {
            val sample = (0.4 * sin(2.0 * PI * freqHz * i / sampleRateHz)).toFloat()
            val result = crossfeed.process(sample, sample, sampleRateHz)
            if (i > 1000) {
                sumSquaredOriginal += sample.toDouble() * sample
                sumSquaredLeft += result.left.toDouble() * result.left
                sumSquaredRight += result.right.toDouble() * result.right
                count++
            }
        }

        val rmsOriginal = sqrt(sumSquaredOriginal / count)
        val rmsLeft = sqrt(sumSquaredLeft / count)
        val rmsRight = sqrt(sumSquaredRight / count)
        val tolerance = rmsOriginal * 0.2

        assertEquals(rmsOriginal, rmsLeft, tolerance)
        assertEquals(rmsOriginal, rmsRight, tolerance)
    }

    @Test
    fun hardPannedLowFrequencyContent_bleedsIntoTheSilentChannel() {
        // Left carries a sub-cutoff tone, right is pure silence - a classic
        // hard-panned mix. Crossfeed should make the "silent" channel no
        // longer exactly silent.
        val freqHz = 200f
        val crossfeed = Crossfeed(CrossfeedSettings(enabled = true, cutoffHz = 700f, amount = 1f))

        var maxRightMagnitude = 0f
        for (i in 0 until 4096) {
            val left = (0.5 * sin(2.0 * PI * freqHz * i / sampleRateHz)).toFloat()
            val result = crossfeed.process(left, 0f, sampleRateHz)
            if (i > 500) {
                maxRightMagnitude = maxOf(maxRightMagnitude, abs(result.right))
            }
        }

        assertTrue("expected some bleed into the silent channel, maxRightMagnitude=$maxRightMagnitude", maxRightMagnitude > 0.02f)
    }

    @Test
    fun crossfeedBleedIsWeakerAboveTheCutoffThanBelowIt() {
        val cutoffHz = 700f
        val belowCutoffHz = 150f
        val aboveCutoffHz = 6000f

        val belowResult = maxBleedIntoSilentChannel(belowCutoffHz, cutoffHz)
        val aboveResult = maxBleedIntoSilentChannel(aboveCutoffHz, cutoffHz)

        assertTrue(
            "expected less bleed above the cutoff ($aboveResult) than below it ($belowResult)",
            aboveResult < belowResult,
        )
    }

    private fun maxBleedIntoSilentChannel(
        freqHz: Float,
        cutoffHz: Float,
    ): Float {
        val crossfeed = Crossfeed(CrossfeedSettings(enabled = true, cutoffHz = cutoffHz, amount = 1f))
        var maxRightMagnitude = 0f
        for (i in 0 until 4096) {
            val left = (0.5 * sin(2.0 * PI * freqHz * i / sampleRateHz)).toFloat()
            val result = crossfeed.process(left, 0f, sampleRateHz)
            if (i > 500) {
                maxRightMagnitude = maxOf(maxRightMagnitude, abs(result.right))
            }
        }
        return maxRightMagnitude
    }

    @Test
    fun output_neverExceedsFullScale() {
        val crossfeed = Crossfeed(CrossfeedSettings(enabled = true, cutoffHz = 700f, amount = 1f))

        for (i in 0 until 2048) {
            val phase = 2.0 * PI * 200.0 * i / sampleRateHz
            val result = crossfeed.process(sin(phase).toFloat(), -sin(phase).toFloat(), sampleRateHz)
            assertTrue(result.left in -1f..1f)
            assertTrue(result.right in -1f..1f)
        }
    }

    @Test
    fun settings_clampCutoffAndAmountToSafeRanges() {
        val settings = CrossfeedSettings(cutoffHz = 10000f, amount = 5f).clamped()
        assertEquals(CrossfeedSettings.MAX_CUTOFF_HZ, settings.cutoffHz, 0.001f)
        assertEquals(1f, settings.amount, 0.001f)

        val tooLow = CrossfeedSettings(cutoffHz = 1f, amount = -1f).clamped()
        assertEquals(CrossfeedSettings.MIN_CUTOFF_HZ, tooLow.cutoffHz, 0.001f)
        assertEquals(0f, tooLow.amount, 0.001f)
    }
}
