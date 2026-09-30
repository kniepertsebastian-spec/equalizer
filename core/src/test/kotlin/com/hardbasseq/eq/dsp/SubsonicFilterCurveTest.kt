package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicFilterCurveTest {
    @Test
    fun `zero or negative cutoff means off`() {
        assertTrue(SubsonicFilterCurve.forCutoff(0f).isEmpty())
        assertTrue(SubsonicFilterCurve.forCutoff(-10f).isEmpty())
    }

    @Test
    fun `curve is sorted by frequency with no duplicate points`() {
        val freqs = SubsonicFilterCurve.forCutoff(35f).map { it.frequencyHz }
        assertEquals(freqs.sorted(), freqs)
        assertEquals(freqs.size, freqs.toSet().size)
    }

    @Test
    fun `gain is about minus 3 dB at the cutoff`() {
        val atCutoff = SubsonicFilterCurve.forCutoff(40f).first { it.frequencyHz == 40f }
        assertEquals(-3f, atCutoff.gainDb, 0.5f)
    }

    @Test
    fun `attenuates below the cutoff and leaves the range above untouched`() {
        val curve = SubsonicFilterCurve.forCutoff(40f)
        assertTrue(curve.first { it.frequencyHz == 20f }.gainDb < -9f)
        assertTrue(curve.last().gainDb > -0.5f)
        // Monotonic: never gets quieter as frequency rises.
        curve.zipWithNext().forEach { (a, b) -> assertTrue(b.gainDb >= a.gainDb - 0.001f) }
    }

    @Test
    fun `cutoff is clamped to the supported range`() {
        val high = SubsonicFilterCurve.forCutoff(500f)
        assertTrue(high.any { it.frequencyHz == SubsonicFilterCurve.MAX_CUTOFF_HZ })
        val low = SubsonicFilterCurve.forCutoff(1f)
        assertTrue(low.any { it.frequencyHz == SubsonicFilterCurve.MIN_CUTOFF_HZ })
    }

    @Test
    fun `gains never boost and stay within the importable range`() {
        SubsonicFilterCurve.forCutoff(80f).forEach { point: TargetPoint ->
            assertTrue(point.gainDb <= 0f)
            assertTrue(point.gainDb >= -24f)
        }
    }
}
