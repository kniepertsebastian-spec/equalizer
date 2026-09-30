package com.hardbasseq.eq.dsp

import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeLevelMapperTest {
    @Test
    fun `no compensation at or above the reference level`() {
        assertTrue(VolumeLevelMapper.compensationCurve(VolumeLevelMapper.REFERENCE_LEVEL_DB, maxBoostDb = 9f).isEmpty())
        assertTrue(VolumeLevelMapper.compensationCurve(0f, maxBoostDb = 9f).isEmpty())
    }

    @Test
    fun `no compensation when the preset disables it`() {
        assertTrue(VolumeLevelMapper.compensationCurve(-20f, maxBoostDb = 0f).isEmpty())
    }

    @Test
    fun `quieter level boosts bass more and never beyond the configured maximum`() {
        fun bassBoost(levelDb: Float) = VolumeLevelMapper.compensationCurve(levelDb, maxBoostDb = 9f).first { it.frequencyHz == 60f }.gainDb

        val quiet = bassBoost(-20f)
        val medium = bassBoost(-10f)
        assertTrue(quiet > medium)
        assertTrue(medium > 0f)
        assertTrue(bassBoost(-60f) <= 9f + 0.001f)
    }
}
