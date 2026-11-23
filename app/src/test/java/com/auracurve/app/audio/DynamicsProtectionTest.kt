package com.auracurve.app.audio

import com.auracurve.app.dsp.PreEqLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicsProtectionTest {
    @Test
    fun limiterThreshold_clampedToZeroDbfsMax() {
        val unsafeSettings =
            ProcessingSettings(
                limiterEnabled = true,
                limiterThresholdDb = 6.0f, // Unsafe positive threshold!
            )

        val safeThreshold = unsafeSettings.limiterThresholdDb.coerceAtMost(0f)
        assertEquals(0f, safeThreshold, 0.001f)
    }

    @Test
    fun defaultSettings_haveSafeLimiterThreshold() {
        val settings = ProcessingSettings()

        assertTrue(settings.limiterThresholdDb <= 0f)
    }

    // Roadmap 13, P13-1/P13-2: the order in which the engine asks the device for its effect chain.
    @Test
    fun attempts_highResolution_tryPostEqAndFiveBandsFirst_thenFallBackDeterministically() {
        val order = DynamicsAttempts.order(highResolutionEq = true)
        val fine = PreEqLayout.CUTOFFS_HZ
        val coarse = PreEqLayout.COARSE_CUTOFFS_HZ
        assertEquals(
            listOf(
                DynamicsAttempt(fine, 5, postEq = true),
                DynamicsAttempt(fine, 5),
                DynamicsAttempt(fine, 4),
                DynamicsAttempt(fine, 0),
                DynamicsAttempt(coarse, 5, postEq = true),
                DynamicsAttempt(coarse, 5),
                DynamicsAttempt(coarse, 4),
                DynamicsAttempt(coarse, 0),
                DynamicsAttempt(null, 5),
                DynamicsAttempt(null, 4),
                DynamicsAttempt(null, 0),
            ),
            order,
        )
    }

    @Test
    fun attempts_classicEq_neverAskForAPreOrPostEq() {
        val order = DynamicsAttempts.order(highResolutionEq = false)
        assertEquals(listOf(DynamicsAttempt(null, 5), DynamicsAttempt(null, 4), DynamicsAttempt(null, 0)), order)
        assertTrue(order.none { it.postEq || it.preEqCutoffsHz != null })
    }

    @Test
    fun refusedPostEqOrFiveBands_withTheLayoutInUse_isNoReasonToReportTheEqUnavailable() {
        val fine = PreEqLayout.CUTOFFS_HZ
        val failures =
            listOf(
                DynamicsAttempt(fine, 5, postEq = true) to "IllegalArgumentException: postEq",
                DynamicsAttempt(fine, 5) to "IllegalArgumentException: 5 bands",
            )
        assertNull(DynamicsAttempts.preEqUnavailableReason(failures, chosen = DynamicsAttempt(fine, 4)))
    }

    @Test
    fun refusedFineLayout_isReportedWhenTheCoarseOrNoPreEqRuns() {
        val fine = PreEqLayout.CUTOFFS_HZ
        val failures = DynamicsAttempts.order(true).take(4).map { it to "IllegalArgumentException: too many bands" }
        val coarse = DynamicsAttempt(PreEqLayout.COARSE_CUTOFFS_HZ, 5, postEq = true)
        val reason = DynamicsAttempts.preEqUnavailableReason(failures, chosen = coarse)
        assertTrue(reason!!.startsWith("${fine.size} bands"))
        assertTrue(DynamicsAttempts.preEqUnavailableReason(failures, chosen = null) != null)
    }

    @Test
    fun fiveBandMbc_splitsMudFromVocals_withTheCrossoversOfRoadmap13() {
        val bands = MbcTuning.bands(5, enabled = true, ratio = 4f, thresholdDb = -10f)
        assertEquals(listOf(50f, 135f, 350f, 3000f, 20000f), bands.map { it.cutoffFrequencyHz })
    }

    @Test
    fun fiveBandMbc_midsAndTrebleStayGentlerThanSubAndKick() {
        val bands = MbcTuning.bands(5, enabled = true, ratio = 6f, thresholdDb = -12f)
        val (sub, kick, mud, vocals, air) = bands
        assertEquals(3.5f, sub.ratio, 1e-4f)
        assertEquals(4f, kick.ratio, 1e-4f)
        assertEquals(1f + 5f * 0.55f, mud.ratio, 1e-4f)
        assertEquals(1.8f, vocals.ratio, 1e-4f)
        assertEquals(1.6f, air.ratio, 1e-4f)
        assertTrue(vocals.ratio < mud.ratio && mud.ratio < kick.ratio)
        assertTrue(air.ratio < kick.ratio && air.ratio < sub.ratio)
        // Vocals and air compress later and get less makeup gain than the bass bands.
        assertTrue(vocals.thresholdDb > kick.thresholdDb && air.thresholdDb > kick.thresholdDb)
        assertTrue(vocals.postGainDb < kick.postGainDb && air.postGainDb < kick.postGainDb)
        assertTrue(mud.postGainDb < kick.postGainDb)
    }

    @Test
    fun fiveBandMbc_attacksLetTheKickTransientThrough() {
        val (sub, kick, mud) = MbcTuning.bands(5, enabled = true, ratio = 3f, thresholdDb = -8f)
        assertTrue(sub.attackMs in 25f..30f && sub.releaseMs in 160f..180f)
        assertTrue(kick.attackMs in 18f..25f && kick.releaseMs in 90f..110f)
        assertTrue(mud.attackMs in 12f..15f)
    }

    @Test
    fun mbc_neverRaisesAThresholdAboveZero_andIsTransparentWhenOff() {
        listOf(4, 5).forEach { count ->
            MbcTuning.bands(count, enabled = true, ratio = 6f, thresholdDb = -2f).forEach { assertTrue(it.thresholdDb <= 0f) }
            MbcTuning.bands(count, enabled = false, ratio = 6f, thresholdDb = -20f).forEach { band ->
                assertEquals(1f, band.ratio, 0f)
                assertEquals(0f, band.postGainDb, 0f)
            }
        }
    }

    @Test
    fun fourBandFallback_keepsItsCrossovers_withGentleTreble() {
        val bands = MbcTuning.bands(4, enabled = true, ratio = 6f, thresholdDb = -12f)
        assertEquals(listOf(45f, 130f, 1500f, 20000f), bands.map { it.cutoffFrequencyHz })
        assertTrue(bands[3].ratio <= 1.6f)
        assertFalse(MbcTuning.bands(0, enabled = true, ratio = 6f, thresholdDb = -12f).isNotEmpty())
    }
}
