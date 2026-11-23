package com.auracurve.app.dsp

import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

class CurveEqualizerTest {
    private val rate = 48_000f

    private fun designed(
        curve: List<TargetPoint>,
        cutoffHz: Float = 0f,
        headroomSafetyRatio: Float = CurveEqualizer.DEFAULT_HEADROOM_SAFETY_RATIO,
    ) = CurveEqualizer().also {
        it.configure(2)
        it.setCurve(curve, cutoffHz, headroomSafetyRatio)
        assertTrue(it.beginBuffer(rate))
    }

    private fun Float.toDb() = 20f * log10(this)

    private val flat12Db = listOf(TargetPoint(20f, 12f), TargetPoint(20_000f, 12f))

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
    fun `with a ratio of 1 the pre-gain makes room for the whole boost`() {
        val equalizer = designed(listOf(TargetPoint(20f, 6f), TargetPoint(20_000f, 6f)), headroomSafetyRatio = 1f)

        assertTrue("pre-gain was ${equalizer.preGain}", equalizer.preGain < 0.6f)
    }

    @Test
    fun `the pre-gain takes back the safety ratio's share of a 12 dB boost`() {
        assertEquals(-12f, CurveEqualizer.preGainDb(12f, 1f), 1e-4f)
        assertEquals(-6f, CurveEqualizer.preGainDb(12f, 0.5f), 1e-4f)
        assertEquals(-9.6f, CurveEqualizer.preGainDb(12f, 0.8f), 1e-4f)
        assertEquals(0f, CurveEqualizer.preGainDb(12f, 0f))
        // Out of range ratios are clamped; a cut needs no headroom.
        assertEquals(-12f, CurveEqualizer.preGainDb(12f, 2f), 1e-4f)
        assertEquals(0f, CurveEqualizer.preGainDb(-6f, 0.5f))
    }

    @Test
    fun `the designed chain's pre-gain follows the safety ratio`() {
        val full = designed(flat12Db, headroomSafetyRatio = 1f).preGain.toDb()
        assertEquals("the whole chain peaks at about +12 dB", -12f, full, 0.5f)

        assertEquals(full * 0.5f, designed(flat12Db, headroomSafetyRatio = 0.5f).preGain.toDb(), 1e-3f)
        assertEquals(full * 0.8f, designed(flat12Db, headroomSafetyRatio = 0.8f).preGain.toDb(), 1e-3f)
        assertEquals(1f, designed(flat12Db, headroomSafetyRatio = 0f).preGain, 0f)
        assertEquals("default", full * 0.5f, designed(flat12Db).preGain.toDb(), 1e-3f)
    }

    @Test
    fun `a new safety ratio glides instead of jumping`() {
        // Headphones (0.5) to a Bluetooth box (0.8): same curve, 3.6 dB more cut.
        val equalizer = designed(flat12Db, headroomSafetyRatio = 0.5f)
        val before = equalizer.appliedGain
        equalizer.setCurve(flat12Db, 0f, 0.8f)
        assertTrue(equalizer.beginBuffer(rate))
        equalizer.process(0, 0f)
        equalizer.endFrame()

        val afterOneFrame = equalizer.appliedGain
        assertTrue("jumped from $before to $afterOneFrame", before - afterOneFrame < 0.01f)
        assertTrue("the target is lower", equalizer.preGain < before - 0.1f)
        repeat(4_800) { equalizer.endFrame() }
        assertEquals(equalizer.preGain, equalizer.appliedGain, 1e-6f)
    }

    @Test
    fun `with the limiter off the player takes back the whole boost so nothing clips`() {
        fun peakOut(limiterEnabled: Boolean): Int {
            val dsp = PlayerDspPcm16()
            dsp.configure(2)
            dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = limiterEnabled))
            dsp.updateCurve(listOf(TargetPoint(20f, 6f), TargetPoint(20_000f, 6f)), 0f, headroomSafetyRatio = 0.5f)
            val samples = ShortArray(32_000) { (sin(2 * PI * 1_000 * (it / 2) / rate) * 30_000).toInt().toShort() }
            dsp.process(samples, 0, samples.size, rate.toInt())
            return (16_000 until samples.size).maxOf { abs(samples[it].toInt()) }
        }

        val unlimited = peakOut(limiterEnabled = false)
        assertTrue("peak was $unlimited", unlimited <= 30_100)
    }

    @Test
    fun `switching the limiter off during playback takes back the whole boost`() {
        val dsp = PlayerDspPcm16()
        dsp.configure(2)
        dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = true))
        dsp.updateCurve(listOf(TargetPoint(20f, 6f), TargetPoint(20_000f, 6f)), 0f, headroomSafetyRatio = 0.5f)
        val warmUp = ShortArray(4_000) { (sin(2 * PI * 1_000 * (it / 2) / rate) * 30_000).toInt().toShort() }
        dsp.process(warmUp, 0, warmUp.size, rate.toInt())

        dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = false))
        val samples = ShortArray(32_000) { (sin(2 * PI * 1_000 * (it / 2) / rate) * 30_000).toInt().toShort() }
        dsp.process(samples, 0, samples.size, rate.toInt())

        val peak = (16_000 until samples.size).maxOf { abs(samples[it].toInt()) }
        assertTrue("peak was $peak", peak <= 30_100)
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
