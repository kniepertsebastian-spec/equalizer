package com.hardbasseq.eq.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class BassExciterTest {
    private val sampleRateHz = 48000f

    private companion object {
        const val INPUT_AMPLITUDE = 0.3f
    }

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

    // P0-5: the high-pass behind the rectifier used to sit at the isolation cutoff (150 Hz) and
    // took ~11 dB off the octave above a 40 Hz tone, the very region small drivers need.
    // Levels are the 2nd harmonic in dB relative to the input amplitude (0.3, -10.5 dBFS),
    // measured over whole periods after the filters have settled. Before the fix the same
    // measurement gave -18.7 dB (40 Hz), -12.9 dB (60 Hz) and -9.4 dB (100 Hz).
    @Test
    fun secondHarmonicLevel_isPinnedAt40_60And100Hz() {
        assertEquals(-9.9, secondHarmonicDb(40f), 1.0)
        assertEquals(-8.9, secondHarmonicDb(60f), 1.0)
        assertEquals(-9.7, secondHarmonicDb(100f), 1.0)
    }

    @Test
    fun secondHarmonicLevel_isFlatAcrossTheBassRange() {
        val levels = listOf(40f, 60f, 100f).map { secondHarmonicDb(it) }

        val spread = levels.max() - levels.min()
        assertTrue("2nd harmonic spread of $spread dB across 40/60/100 Hz ($levels) is too uneven", spread <= 2.0)
        // The old design was 9 dB weaker at 40 Hz than at 100 Hz.
        assertTrue("40 Hz harmonic ${levels[0]} dB is still far below 100 Hz ${levels[2]} dB", levels[0] > levels[2] - 3.0)
    }

    @Test
    fun secondHarmonicLevel_at100HzStaysAtTheCalibratedReference() {
        // OUTPUT_TRIM keeps existing presets from getting louder: 100 Hz sat at -9.4 dB before.
        assertEquals(-9.4, secondHarmonicDb(100f), 0.8)
    }

    @Test
    fun drive_changesTheHarmonicLevel() {
        val low = secondHarmonicDb(60f, drive = 0f)
        val mid = secondHarmonicDb(60f, drive = 0.5f)
        val high = secondHarmonicDb(60f, drive = 1f)

        // abs(x * drive) / drive used to cancel out: all three were identical.
        assertTrue("drive 0 -> 0.5 should raise the harmonic: $low -> $mid", mid > low + 1.0)
        assertTrue("drive 0.5 -> 1 should raise the harmonic: $mid -> $high", high > mid + 1.0)
    }

    @Test
    fun harmonicsAboveTwiceTheCutoff_areLimited() {
        // 100 Hz tone: the 2nd harmonic (200 Hz) sits inside the 300 Hz low-pass, the 6th (600 Hz)
        // is beyond it. Without the upper low-pass the 6th harmonic was only ~20 dB below the 2nd.
        val second = harmonicDb(fundamentalHz = 100f, harmonic = 2)
        val sixth = harmonicDb(fundamentalHz = 100f, harmonic = 6)

        assertTrue("6th harmonic ($sixth dB) should be >= 25 dB below the 2nd ($second dB)", sixth <= second - 25.0)
    }

    @Test
    fun settings_clampCutoffMixAndDrive() {
        val settings = BassExciterSettings(cutoffHz = 10000f, mix = 5f, drive = -2f).clamped()

        assertEquals(BassExciterSettings.MAX_CUTOFF_HZ, settings.cutoffHz, 0.001f)
        assertEquals(1f, settings.mix, 0.001f)
        assertEquals(0f, settings.drive, 0.001f)
    }

    private fun secondHarmonicDb(
        fundamentalHz: Float,
        drive: Float = BassExciterSettings().drive,
    ): Double = harmonicDb(fundamentalHz, harmonic = 2, drive = drive)

    // Level of the n-th harmonic in dB relative to the input amplitude. One second of warm-up,
    // then exactly one second measured: a whole number of periods for integer frequencies, so
    // the single-bin DFT has no leakage from the fundamental.
    private fun harmonicDb(
        fundamentalHz: Float,
        harmonic: Int,
        drive: Float = BassExciterSettings().drive,
    ): Double {
        val exciter = BassExciter(BassExciterSettings(enabled = true, cutoffHz = 150f, mix = 1f, drive = drive))
        val sampleCount = sampleRateHz.toInt()
        val input = sineWave(fundamentalHz, INPUT_AMPLITUDE, sampleCount * 2)
        val output = FloatArray(input.size) { exciter.processUnclamped(input[it], sampleRateHz) }
        val measured = output.copyOfRange(sampleCount, output.size)
        val amplitude = singleBinAmplitude(measured, fundamentalHz * harmonic, sampleRateHz)
        return 20.0 * log10(amplitude / INPUT_AMPLITUDE)
    }

    // Amplitude of one frequency by direct correlation; exact when the window holds whole periods.
    private fun singleBinAmplitude(
        samples: FloatArray,
        freqHz: Float,
        sampleRateHz: Float,
    ): Double {
        var real = 0.0
        var imag = 0.0
        for (i in samples.indices) {
            val phase = 2.0 * PI * freqHz * i / sampleRateHz
            real += samples[i] * cos(phase)
            imag += samples[i] * sin(phase)
        }
        return 2.0 * sqrt(real * real + imag * imag) / samples.size
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
