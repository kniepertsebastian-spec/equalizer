package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessMatchTest {
    private val flat = listOf(60f to 0f, 230f to 0f, 910f to 0f, 3600f to 0f, 14000f to 0f)

    @Test
    fun flatCurveHasNoDifference() {
        assertEquals(0f, LoudnessMatch.eqLevelDeltaDb(flat, 0f), 0.001f)
        assertEquals(0f, LoudnessMatch.meanGainDb(emptyList()), 0.001f)
    }

    @Test
    fun uniformBoostCountsFully() {
        val boosted = flat.map { it.first to 4f }
        assertEquals(4f, LoudnessMatch.meanGainDb(boosted), 0.01f)
    }

    @Test
    fun bassBoostCountsLessThanMidBoostBecauseTheEarHearsBassLessWell() {
        val bass = LoudnessMatch.meanGainDb(listOf(60f to 6f, 910f to 0f, 3600f to 0f))
        val mid = LoudnessMatch.meanGainDb(listOf(60f to 0f, 910f to 6f, 3600f to 0f))
        assertTrue(bass < mid)
    }

    @Test
    fun inputGainLowersTheEstimate() {
        assertEquals(-3f, LoudnessMatch.eqLevelDeltaDb(flat, -3f), 0.001f)
    }

    @Test
    fun theLouderSideIsTurnedDown() {
        val louder = LoudnessMatch.plan(3f)
        assertEquals(0f, louder.originalGainDb, 0f)
        assertEquals(-3f, louder.eqExtraGainDb, 0f)
        val quieter = LoudnessMatch.plan(-2f)
        assertEquals(-2f, quieter.originalGainDb, 0f)
        assertEquals(0f, quieter.eqExtraGainDb, 0f)
    }

    @Test
    fun theAttenuationIsLimited() {
        assertEquals(-15f, LoudnessMatch.plan(40f).eqExtraGainDb, 0f)
        assertEquals(-15f, LoudnessMatch.plan(-40f).originalGainDb, 0f)
    }
}
