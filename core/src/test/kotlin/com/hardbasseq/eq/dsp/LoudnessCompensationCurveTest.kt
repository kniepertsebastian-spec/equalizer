package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessCompensationCurveTest {
    @Test
    fun atReferenceLevel_noCompensation() {
        val curve = LoudnessCompensationCurve.forLevel(currentLevelDb = 0f, referenceLevelDb = 0f)
        assertTrue(curve.isEmpty())
    }

    @Test
    fun aboveReferenceLevel_noCompensation() {
        // Louder than the reference - never subtract gain here, quiet-level
        // compensation only ever adds boost as level drops below reference.
        val curve = LoudnessCompensationCurve.forLevel(currentLevelDb = 10f, referenceLevelDb = 0f)
        assertTrue(curve.isEmpty())
    }

    @Test
    fun halfwayToFullDrop_appliesHalfMaxBoost() {
        // REFERENCE_DROP_DB is 40, so a 20 dB drop is exactly halfway.
        val curve =
            LoudnessCompensationCurve.forLevel(
                currentLevelDb = -20f,
                referenceLevelDb = 0f,
                maxBoostDb = 10f,
            )

        val bassGain = curve.first { it.frequencyHz == 60f }.gainDb
        assertEquals(5f, bassGain, 0.01f)
    }

    @Test
    fun beyondFullDrop_boostIsClampedNotExtrapolated() {
        val at40 = LoudnessCompensationCurve.forLevel(currentLevelDb = -40f, referenceLevelDb = 0f, maxBoostDb = 10f)
        val at80 = LoudnessCompensationCurve.forLevel(currentLevelDb = -80f, referenceLevelDb = 0f, maxBoostDb = 10f)

        val bassAt40 = at40.first { it.frequencyHz == 60f }.gainDb
        val bassAt80 = at80.first { it.frequencyHz == 60f }.gainDb
        assertEquals(10f, bassAt40, 0.01f)
        assertEquals(bassAt40, bassAt80, 0.001f)
    }

    @Test
    fun trebleBoostIsSmallerThanBassBoost() {
        val curve =
            LoudnessCompensationCurve.forLevel(
                currentLevelDb = -40f,
                referenceLevelDb = 0f,
                maxBoostDb = 10f,
            )

        val bassGain = curve.first { it.frequencyHz == 60f }.gainDb
        val trebleGain = curve.first { it.frequencyHz == 16000f }.gainDb
        assertTrue(trebleGain > 0f)
        assertTrue(trebleGain < bassGain)
    }

    @Test
    fun midrangeStaysUnaffected() {
        val curve =
            LoudnessCompensationCurve.forLevel(
                currentLevelDb = -40f,
                referenceLevelDb = 0f,
                maxBoostDb = 10f,
            )

        val midGain = curve.first { it.frequencyHz == 1000f }.gainDb
        assertEquals(0f, midGain, 0.001f)
    }

    @Test
    fun maxBoostIsClampedToSafeRange() {
        val curve =
            LoudnessCompensationCurve.forLevel(
                currentLevelDb = -40f,
                referenceLevelDb = 0f,
                maxBoostDb = 999f,
            )

        val bassGain = curve.first { it.frequencyHz == 60f }.gainDb
        assertEquals(15f, bassGain, 0.01f)
    }

    @Test
    fun composesWithCorrectionAndVoicingCurvesViaCurveComposer() {
        val correction = listOf(TargetPoint(60f, 2f))
        val voicing = listOf(TargetPoint(60f, 3f))
        val loudness = LoudnessCompensationCurve.forLevel(currentLevelDb = -40f, referenceLevelDb = 0f, maxBoostDb = 10f)

        val combined = CurveComposer.combine(listOf(correction, voicing, loudness))

        val bassGain = combined.first { it.frequencyHz == 60f }.gainDb
        assertEquals(2f + 3f + 10f, bassGain, 0.01f)
    }
}
