package com.hardbasseq.eq.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Roadmap 03, P4-3. Levels are in dB relative to the input amplitude, measured with a single-bin
// DFT over exactly one second after one second of settling (whole periods for integer
// frequencies, so no leakage from the fundamental). Concept and the measured values of the old
// rectifier design for comparison: docs/DSP_VIRTUAL_BASS.md.
class BassExciterTest {
    private val sampleRateHz = 48000f

    private companion object {
        const val INPUT_AMPLITUDE = 0.3f

        // 20·log10(sqrt(0.25) / sqrt(0.75)): 3rd re 2nd at the default share.
        const val DEFAULT_THIRD_RE_SECOND_DB = -4.77
    }

    // --- Bypass and safety --------------------------------------------------------------------

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
    fun silence_staysSilent() {
        val exciter = fullMix()

        val output = FloatArray(4800) { exciter.processUnclamped(0f, sampleRateHz) }

        assertTrue(output.all { it == 0f })
    }

    @Test
    fun output_neverExceedsFullScale() {
        val input = sineWave(freqHz = 55f, amplitude = 1f, sampleCount = 4096)
        val exciter = fullMix()

        for (sample in input) {
            val output = exciter.process(sample, sampleRateHz)
            assertTrue(output in -1f..1f)
        }
    }

    @Test
    fun settings_clampCutoffMixAndShare() {
        val settings = BassExciterSettings(cutoffHz = 10000f, mix = 5f, thirdHarmonicShare = -2f).clamped()

        assertEquals(BassExciterSettings.MAX_CUTOFF_HZ, settings.cutoffHz, 0.001f)
        assertEquals(1f, settings.mix, 0.001f)
        assertEquals(0f, settings.thirdHarmonicShare, 0.001f)
    }

    // --- Frequency response -------------------------------------------------------------------

    @Test
    fun outOfBandTones_passUnchanged() {
        for (freqHz in listOf(1000f, 5000f)) {
            val input = sineWave(freqHz, amplitude = 0.3f, sampleCount = 19200)
            val exciter = fullMix()
            val output = FloatArray(input.size) { exciter.processUnclamped(input[it], sampleRateHz) }

            // Steady state only: switching a sine on is itself a broadband click whose low end
            // briefly reaches the generator.
            var sumSquaredError = 0.0
            for (i in 9600 until input.size) {
                val error = (output[i] - input[i]).toDouble()
                sumSquaredError += error * error
            }
            val rmsError = sqrt(sumSquaredError / 9600)
            // The dry path is the input itself; what the isolation low-pass lets through stays
            // below the 16-bit floor (3e-5).
            assertTrue("rms error $rmsError at $freqHz Hz", rmsError < 3e-5)
        }
    }

    @Test
    fun secondHarmonicLevel_isPinnedAcrossTheBassRange() {
        // Default share, mix 1. The P0-5 rectifier measured -9.9 / -8.9 / -9.7 dB here, but spread
        // energy over the 4th, 6th, ... harmonics as well; the total below is what is matched.
        assertEquals(-12.2, harmonicDb(40f, 2), 0.5)
        assertEquals(-10.8, harmonicDb(60f, 2), 0.5)
        assertEquals(-10.8, harmonicDb(100f, 2), 0.5)
    }

    @Test
    fun harmonicLevel_isFlatFrom40To120Hz() {
        val levels = listOf(40f, 60f, 80f, 100f, 120f).map { totalHarmonicDb(it) }

        val spread = levels.max() - levels.min()
        assertTrue("harmonic spread $spread dB across 40..120 Hz ($levels)", spread <= 2.0)
    }

    @Test
    fun totalHarmonicLevel_at100HzMatchesTheP05Reference() {
        // The P0-5 exciter put the 2nd harmonic of this tone at -9.7 dB; presets must not get louder.
        assertEquals(-9.7, totalHarmonicDb(100f), 0.5)
    }

