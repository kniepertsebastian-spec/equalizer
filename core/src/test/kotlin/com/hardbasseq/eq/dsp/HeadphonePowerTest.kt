package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadphonePowerTest {
    @Test
    fun defaultCurveMatchesLegacyValues() {
        assertEquals(HeadphoneComfortCurve.curve(2.5f), HeadphoneComfortCurve.curve)
        assertEquals(1f, HeadphoneComfortCurve.curve.first { it.frequencyHz == 150f }.gainDb, 0.001f)
    }

    @Test
    fun bassScalesCurve() {
        val c = HeadphoneComfortCurve.curve(7f)
        assertEquals(7f, c.first().gainDb, 0.001f)
        assertEquals(2.8f, c.first { it.frequencyHz == 150f }.gainDb, 0.001f)
    }

    @Test
    fun knallIsStrongerThanDefault() {
        assertTrue(HeadphonePower.KNALL.bassDb > HeadphonePower.DEFAULT.bassDb)
        assertTrue(HeadphonePower.KNALL.loudnessMaxDb > HeadphonePower.DEFAULT.loudnessMaxDb)
        assertFalse(HeadphonePower.KNALL.easeDynamics)
    }

    @Test
    fun sanitizedClamps() {
        val p = HeadphonePower(bassDb = 40f, loudnessMaxDb = -3f).sanitized()
        assertEquals(HeadphonePower.MAX_BASS_DB, p.bassDb, 0.001f)
        assertEquals(0f, p.loudnessMaxDb, 0.001f)
    }
}
