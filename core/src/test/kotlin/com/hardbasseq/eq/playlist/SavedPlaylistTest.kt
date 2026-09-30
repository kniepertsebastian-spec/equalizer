package com.hardbasseq.eq.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlaylistTest {
    private val sample =
        SavedPlaylist(
            id = "p1",
            title = "Uptempo Mix",
            sourceUrl = "https://soundcloud.com/a/sets/mix",
            tracks =
                listOf(
                    SavedTrack(1L, "Kick", "DJ A", "https://img/1.jpg", 180_000L),
                    SavedTrack(2L, "Snare", "DJ B"),
                ),
            createdAtMs = 42L,
        )

    @Test
    fun `round trips through json`() {
        assertEquals(listOf(sample), SavedPlaylistJson.decode(SavedPlaylistJson.encode(listOf(sample))))
    }

    @Test
    fun `missing or corrupt data reads as no playlists`() {
        assertTrue(SavedPlaylistJson.decode(null).isEmpty())
        assertTrue(SavedPlaylistJson.decode("").isEmpty())
        assertTrue(SavedPlaylistJson.decode("{not json").isEmpty())
    }

    @Test
    fun `unknown fields from a newer version are ignored`() {
        val raw = """{"playlists":[{"id":"p","title":"T","tracks":[],"futureField":1}],"extra":true}"""
        assertEquals("T", SavedPlaylistJson.decode(raw).single().title)
    }

    @Test
    fun `optional fields default when absent`() {
        val raw = """{"playlists":[{"id":"p","title":"T","tracks":[{"id":5,"title":"x","artist":"y"}]}]}"""
        val track =
            SavedPlaylistJson
                .decode(raw)
                .single()
                .tracks
                .single()
        assertNull(track.artworkUrl)
        assertEquals(0L, track.durationMs)
    }
}