    @Test
    fun tonesAboveTheCutoff_generateLittle() {
        val atCutoffBand = totalHarmonicDb(80f)
        // 4th-order isolation: one octave above the 150 Hz cutoff is 24 dB down before the
        // generator, then the harmonic low-pass takes more.
        assertTrue(totalHarmonicDb(300f) < atCutoffBand - 25.0)
        assertTrue(totalHarmonicDb(600f) < atCutoffBand - 60.0)
    }

    @Test
    fun differentSampleRates_giveTheSameHarmonicLevels() {
        val reference = harmonicDb(60f, 2)
        for (rate in listOf(44100f, 96000f)) {
            assertEquals("2nd harmonic at $rate Hz", reference, harmonicDb(60f, 2, sampleRate = rate), 0.3)
        }
    }

    // --- 2nd / 3rd harmonic ratio and purity ---------------------------------------------------

    @Test
    fun defaultShare_putsTheThirdAbout5dBBelowTheSecond() {
        val second = harmonicDb(60f, 2)
        val third = harmonicDb(60f, 3)

        assertEquals(DEFAULT_THIRD_RE_SECOND_DB, third - second, 1.0)
    }

    @Test
    fun shareZero_givesOnlyTheOctave() {
        val second = harmonicDb(60f, 2, share = 0f)
        val third = harmonicDb(60f, 3, share = 0f)

        assertTrue("3rd $third dB should be >= 60 dB below 2nd $second dB", third < second - 60.0)
    }

    @Test
    fun shareOne_givesOnlyTheThird() {
        val second = harmonicDb(60f, 2, share = 1f)
        val third = harmonicDb(60f, 3, share = 1f)

        assertTrue("2nd $second dB should be >= 60 dB below 3rd $third dB", second < third - 60.0)
    }

    @Test
    fun halfShare_balancesSecondAndThird() {
        assertEquals(harmonicDb(80f, 2, share = 0.5f), harmonicDb(80f, 3, share = 0.5f), 1.0)
    }

    @Test
    fun share_keepsTheTotalHarmonicPower() {
        // 80 Hz: 160 and 240 Hz both sit flat in the harmonic band-pass.
        val levels = listOf(0f, 0.25f, 0.5f, 1f).map { totalHarmonicDb(80f, share = it) }

        assertTrue("total harmonic level moves with the share: $levels", levels.max() - levels.min() <= 0.5)
    }

    @Test
    fun noHarmonicsAboveTheThird() {
        // The full-wave rectifier put the 4th harmonic only ~12 dB below the 2nd.
        val second = harmonicDb(60f, 2)
        for (n in 4..6) {
            val level = harmonicDb(60f, n)
            assertTrue("harmonic $n at $level dB, 2nd at $second dB", level < second - 50.0)
        }
    }

    @Test
    fun twoTones_produceNoDifferenceTone() {
        // 50 + 70 Hz: any square law makes 20 Hz (sub-sonic mud); the old rectifier left it at
        // -27 dB. The analytic signal only multiplies upwards.
        val amplitude = 0.2f
        val input = sineWave(50f, amplitude, 96000).zip(sineWave(70f, amplitude, 96000)) { a, b -> a + b }
        val exciter = fullMix()
        val output = FloatArray(input.size) { exciter.processUnclamped(input[it], sampleRateHz) }
        val measured = output.copyOfRange(48000, output.size)

        val difference = 20.0 * log10(singleBinAmplitude(measured, 20f, sampleRateHz) / amplitude)
        assertTrue("20 Hz difference tone at $difference dB", difference < -50.0)
    }

    // --- Level behaviour (envelope follower and gate) -----------------------------------------

    @Test
    fun harmonicRatio_isIndependentOfTheInputLevel() {
        val reference = harmonicDb(60f, 2)
        for (levelDb in listOf(0.0, -6.0, -20.0, -40.0)) {
            val amplitude = 10.0.pow(levelDb / 20.0).toFloat()
            assertEquals("at $levelDb dBFS", reference, harmonicDb(60f, 2, amplitude = amplitude), 0.3)
        }
    }

