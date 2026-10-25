package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.BuiltInContextPresets
import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class CurveEqualizerTest {
    private val rate = 48_000f

    private fun designed(
        curve: List<TargetPoint>,
        cutoffHz: Float = 0f,
    ) = CurveEqualizer().also {
        it.configure(2)
        it.setCurve(curve, cutoffHz)
        assertTrue(it.beginBuffer(rate))
    }

    @Test
    fun `the chain follows the car and box curves within 1 dB from 40 to 16000 Hz`() {
        listOf(BuiltInContextPresets.Car, BuiltInContextPresets.BluetoothSpeaker).forEach { preset ->
            val equalizer = designed(preset.targetCurve)
            val sorted = preset.targetCurve.sortedBy { it.frequencyHz }
            var hz = 40f
            while (hz <= 16_000f) {
                val target = EqualizerInterpolator.interpolateFrequency(hz, sorted)
                assertEquals("${preset.id} at $hz Hz", target, equalizer.chainResponseDb(hz), 1.0f)
                hz *= 1.07f
            }
        }
    }

    @Test
    fun `the subsonic high-pass cuts well below and passes well above the cutoff`() {
        val equalizer = designed(emptyList(), cutoffHz = 40f)

        assertTrue("at half the cutoff", equalizer.chainResponseDb(20f) <= -20f)
        assertTrue("at twice the cutoff", equalizer.chainResponseDb(80f) >= -1f)
    }

    @Test
    fun `a flat curve without a cutoff keeps the equalizer off`() {
        val equalizer = CurveEqualizer()
        equalizer.configure(2)
        equalizer.setCurve(listOf(TargetPoint(100f, 0f), TargetPoint(5000f, 0f)), 0f)

        assertFalse(equalizer.beginBuffer(rate))
        assertEquals(1f, equalizer.preGain, 0f)
    }

    @Test
    fun `the pre-gain makes room for the strongest boost`() {
        val equalizer = designed(listOf(TargetPoint(20f, 6f), TargetPoint(20_000f, 6f)))

        assertTrue("pre-gain was ${equalizer.preGain}", equalizer.preGain < 0.6f)
    }

    @Test
    fun `a boosted loud sine stays under the limiter threshold`() {
        val dsp = PlayerDspPcm16()
        dsp.configure(2)
        dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = true))
        dsp.updateCurve(listOf(TargetPoint(20f, 6f), TargetPoint(20_000f, 6f)), 0f)
        val samples = ShortArray(32_000) { (sin(2 * PI * 1_000 * (it / 2) / rate) * 32_000).toInt().toShort() }

        dsp.process(samples, 0, samples.size, rate.toInt())

        val peak = (16_000 until samples.size).maxOf { abs(samples[it].toInt()) }
        assertTrue("peak was $peak", peak <= 29_300)
    }

    @Test
    fun `with every stage off the audio is untouched even if a curve is set`() {
        val dsp = PlayerDspPcm16()
        dsp.configure(2)
        dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = false))
        dsp.updateCurve(emptyList(), 0f)
        val samples = ShortArray(2_000) { (sin(2 * PI * 200 * (it / 2) / rate) * 20_000).toInt().toShort() }
        val copy = samples.copyOf()

        dsp.process(samples, 0, samples.size, rate.toInt())

        assertTrue(copy.contentEquals(samples))
    }
}
