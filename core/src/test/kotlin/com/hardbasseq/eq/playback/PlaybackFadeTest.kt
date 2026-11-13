package com.hardbasseq.eq.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFadeTest {
    @Test
    fun curves_runFromZeroToOne() {
        assertEquals(0f, PlaybackFade.fadeIn(0f), 1e-6f)
        assertEquals(1f, PlaybackFade.fadeIn(1f), 1e-6f)
        assertEquals(1f, PlaybackFade.fadeOut(0f), 1e-6f)
        assertEquals(0f, PlaybackFade.fadeOut(1f), 1e-6f)
    }

    @Test
    fun curves_areEqualPowerAtTheMidpoint() {
        // In + out at the same time (a true overlap) would sum to constant power.
        val inGain = PlaybackFade.fadeIn(0.5f)
        val outGain = PlaybackFade.fadeOut(0.5f)
        assertEquals(1f, inGain * inGain + outGain * outGain, 1e-5f)
    }

    @Test
    fun trackEdgeGain_isFullInTheMiddleAndOffWithoutFade() {
        assertEquals(1f, PlaybackFade.trackEdgeGain(60_000, 180_000, 5_000), 0f)
        assertEquals(1f, PlaybackFade.trackEdgeGain(0, 180_000, 0), 0f)
    }

    @Test
    fun trackEdgeGain_fadesInAtTheStartAndOutAtTheEnd() {
        assertEquals(0f, PlaybackFade.trackEdgeGain(0, 180_000, 5_000), 1e-6f)
        assertTrue(PlaybackFade.trackEdgeGain(2_500, 180_000, 5_000) in 0.5f..0.9f)
        assertTrue(PlaybackFade.trackEdgeGain(177_500, 180_000, 5_000) in 0.5f..0.9f)
        assertEquals(0f, PlaybackFade.trackEdgeGain(180_000, 180_000, 5_000), 1e-5f)
    }

    @Test
    fun trackEdgeGain_shortTrackNeverOverlapsItsOwnRamps() {
        // 6 s long, 5 s requested: each edge gets 3 s, so the middle is exactly 1.
        assertEquals(1f, PlaybackFade.trackEdgeGain(3_000, 6_000, 5_000), 1e-5f)
        val quarter = PlaybackFade.trackEdgeGain(1_500, 6_000, 5_000)
        assertTrue(quarter in 0.1f..0.99f)
    }

    @Test
    fun trackEdgeGain_unknownDurationOnlyFadesIn() {
        assertEquals(0f, PlaybackFade.trackEdgeGain(0, 0, 5_000), 1e-6f)
        assertEquals(1f, PlaybackFade.trackEdgeGain(10_000, 0, 5_000), 0f)
    }

    @Test
    fun sleepGain_isFullUntilTheLastFadeThenReachesZero() {
        assertEquals(1f, PlaybackFade.sleepGain(10 * 60_000L), 0f)
        assertEquals(1f, PlaybackFade.sleepGain(PlaybackFade.SLEEP_FADE_MS), 0f)
        assertTrue(PlaybackFade.sleepGain(PlaybackFade.SLEEP_FADE_MS / 2) in 0.1f..0.95f)
        assertEquals(0f, PlaybackFade.sleepGain(0), 0f)
        assertEquals(0f, PlaybackFade.sleepGain(-5), 0f)
    }
}
