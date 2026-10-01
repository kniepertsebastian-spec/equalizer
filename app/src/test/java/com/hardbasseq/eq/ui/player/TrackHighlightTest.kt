package com.hardbasseq.eq.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackHighlightTest {
    @Test
    fun `the playing track is current even if it was played before`() {
        assertEquals(TrackPlayState.CURRENT, trackPlayState(1L, currentTrackId = 1L, playedIds = setOf(1L)))
    }

    @Test
    fun `an earlier track is played and an untouched one is new`() {
        assertEquals(TrackPlayState.PLAYED, trackPlayState(2L, currentTrackId = 1L, playedIds = setOf(1L, 2L)))
        assertEquals(TrackPlayState.NEW, trackPlayState(3L, currentTrackId = 1L, playedIds = setOf(1L, 2L)))
    }

    @Test
    fun `nothing is current when nothing plays`() {
        assertEquals(TrackPlayState.NEW, trackPlayState(1L, currentTrackId = null, playedIds = emptySet()))
    }

    @Test
    fun `only played tracks are dimmed`() {
        assertEquals(1f, TrackPlayState.CURRENT.textAlpha(), 0f)
        assertEquals(1f, TrackPlayState.NEW.textAlpha(), 0f)
        assertEquals(0.6f, TrackPlayState.PLAYED.textAlpha(), 0f)
    }
}
