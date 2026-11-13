package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class HilbertAllpassPairTest {
    private val testFrequenciesHz = listOf(16f, 25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f, 800f)

    @Test
    fun phaseDifference_is90DegreesAcrossTheBand_at44_1And48kHz() {
        for (rate in listOf(44100f, 48000f)) {
            for (freq in testFrequenciesHz) {
                assertEquals("$freq Hz at $rate Hz", 90.0, phaseDifferenceDeg(freq, rate), 0.25)
            }
        }
    }

    @Test
    fun phaseDifference_is90DegreesAcrossTheBand_atHighSampleRates() {
        for (rate in listOf(96000f, 192000f)) {
            for (freq in testFrequenciesHz) {
                assertEquals("$freq Hz at $rate Hz", 90.0, phaseDifferenceDeg(freq, rate), 0.5)
            }
        }
    }

    @Test
    fun bothOutputs_keepTheInputAmplitude() {
        val rate = 48000f
        for (freq in testFrequenciesHz) {
            val (i, q) = run(freq, rate)
            assertEquals("I at $freq Hz", 1.0, amplitude(i, freq, rate), 1e-3)
            assertEquals("Q at $freq Hz", 1.0, amplitude(q, freq, rate), 1e-3)
        }
    }

    @Test
    fun envelope_isFlatForASineWithoutSmoothing() {
        val rate = 48000f
        for (freq in listOf(30f, 60f, 120f)) {
            val (i, q) = run(freq, rate)
            val envelope = DoubleArray(i.size) { sqrt(i[it] * i[it] + q[it] * q[it]) }
            val ripple = envelope.max() - envelope.min()
            assertTrue("envelope ripple $ripple at $freq Hz", ripple < 0.01)
        }
    }

    @Test
    fun reset_clearsTheState() {
        val pair = HilbertAllpassPair()
        pair.configure(48000f)
        repeat(100) { pair.process(1.0) }

        pair.reset()
        pair.process(0.0)

        assertEquals(0.0, pair.inPhase, 0.0)
        assertEquals(0.0, pair.quadrature, 0.0)
    }

    // Unit sine through the pair; one second of settling (the 5 Hz pole rings for a while), then
    // exactly one second returned: whole periods for integer frequencies.
    private fun run(
        freqHz: Float,
        sampleRateHz: Float,
    ): Pair<DoubleArray, DoubleArray> {
        val pair = HilbertAllpassPair()
        pair.configure(sampleRateHz)
        val n = sampleRateHz.toInt()
        val i = DoubleArray(n)
        val q = DoubleArray(n)
        for (k in 0 until 2 * n) {
            pair.process(sin(2.0 * PI * freqHz * k / sampleRateHz))
            if (k >= n) {
                i[k - n] = pair.inPhase
                q[k - n] = pair.quadrature
            }
        }
        return i to q
    }

    private fun phaseDifferenceDeg(
        freqHz: Float,
        sampleRateHz: Float,
    ): Double {
        val (i, q) = run(freqHz, sampleRateHz)
        var difference = Math.toDegrees(phase(i, freqHz, sampleRateHz) - phase(q, freqHz, sampleRateHz))
        while (difference < 0.0) difference += 360.0
        while (difference >= 360.0) difference -= 360.0
        // Which chain leads does not matter for the generator; fold onto 0..180.
        return if (difference > 180.0) 360.0 - difference else difference
    }

    private fun phase(
        samples: DoubleArray,
        freqHz: Float,
        sampleRateHz: Float,
    ): Double {
        var real = 0.0
        var imag = 0.0
        for (k in samples.indices) {
            val w = 2.0 * PI * freqHz * k / sampleRateHz
            real += samples[k] * cos(w)
            imag += samples[k] * sin(w)
        }
        return atan2(imag, real)
    }

    private fun amplitude(
        samples: DoubleArray,
        freqHz: Float,
        sampleRateHz: Float,
    ): Double {
        var real = 0.0
        var imag = 0.0
        for (k in samples.indices) {
            val w = 2.0 * PI * freqHz * k / sampleRateHz
            real += samples[k] * cos(w)
            imag += samples[k] * sin(w)
        }
        return 2.0 * sqrt(real * real + imag * imag) / samples.size
    }
}
