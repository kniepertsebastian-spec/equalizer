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

    @Test
    fun `changing the curve during playback does not click`() {
        val equalizer = CurveEqualizer()
        equalizer.configure(1)
        equalizer.setCurve(listOf(TargetPoint(20f, 6f), TargetPoint(200f, 6f), TargetPoint(1_000f, 0f)), 0f)
        val out = FloatArray(48_000)
        for (block in 0 until 48) {
            // Halfway through, the bass boost is dragged down by 3 dB, as on the graph.
            if (block == 24) equalizer.setCurve(listOf(TargetPoint(20f, 3f), TargetPoint(200f, 3f), TargetPoint(1_000f, 0f)), 0f)
            assertTrue(equalizer.beginBuffer(rate))
            for (n in block * 1_000 until (block + 1) * 1_000) {
                out[n] = equalizer.process(0, (0.5 * sin(2 * PI * 80 * n / rate)).toFloat())
                equalizer.endFrame()
            }
        }
        // The steepest sample-to-sample step of a clean 80 Hz sine at this level, with margin.
        val steady = (12_000 until 24_000).maxOf { abs(out[it] - out[it - 1]) }
        val aroundChange = (24_000 until 26_000).maxOf { abs(out[it] - out[it - 1]) }
        assertTrue("step $aroundChange after the change vs $steady before", aroundChange <= steady * 1.2f)
    }

    @Test
    fun `the pre-gain glides to a new curve instead of jumping`() {
        val equalizer = designed(listOf(TargetPoint(20f, 0.5f), TargetPoint(20_000f, 0.5f)))
        val before = equalizer.appliedGain
        equalizer.setCurve(listOf(TargetPoint(20f, 9f), TargetPoint(20_000f, 9f)), 0f)
        assertTrue(equalizer.beginBuffer(rate))
        equalizer.process(0, 0f)
        equalizer.endFrame()

        val afterOneFrame = equalizer.appliedGain
        assertTrue("jumped from $before to $afterOneFrame", before - afterOneFrame < 0.01f)
        repeat(4_800) { equalizer.endFrame() }
        assertEquals(equalizer.preGain, equalizer.appliedGain, 1e-6f)
    }
}