    @Test
    fun gate_fadesHarmonicsOutBelowTheThreshold() {
        val open = harmonicDb(60f, 2)
        val inside = harmonicDb(60f, 2, amplitude = 10.0.pow(-60.0 / 20.0).toFloat())
        val closed = harmonicDb(60f, 2, amplitude = 10.0.pow(-70.0 / 20.0).toFloat())

        assertTrue("-60 dBFS should be partly gated: $inside vs $open", inside < open - 3.0 && inside > open - 30.0)
        assertTrue("-70 dBFS should be gated: $closed", closed < open - 60.0)
    }

    @Test
    fun harmonics_followAToneBurst() {
        val exciter = fullMix()
        val burst = sineWave(60f, INPUT_AMPLITUDE, 24000)
        val silence = FloatArray(24000)
        val input = burst + silence
        val harmonics = FloatArray(input.size) { exciter.processUnclamped(input[it], sampleRateHz) - input[it] }

        // 20 ms after the onset the harmonics are at least at half their steady-state peak.
        val steadyPeak = peak(harmonics, 12000, 24000)
        val earlyPeak = peak(harmonics, 960, 1440)
        assertTrue("onset: $earlyPeak vs steady $steadyPeak", earlyPeak > 0.5f * steadyPeak)

        // 100 ms after the burst they are gone (no hanging envelope).
        val tail = peak(harmonics, 24000 + 4800, input.size)
        assertTrue("tail $tail vs steady $steadyPeak", tail < 0.01f * steadyPeak)
    }

    @Test
    fun reset_dropsTheFilterMemory() {
        val exciter = fullMix()
        sineWave(60f, INPUT_AMPLITUDE, 4800).forEach { exciter.processUnclamped(it, sampleRateHz) }

        exciter.reset()

        assertEquals(0f, exciter.processUnclamped(0f, sampleRateHz), 0f)
    }

    // --- Helpers ------------------------------------------------------------------------------

    private fun fullMix(share: Float = BassExciterSettings.DEFAULT_THIRD_HARMONIC_SHARE): BassExciter =
        BassExciter(BassExciterSettings(enabled = true, cutoffHz = 150f, mix = 1f, thirdHarmonicShare = share))

    private fun totalHarmonicDb(
        fundamentalHz: Float,
        share: Float = BassExciterSettings.DEFAULT_THIRD_HARMONIC_SHARE,
    ): Double {
        val second = 10.0.pow(harmonicDb(fundamentalHz, 2, share = share) / 10.0)
        val third = 10.0.pow(harmonicDb(fundamentalHz, 3, share = share) / 10.0)
        return 10.0 * log10(second + third)
    }

    private fun harmonicDb(
        fundamentalHz: Float,
        harmonic: Int,
        share: Float = BassExciterSettings.DEFAULT_THIRD_HARMONIC_SHARE,
        amplitude: Float = INPUT_AMPLITUDE,
        sampleRate: Float = sampleRateHz,
    ): Double {
        val exciter = fullMix(share)
        val sampleCount = sampleRate.toInt()
        val input = sineWave(fundamentalHz, amplitude, sampleCount * 2, sampleRate)
        val output = FloatArray(input.size) { exciter.processUnclamped(input[it], sampleRate) }
        val measured = output.copyOfRange(sampleCount, output.size)
        val level = singleBinAmplitude(measured, fundamentalHz * harmonic, sampleRate)
        return 20.0 * log10(level.coerceAtLeast(1e-15) / amplitude)
    }

    private fun peak(
        samples: FloatArray,
        from: Int,
        to: Int,
    ): Float = (from until to).maxOf { abs(samples[it]) }

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
        sampleRate: Float = sampleRateHz,
    ): FloatArray =
        FloatArray(sampleCount) { i ->
            (amplitude * sin(2.0 * PI * freqHz * i / sampleRate)).toFloat()
        }
}
