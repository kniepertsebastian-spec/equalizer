package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerInterpolatorTest {

    private val sampleBands = listOf(
        EqualizerBandCapabilities(0, 60, -15f, 15f),
        EqualizerBandCapabilities(1, 230, -15f, 15f),
        EqualizerBandCapabilities(2, 910, -15f, 15f),
        EqualizerBandCapabilities(3, 3600, -15f, 15f),
        EqualizerBandCapabilities(4, 14000, -15f, 15f),
    )

    @Test
    fun interpolateFrequency_exactMatchReturnsTargetGain() {
        val points = listOf(
            TargetPoint(50f, 3f),
            TargetPoint(100f, 1f)
        )
        val gain = EqualizerInterpolator.interpolateFrequency(50f, points)
        assertEquals(3f, gain, 0.001f)
    }

    @Test
    fun interpolateFrequency_logarithmicMidpointCorrect() {
        // Log midpoint between 10 Hz and 100 Hz is sqrt(10*100) = 31.62 Hz
        val points = listOf(
            TargetPoint(10f, 0f),
            TargetPoint(100f, 10f)
        )
        val gainAtMid = EqualizerInterpolator.interpolateFrequency(31.622777f, points)
        assertEquals(5f, gainAtMid, 0.01f)
    }

    @Test
    fun interpolatePresetToBands_cleanPunchPresetMappedAndClamped() {
        val gains = EqualizerInterpolator.interpolatePresetToBands(
            preset = BuiltInPresets.CleanPunch,
            bands = sampleBands,
        )

        assertEquals(5, gains.size)
        assertTrue(gains[0]!! > 0f) // 60 Hz should be boosted
        assertTrue(gains[3]!! > 0f) // 3600 Hz should be boosted
    }

    @Test
    fun calculateHeadroom_detectsClippingRiskWhenPositiveGainPresent() {
        val bandGains = mapOf(0 to 3f, 1 to -2f)
        val headroom = EqualizerInterpolator.calculateHeadroom(bandGains)

        assertTrue(headroom.isClippingRisk)
        assertEquals(3f, headroom.maxPositiveGainDb, 0.001f)
        assertEquals(-3f, headroom.recommendedInputGainDb, 0.001f)
    }

    @Test
    fun calculateHeadroom_noClippingRiskForFlatOrNegativeGains() {
        val bandGains = mapOf(0 to 0f, 1 to -2f)
        val headroom = EqualizerInterpolator.calculateHeadroom(bandGains)

        assertFalse(headroom.isClippingRisk)
        assertEquals(0f, headroom.maxPositiveGainDb, 0.001f)
        assertEquals(0f, headroom.recommendedInputGainDb, 0.001f)
    }
}
