package com.hardbasseq.eq.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSearchTest {
    private val tracks =
        listOf(
            SavedTrack(1L, "Drum Go Bang", "Angerfist"),
            SavedTrack(2L, "Café del Mar", "Energy 52"),
            SavedTrack(3L, "Bomb (Uptempo Mix)", "Miss K8"),
            SavedTrack(4L, "Criminally Insane", "Angerfist"),
        )

    @Test
    fun `an empty search keeps everything in order`() {
        assertEquals(listOf(0, 1, 2, 3), PlaylistSearch.filter(tracks, "").map { it.index })
        assertEquals(listOf(0, 1, 2, 3), PlaylistSearch.filter(tracks, "   ").map { it.index })
    }

    @Test
    fun `finds by title or artist and keeps the place in the playlist`() {
        val hits = PlaylistSearch.filter(tracks, "angerfist")

        assertEquals(listOf(0, 3), hits.map { it.index })
        assertEquals(listOf(1L, 4L), hits.map { it.track.id })
    }

    @Test
    fun `parts of words, case and accents do not matter`() {
        assertEquals(listOf(1), PlaylistSearch.filter(tracks, "CAFE").map { it.index })
        assertEquals(listOf(0, 3), PlaylistSearch.filter(tracks, "angerf").map { it.index })
    }

    @Test
    fun `every typed word has to match`() {
        assertEquals(listOf(3), PlaylistSearch.filter(tracks, "angerfist insane").map { it.index })
        assertEquals(listOf(2), PlaylistSearch.filter(tracks, "uptempo k8").map { it.index })
        assertTrue(PlaylistSearch.filter(tracks, "angerfist bomb").isEmpty())
    }

    @Test
    fun `nothing matches gives an empty list`() {
        assertTrue(PlaylistSearch.filter(tracks, "xyz").isEmpty())
        assertTrue(PlaylistSearch.filter(emptyList(), "a").isEmpty())
    }
}
