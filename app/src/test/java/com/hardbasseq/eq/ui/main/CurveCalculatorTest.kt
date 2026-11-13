package com.hardbasseq.eq.ui.main

import com.hardbasseq.eq.audio.AudioCapabilities
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.dsp.HeadphonePower
import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveCalculatorTest {
    private val flat =
        CurveInputs(
            correctionCurve = emptyList(),
            presetCurve = emptyList(),
            headphoneAcoustics = false,
            headphonePower = HeadphonePower(),
            currentLevelDb = 0f,
            goalCurve = emptyList(),
            context = null,
            ownPlayerSession = true,
            preEqAvailable = false,
            settings = ProcessingSettings(loudnessMaxBoostDb = 0f, subsonicCutoffHz = 0f),
        )

    private fun gainAt(
        curve: List<TargetPoint>,
        hz: Float,
    ): Float = curve.minBy { kotlin.math.abs(it.frequencyHz - hz) }.gainDb

    @Test
    fun combinedCurve_withNothingActive_isFlat() {
        val curve = CurveCalculator.combinedCurve(flat)

        assertTrue(curve.all { it.gainDb == 0f })
    }

    @Test
    fun combinedCurve_addsCorrectionAndPreset() {
        val inputs =
            flat.copy(
                correctionCurve = listOf(TargetPoint(100f, 2f), TargetPoint(1000f, 2f)),
                presetCurve = listOf(TargetPoint(100f, 3f), TargetPoint(1000f, 3f)),
            )

        assertEquals(5f, gainAt(CurveCalculator.combinedCurve(inputs), 100f), 0.01f)
    }

    @Test
    fun combinedCurve_subsonicFilterCanBeLeftOut() {
        val inputs = flat.copy(settings = flat.settings.copy(subsonicCutoffHz = 30f))

        val with = CurveCalculator.combinedCurve(inputs, includeSubsonic = true)
        val without = CurveCalculator.combinedCurve(inputs, includeSubsonic = false)

        assertTrue("the high-pass cuts the lowest octave", with.minOf { it.gainDb } < -1f)
        assertTrue(without.all { it.gainDb == 0f })
    }

    @Test
    fun combinedCurve_headphoneModeChangesTheCurve() {
        val speaker = CurveCalculator.combinedCurve(flat)
        val headphones = CurveCalculator.combinedCurve(flat.copy(headphoneAcoustics = true))

        assertTrue(speaker != headphones)
    }

    @Test
    fun preEqGains_areEmptyWithoutAPreEq() {
        val caps = AudioCapabilities(bands = listOf(EqualizerBandCapabilities(0, 60, -15f, 15f)))

        assertTrue(CurveCalculator.preEqGains(flat, caps, mapOf(0 to 3f)).isEmpty())
    }

    @Test
    fun eqLevelDeltaDb_isZeroForAFlatEq() {
        val bands = listOf(EqualizerBandCapabilities(0, 60, -15f, 15f), EqualizerBandCapabilities(1, 1000, -15f, 15f))

        assertEquals(0f, CurveCalculator.eqLevelDeltaDb(ProcessingSettings(), bands), 0.01f)
    }

    @Test
    fun eqLevelDeltaDb_risesWithBoostedBands() {
        val bands = listOf(EqualizerBandCapabilities(0, 60, -15f, 15f), EqualizerBandCapabilities(1, 1000, -15f, 15f))
        val boosted = ProcessingSettings(bandGainsDb = mapOf(0 to 6f, 1 to 6f))

        assertTrue(CurveCalculator.eqLevelDeltaDb(boosted, bands) > 3f)
    }
}
