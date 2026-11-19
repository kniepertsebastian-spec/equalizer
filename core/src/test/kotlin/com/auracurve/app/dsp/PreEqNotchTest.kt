package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap 07, L1: one measurement gap in the pre-EQ. */
class PreEqNotchTest {
    private val cutoffs = PreEqLayout.CUTOFFS_WITH_NOTCH_HZ
    private val notch = PreEqLayout.notchBandIndex(cutoffs)

    @Test
    fun `the notch layout has 36 strictly increasing edges and contains the gap`() {
        assertEquals(36, cutoffs.size)
        cutoffs.zipWithNext().forEach { (a, b) -> assertTrue("$b > $a", b > a) }
        assertTrue(1000f in cutoffs && 1046f in cutoffs)
        assertEquals(1000f, cutoffs[notch - 1])
        assertEquals(1046f, cutoffs[notch])
        // The old 920-1120 Hz band is split in three.
        assertEquals(920f, cutoffs[notch - 2])
        assertEquals(1120f, cutoffs[notch + 1])
    }

    @Test
    fun `the normal layouts have no gap`() {
        assertEquals(-1, PreEqLayout.notchBandIndex(PreEqLayout.CUTOFFS_HZ))
        assertEquals(-1, PreEqLayout.notchBandIndex(PreEqLayout.COARSE_CUTOFFS_HZ))
    }

    @Test
    fun `the gap gets exactly the notch gain, its neighbours follow the curve`() {
        // A gentle +6 dB lift around 1 kHz.
        val curve = listOf(TargetPoint(500f, 6f), TargetPoint(1000f, 6f), TargetPoint(2000f, 6f))
        val gains = PreEqMapper.map(curve, PreEqMacros(), cutoffs)
        assertEquals(PreEqLayout.NOTCH_GAIN_DB, gains[notch], 0f)
        assertEquals(6f, gains[notch - 1], 0.5f)
        assertEquals(6f, gains[notch + 1], 0.5f)
    }

    @Test
    fun `a flat curve stays flat except for the gap`() {
        val gains = PreEqMapper.map(emptyList(), PreEqMacros(), cutoffs)
        gains.forEachIndexed { i, g -> assertEquals("band $i", if (i == notch) PreEqLayout.NOTCH_GAIN_DB else 0f, g, 0f) }
    }

    @Test
    fun `without the notch layout the mapping is unchanged`() {
        val gains = PreEqMapper.map(emptyList(), PreEqMacros(), PreEqLayout.CUTOFFS_HZ)
        assertTrue(gains.all { it == 0f })
    }
}
