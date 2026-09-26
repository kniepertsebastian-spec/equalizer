package com.hardbasseq.eq.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class BassExciterTest {
    private val sampleRateHz = 48000f

    @Test
    fun disabled_isBypass() {
        val exciter = BassExciter(BassExciterSettings(enabled = false))
        val input = sineWave(freqHz = 55f, amplitude = 0.3f, sampleCount = 100)

        val output = input.map { exciter.process(it, sampleRateHz) }.toFloatArray()

        assertArrayEquals(input, output, 0f)
    }

    @Test
    fun zeroMix_isBypass() {
        val exciter = BassExciter(BassExciterSettings(enabled = true, mix = 0f))
        val input = sineWave(freqHz = 55f, amplitude = 0.3f, sampleCount = 100)

        val output = input.map { exciter.process(it, sampleRateHz) }.toFloatArray()

        assertArrayEquals(input, output, 0f)
    }

    @Test
    fun lowFrequencyTone_generatesSecondHarmonic() {
        val fundamentalHz = 55f
        val secondHarmonicHz = fundamentalHz * 2f
        val sampleCount = 8192
        val input = sineWave(freqHz = fundamentalHz, amplitude = 0.3f, sampleCount = sampleCount)

        val bypassed = BassExciter(BassExciterSettings(enabled = false))
        val bypassOutput = FloatArray(sampleCount) { bypassed.process(input[it], sampleRateHz) }

        val exciter =
            BassExciter(
                BassExciterSettings(enabled = true, cutoffHz = 150f, mix = 1f, drive = 1f),
            )
        val excitedOutput = FloatArray(sampleCount) { exciter.process(input[it], sampleRateHz) }

        val harmonicEnergyBypassed = goertzelMagnitude(bypassOutput, secondHarmonicHz, sampleRateHz)
        val harmonicEnergyExcited = goertzelMagnitude(excitedOutput, secondHarmonicHz, sampleRateHz)

        // The bypass path can't have any 110 Hz content at all (input is a pure
        // 55 Hz tone) - the exciter must introduce a clearly measurable amount.
        assertTrue(
            "expected excited 2nd-harmonic energy ($harmonicEnergyExcited) to clearly " +
                "exceed bypass ($harmonicEnergyBypassed)",
            harmonicEnergyExcited > harmonicEnergyBypassed + 5.0,
        )
    }

    @Test
    fun outOfBandTone_leftEssentiallyUnchanged() {
        // 5 kHz sits far above any reasonable bass cutoff - the lowpass stage
        // should strip it from the band that feeds the rectifier, so the
        // exciter should barely touch it.
        val sampleCount = 2048
        val input = sineWave(freqHz = 5000f, amplitude = 0.3f, sampleCount = sampleCount)

        val exciter = BassExciter(BassExciterSettings(enabled = true, cutoffHz = 150f, mix = 1f, drive = 1f))
        val output = FloatArray(sampleCount) { exciter.process(input[it], sampleRateHz) }

        var sumSquaredError = 0.0
        for (i in input.indices) {
            val error = (output[i] - input[i]).toDouble()
            sumSquaredError += error * error
        }
        val rmsError = sqrt(sumSquaredError / sampleCount)
        assertTrue("expected rmsError ($rmsError) to be small for an out-of-band tone", rmsError < 0.05)
    }

    @Test
    fun output_neverExceedsFullScale() {
        val input = sineWave(freqHz = 55f, amplitude = 1f, sampleCount = 4096)
        val exciter = BassExciter(BassExciterSettings(enabled = true, cutoffHz = 150f, mix = 1f, drive = 1f))

        for (sample in input) {
            val output = exciter.process(sample, sampleRateHz)
            assertTrue(output in -1f..1f)
        }
    }

    @Test
    fun settings_clampCutoffMixAndDrive() {
        val settings = BassExciterSettings(cutoffHz = 10000f, mix = 5f, drive = -2f).clamped()

        assertEquals(BassExciterSettings.MAX_CUTOFF_HZ, settings.cutoffHz, 0.001f)
        assertEquals(1f, settings.mix, 0.001f)
        assertEquals(0f, settings.drive, 0.001f)
    }

    private fun sineWave(
        freqHz: Float,
        amplitude: Float,
        sampleCount: Int,
    ): FloatArray =
        FloatArray(sampleCount) { i ->
            (amplitude * sin(2.0 * PI * freqHz * i / sampleRateHz)).toFloat()
        }

    // Single-bin DFT (Goertzel algorithm) - measures how much energy a signal
    // has at exactly targetFreqHz without needing a full FFT dependency just
    // for this one test.
    private fun goertzelMagnitude(
        samples: FloatArray,
        targetFreqHz: Float,
        sampleRateHz: Float,
    ): Double {
        val n = samples.size
        val k = 0.5 + n * targetFreqHz / sampleRateHz
        val w = 2.0 * PI * k / n
        val cosine = cos(w)
        val coeff = 2.0 * cosine

        var q1 = 0.0
        var q2 = 0.0
        for (sample in samples) {
            val q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }

        val real = q1 - q2 * cosine
        val imag = q2 * sin(w)
        return sqrt(real * real + imag * imag)
    }
}
