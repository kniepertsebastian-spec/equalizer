package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.pow

class CompressorTest {
    private val sampleRateHz = 48000f

    @Test
    fun disabled_isBypass() {
        val compressor = Compressor(CompressorSettings(enabled = false))

        val output = compressor.process(0.9f, sampleRateHz)

        assertEquals(0.9f, output, 0.0001f)
    }

    @Test
    fun belowThreshold_staysAtUnityGainOnceSettled() {
        val compressor = Compressor(CompressorSettings(enabled = true, thresholdDb = -8f, ratio = 2.5f))
        val level = 0.05f // 20*log10(0.05) = -26 dB, well below the -8 dB threshold

        var output = 0f
        repeat(5000) { output = compressor.process(level, sampleRateHz) }

        assertEquals(level, output, 0.001f)
    }

    @Test
    fun aboveThreshold_reducesGainByTheExpectedAmountOnceSettled() {
        val thresholdDb = -8f
        val ratio = 2.5f
        val compressor = Compressor(CompressorSettings(enabled = true, thresholdDb = thresholdDb, ratio = ratio))
        val level = 0.8f // 20*log10(0.8) = -1.94 dB, above the -8 dB threshold

        var output = 0f
        repeat(5000) { output = compressor.process(level, sampleRateHz) }

        val inputDb = 20f * log10(level)
        val overshootDb = inputDb - thresholdDb
        val expectedGainReductionDb = overshootDb - overshootDb / ratio
        val expectedOutput = level * 10f.pow(-expectedGainReductionDb / 20f)

        assertEquals(expectedOutput, output, 0.01f)
        assertTrue("expected compression to reduce level", output < level)
    }

    @Test
    fun higherRatio_compressesMoreThanLowerRatio() {
        val lowRatioCompressor = Compressor(CompressorSettings(enabled = true, thresholdDb = -8f, ratio = 1.5f))
        val highRatioCompressor = Compressor(CompressorSettings(enabled = true, thresholdDb = -8f, ratio = 10f))
        val level = 0.9f

        var lowRatioOutput = 0f
        var highRatioOutput = 0f
        repeat(5000) {
            lowRatioOutput = lowRatioCompressor.process(level, sampleRateHz)
            highRatioOutput = highRatioCompressor.process(level, sampleRateHz)
        }

        assertTrue(highRatioOutput < lowRatioOutput)
    }

    @Test
    fun output_neverExceedsFullScale() {
        val compressor = Compressor(CompressorSettings(enabled = true, thresholdDb = -8f, ratio = 2.5f))

        repeat(1000) {
            val output = compressor.process(1f, sampleRateHz)
            assertTrue(output in -1f..1f)
        }
    }

    @Test
    fun settings_clampThresholdAndRatioToSafeRanges() {
        val settings = CompressorSettings(thresholdDb = 10f, ratio = 999f).clamped()
        assertEquals(CompressorSettings.MAX_THRESHOLD_DB, settings.thresholdDb, 0.001f)
        assertEquals(CompressorSettings.MAX_RATIO, settings.ratio, 0.001f)

        val tooLow = CompressorSettings(thresholdDb = -999f, ratio = 0f).clamped()
        assertEquals(CompressorSettings.MIN_THRESHOLD_DB, tooLow.thresholdDb, 0.001f)
        assertEquals(CompressorSettings.MIN_RATIO, tooLow.ratio, 0.001f)
    }
}
