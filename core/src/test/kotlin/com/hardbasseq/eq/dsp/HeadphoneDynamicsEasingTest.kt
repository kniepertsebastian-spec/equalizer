package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadphoneDynamicsEasingTest {
    @Test
    fun `threshold is raised by a fixed amount so the compressor engages later`() {
        assertEquals(-6f, HeadphoneDynamicsEasing.easedThresholdDb(-8f), 0.001f)
        assertEquals(-4f, HeadphoneDynamicsEasing.easedThresholdDb(-6f), 0.001f)
    }

    @Test
    fun `threshold never rises above 0 dBFS even for an already-high base`() {
        assertEquals(0f, HeadphoneDynamicsEasing.easedThresholdDb(-1f), 0.001f)
        assertEquals(0f, HeadphoneDynamicsEasing.easedThresholdDb(0f), 0.001f)
    }

    @Test
    fun `ratio is pulled proportionally toward 1 (transparent)`() {
        // base 3.5 -> 1 + 2.5*0.75 = 2.875
        assertEquals(2.875f, HeadphoneDynamicsEasing.easedRatio(3.5f), 0.001f)
        // base 2.0 -> 1 + 1.0*0.75 = 1.75
        assertEquals(1.75f, HeadphoneDynamicsEasing.easedRatio(2.0f), 0.001f)
    }

    @Test
    fun `a higher base ratio is still eased to something higher than a lower base ratio's`() {
        val easedLow = HeadphoneDynamicsEasing.easedRatio(1.8f)
        val easedHigh = HeadphoneDynamicsEasing.easedRatio(3.5f)
        assertTrue(easedHigh > easedLow)
    }

    @Test
    fun `an already-transparent ratio of 1 stays 1`() {
        assertEquals(1f, HeadphoneDynamicsEasing.easedRatio(1f), 0.001f)
    }
}
