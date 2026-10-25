package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualBassApproxCurveTest {
    @Test
    fun `mix zero gives no curve`() {
        assertTrue(VirtualBassApproxCurve.forMix(0f).isEmpty())
    }

    @Test
    fun `mix one gives the documented points`() {
        val gains = VirtualBassApproxCurve.forMix(1f).associate { it.frequencyHz to it.gainDb }

        assertEquals(-6f, gains.getValue(30f), 0f)
        assertEquals(-3f, gains.getValue(45f), 0f)
        assertEquals(0f, gains.getValue(70f), 0f)
        assertEquals(3f, gains.getValue(110f), 0f)
        assertEquals(2.5f, gains.getValue(160f), 0f)
        assertEquals(0f, gains.getValue(250f), 0f)
    }

    @Test
    fun `the mix scales the gains`() {
        val gains = VirtualBassApproxCurve.forMix(0.5f).associate { it.frequencyHz to it.gainDb }

        assertEquals(-3f, gains.getValue(30f), 0f)
        assertEquals(1.5f, gains.getValue(110f), 0f)
    }
}
