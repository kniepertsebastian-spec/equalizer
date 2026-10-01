package com.hardbasseq.eq.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMediaIdTest {
    @Test
    fun `folders round trip`() {
        assertEquals(AutoMediaId.Root, AutoMediaId.parse(AutoMediaId.ROOT))
        assertEquals(AutoMediaId.PlaylistsFolder, AutoMediaId.parse(AutoMediaId.PLAYLISTS))
        assertEquals(AutoMediaId.DiscoveryFolder, AutoMediaId.parse(AutoMediaId.DISCOVERY))
        assertEquals(AutoMediaId.LikesFolder, AutoMediaId.parse(AutoMediaId.LIKES))
    }

    @Test
    fun `a playlist id with colons and slashes survives`() {
        val playlistId = "https://soundcloud.com/a/sets/mix?x=1"

        assertEquals(AutoMediaId.PlaylistFolder(playlistId), AutoMediaId.parse(AutoMediaId.playlistFolder(playlistId)))
        assertEquals(
            AutoMediaId.Track(AutoMediaId.Source.PLAYLIST, playlistId, 42L),
            AutoMediaId.parse(AutoMediaId.playlistTrack(playlistId, 42L)),
        )
    }

    @Test
    fun `discovery and like tracks`() {
        assertEquals(AutoMediaId.Track(AutoMediaId.Source.DISCOVERY, "", 7L), AutoMediaId.parse(AutoMediaId.discoveryTrack(7L)))
        assertEquals(AutoMediaId.Track(AutoMediaId.Source.LIKES, "", 8L), AutoMediaId.parse(AutoMediaId.likedTrack(8L)))
    }

    @Test
    fun `only tracks are playable`() {
        assertTrue(AutoMediaId.isTrack(AutoMediaId.discoveryTrack(1L)))
        assertFalse(AutoMediaId.isTrack(AutoMediaId.PLAYLISTS))
        assertFalse(AutoMediaId.isTrack(AutoMediaId.playlistFolder("x")))
    }

    @Test
    fun `foreign or broken ids read as null`() {
        assertNull(AutoMediaId.parse(""))
        assertNull(AutoMediaId.parse("spotify:track:123"))
        assertNull(AutoMediaId.parse("pl|"))
        assertNull(AutoMediaId.parse("pl|x|notanumber"))
        assertNull(AutoMediaId.parse("disc|1"))
        assertNull(AutoMediaId.parse("pl|a|b|c"))
    }
}
