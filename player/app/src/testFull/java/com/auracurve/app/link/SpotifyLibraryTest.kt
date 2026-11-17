package com.auracurve.app.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyLibraryTest {
    private val idA = "4uLU6hMCjMI75M1A2tKUQC"
    private val idB = "7ouMYWpwJ422jRcDASZB7P"
    private val listId = "37i9dQZF1DXcBWIGoYBM5M"

    @Test
    fun `only 22 base-62 characters count as a spotify id`() {
        assertTrue(SpotifyLibraryJson.isSpotifyId(idA))
        assertFalse(SpotifyLibraryJson.isSpotifyId("short"))
        assertFalse(SpotifyLibraryJson.isSpotifyId("4uLU6hMCjMI75M1A2tKUQ:"))
        assertFalse(SpotifyLibraryJson.isSpotifyId("spotify:track:$idA"))
        assertFalse(SpotifyLibraryJson.isSpotifyId(null))
    }

    @Test
    fun `a playlists page with the old and the new song counter`() {
        val raw =
            """
            {"items":[
              {"id":"$listId","name":"Auto","owner":{"display_name":"Basti","id":"basti"},"items":{"total":412}},
              {"id":"$idB","name":"Alt","owner":{"id":"other"},"tracks":{"total":7}},
              {"id":"bad id","name":"Kaputt"}
            ],"next":"https://api.spotify.com/v1/me/playlists?offset=50"}
            """.trimIndent()

        val page = SpotifyLibraryJson.parsePlaylistsPage(raw)!!

        assertEquals(listOf(PlaylistInfo(listId, "Auto", "Basti", 412), PlaylistInfo(idB, "Alt", "other", 7)), page.entries)
        assertEquals("https://api.spotify.com/v1/me/playlists?offset=50", page.next)
        assertNull(SpotifyLibraryJson.parsePlaylistsPage("not json"))
    }

    @Test
    fun `a tracks page keeps ids, all artists, album and length and skips what cannot play`() {
        val raw =
            """
            {"items":[
              {"item":{"type":"track","id":"$idA","name":"Song","artists":[{"name":"A"},{"name":"B"}],"album":{"name":"LP"},"duration_ms":201000}},
              {"track":{"type":"track","id":"$idB","name":"Old shape","artists":[{"name":"C"}]}},
              {"item":{"type":"episode","id":"$listId","name":"Podcast"}},
              {"item":{"type":"track","is_local":true,"id":null,"name":"Local file"}},
              {"item":null}
            ],"next":null,"total":5}
            """.trimIndent()

        val page = SpotifyLibraryJson.parseTracksPage(raw)!!

        assertEquals(
            listOf(LibraryTrack(idA, "Song", listOf("A", "B"), "LP", 201_000), LibraryTrack(idB, "Old shape", listOf("C"))),
            page.entries,
        )
        assertNull(page.next)
    }

    @Test
    fun `the library survives a round trip and garbage reads as missing`() {
        val library =
            SpotifyLibrary(
                syncedAtMs = 1_700_000_000_000,
                playlists = listOf(LibraryPlaylist(listId, "Auto", "Basti", 2, listOf(idA, idB)), LibraryPlaylist(idB, "Fremd", "x", 9, null)),
                tracks = mapOf(idA to LibraryTrack(idA, "Song", listOf("A")), idB to LibraryTrack(idB, "Zwei", listOf("B"), "LP", 1)),
            )

        assertEquals(library, SpotifyLibraryJson.decode(SpotifyLibraryJson.encode(library)))
        assertNull(SpotifyLibraryJson.decode("{broken"))
        assertNull(SpotifyLibraryJson.decode(""))
        assertNull(SpotifyLibraryJson.decode(null))
        assertEquals(listId, library.playlist(listId)?.id)
    }
}
