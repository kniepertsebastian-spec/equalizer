package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeLevelMapperTest {
    @Test
    fun `full volume is 0 dB and half volume is about minus 6 dB`() {
        assertEquals(0f, VolumeLevelMapper.fractionToRelativeDb(1f), 0.001f)
        assertEquals(-6.02f, VolumeLevelMapper.fractionToRelativeDb(0.5f), 0.01f)
    }

    @Test
    fun `zero and out-of-range volumes stay finite`() {
        assertEquals(-40f, VolumeLevelMapper.fractionToRelativeDb(0f), 0.01f)
        assertEquals(0f, VolumeLevelMapper.fractionToRelativeDb(3f), 0.001f)
    }

    @Test
    fun `no compensation at or above the reference volume`() {
        assertTrue(VolumeLevelMapper.compensationCurve(0.5f, maxBoostDb = 9f).isEmpty())
        assertTrue(VolumeLevelMapper.compensationCurve(1f, maxBoostDb = 9f).isEmpty())
    }

    @Test
    fun `no compensation when the preset disables it`() {
        assertTrue(VolumeLevelMapper.compensationCurve(0.1f, maxBoostDb = 0f).isEmpty())
    }

    @Test
    fun `quieter volume boosts bass more and never beyond the configured maximum`() {
        fun bassBoost(volume: Float) = VolumeLevelMapper.compensationCurve(volume, maxBoostDb = 9f).first { it.frequencyHz == 60f }.gainDb

        val quiet = bassBoost(0.1f)
        val medium = bassBoost(0.3f)
        assertTrue(quiet > medium)
        assertTrue(medium > 0f)
        assertTrue(bassBoost(0.01f) <= 9f + 0.001f)
    }
}
