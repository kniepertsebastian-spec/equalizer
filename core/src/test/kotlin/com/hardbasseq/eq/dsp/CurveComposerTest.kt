package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveComposerTest {
    @Test
    fun combineTwoCurves_sumsGainAtSharedPoint() {
        val correction = listOf(TargetPoint(100f, 2f))
        val voicing = listOf(TargetPoint(100f, 3f))

        val combined = CurveComposer.combine(correction, voicing)

        assertEquals(1, combined.size)
        assertEquals(5f, combined.first().gainDb, 0.001f)
    }

    @Test
    fun combineTwoCurves_emptyCorrectionIsNoOp() {
        val voicing = listOf(TargetPoint(100f, 3f))

        val combined = CurveComposer.combine(emptyList(), voicing)

        assertEquals(voicing, combined)
    }

    @Test
    fun combineList_sumsAcrossThreeCurves() {
        val correction = listOf(TargetPoint(60f, 1f))
        val voicing = listOf(TargetPoint(60f, 2f))
        val loudness = listOf(TargetPoint(60f, 3f))

        val combined = CurveComposer.combine(listOf(correction, voicing, loudness))

        assertEquals(1, combined.size)
        assertEquals(6f, combined.first().gainDb, 0.001f)
    }

    @Test
    fun combineList_unionsFrequenciesFromAllCurves() {
        val correction = listOf(TargetPoint(50f, 4f))
        val voicing = listOf(TargetPoint(100f, 2f))
        val loudness = listOf(TargetPoint(200f, 1f))

        val combined = CurveComposer.combine(listOf(correction, voicing, loudness))

        val frequencies = combined.map { it.frequencyHz }.toSet()
        assertEquals(setOf(50f, 100f, 200f), frequencies)
    }

    @Test
    fun combineList_ignoresEmptyCurvesAmongNonEmptyOnes() {
        val voicing = listOf(TargetPoint(100f, 3f))

        val combined = CurveComposer.combine(listOf(emptyList(), voicing, emptyList()))

        assertEquals(voicing, combined)
    }

    @Test
    fun combineList_allEmptyReturnsEmpty() {
        val combined = CurveComposer.combine(listOf(emptyList(), emptyList()))
        assertTrue(combined.isEmpty())
    }
}
