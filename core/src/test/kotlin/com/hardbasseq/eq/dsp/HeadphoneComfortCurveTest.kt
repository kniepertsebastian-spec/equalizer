package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadphoneComfortCurveTest {
    @Test
    fun boostsBassRelativeToMidrange() {
        val bassGain = EqualizerInterpolator.interpolateFrequency(60f, HeadphoneComfortCurve.curve)
        val midGain = EqualizerInterpolator.interpolateFrequency(1000f, HeadphoneComfortCurve.curve)
        assertTrue(bassGain > midGain)
    }

    @Test
    fun gentlyReducesPresence() {
        val presenceGain = EqualizerInterpolator.interpolateFrequency(4500f, HeadphoneComfortCurve.curve)
        assertTrue(presenceGain < 0f)
    }

    @Test
    fun staysWithinModerateBounds() {
        for (point in HeadphoneComfortCurve.curve) {
            assertTrue("gain at ${point.frequencyHz} Hz (${point.gainDb} dB) should be a moderate tilt", point.gainDb in -6f..6f)
        }
    }

    @Test
    fun composesWithAnEmptyCorrectionAndVoicingCurve() {
        val combined = CurveComposer.combine(listOf(emptyList(), emptyList(), HeadphoneComfortCurve.curve))
        assertEquals(HeadphoneComfortCurve.curve.sortedBy { it.frequencyHz }, combined)
    }
}
