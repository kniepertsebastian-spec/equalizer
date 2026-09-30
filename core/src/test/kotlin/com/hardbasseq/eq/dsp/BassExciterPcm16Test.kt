package com.hardbasseq.eq.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class BassExciterPcm16Test {
    private val sampleRateHz = 48000

    @Test
    fun `disabled leaves samples untouched`() {
        val processor = BassExciterPcm16().apply { configure(2) }
        val samples = stereoTone(leftAmplitude = 0.3f, rightAmplitude = 0.3f, frames = 512)
        val expected = samples.copyOf()

        processor.process(samples, 0, samples.size, sampleRateHz)

        assertArrayEquals(expected, samples)
    }

    @Test
    fun `enabled adds second-harmonic energy to a low tone`() {
        val processor = BassExciterPcm16().apply { configure(1) }
        processor.updateSettings(BassExciterSettings(enabled = true, cutoffHz = 150f, mix = 1f, drive = 1f))
        val frames = 8192
        val samples = ShortArray(frames) { (0.3f * sin(2.0 * PI * 55.0 * it / sampleRateHz) * 32768f).toInt().toShort() }
        val before = goertzel(samples, 110.0)

        processor.process(samples, 0, samples.size, sampleRateHz)

        assertTrue("110 Hz energy ${goertzel(samples, 110.0)} should exceed $before", goertzel(samples, 110.0) > before + 50.0)
    }

    @Test
    fun `channels are processed independently`() {
        val processor = BassExciterPcm16().apply { configure(2) }
        processor.updateSettings(BassExciterSettings(enabled = true, mix = 1f, drive = 1f))
        val samples = stereoTone(leftAmplitude = 0.5f, rightAmplitude = 0f, frames = 4096)

        processor.process(samples, 0, samples.size, sampleRateHz)

        // A silent right channel must stay silent: left-channel harmonics must not bleed into it.
        for (i in 1 until samples.size step 2) assertTrue("right sample $i was ${samples[i]}", samples[i] == 0.toShort())
    }

    @Test
    fun `full-scale input is clipped safely, not wrapped`() {
        val processor = BassExciterPcm16().apply { configure(1) }
        processor.updateSettings(BassExciterSettings(enabled = true, mix = 1f, drive = 1f))
        val samples = ShortArray(4096) { (sin(2.0 * PI * 55.0 * it / sampleRateHz) * 32767.0).toInt().toShort() }

        processor.process(samples, 0, samples.size, sampleRateHz)

        // Wrap-around would show up as a huge jump between neighbouring samples of a 55 Hz tone.
        for (i in 1 until samples.size) assertTrue(abs(samples[i] - samples[i - 1]) < 4000)
    }

    @Test
    fun `offset and sample count restrict the processed range`() {
        val processor = BassExciterPcm16().apply { configure(1) }
        processor.updateSettings(BassExciterSettings(enabled = true, mix = 1f, drive = 1f))
        val samples = ShortArray(2000) { (0.4f * sin(2.0 * PI * 55.0 * it / sampleRateHz) * 32768f).toInt().toShort() }
        val original = samples.copyOf()

        processor.process(samples, offset = 500, sampleCount = 1000, sampleRateHz = sampleRateHz)

        for (i in 0 until 500) assertTrue(samples[i] == original[i])
        for (i in 1500 until 2000) assertTrue(samples[i] == original[i])
    }

    private fun stereoTone(
        leftAmplitude: Float,
        rightAmplitude: Float,
        frames: Int,
    ): ShortArray {
        val out = ShortArray(frames * 2)
        for (f in 0 until frames) {
            val phase = 2.0 * PI * 55.0 * f / sampleRateHz
            out[f * 2] = (leftAmplitude * sin(phase) * 32768f).toInt().toShort()
            out[f * 2 + 1] = (rightAmplitude * sin(phase) * 32768f).toInt().toShort()
        }
        return out
    }

    private fun goertzel(
        samples: ShortArray,
        targetHz: Double,
    ): Double {
        val w = 2.0 * PI * targetHz / sampleRateHz
        val coeff = 2.0 * cos(w)
        var s1 = 0.0
        var s2 = 0.0
        for (x in samples) {
            val s = x / 32768.0 + coeff * s1 - s2
            s2 = s1
            s1 = s
        }
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return kotlin.math.sqrt(power.coerceAtLeast(0.0))
    }
}
