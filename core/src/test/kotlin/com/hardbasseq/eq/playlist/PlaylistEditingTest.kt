package com.hardbasseq.eq.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistEditingTest {
    private val kick = SavedTrack(1L, "Kick", "DJ A")
    private val snare = SavedTrack(2L, "Snare", "DJ B")

    @Test
    fun `creates a local playlist with a trimmed name`() {
        val playlist = PlaylistEditing.create("  Auto  ", emptyList(), nowMs = 100L)!!

        assertEquals("Auto", playlist.title)
        assertTrue(playlist.tracks.isEmpty())
        assertTrue(PlaylistEditing.isLocal(playlist))
    }

    @Test
    fun `a blank name creates nothing`() {
        assertNull(PlaylistEditing.create("   ", emptyList(), nowMs = 1L))
    }

    @Test
    fun `an over long name is shortened`() {
        val playlist = PlaylistEditing.create("x".repeat(200), emptyList(), nowMs = 1L)!!
        assertEquals(PlaylistEditing.MAX_TITLE_LENGTH, playlist.title.length)
    }

    @Test
    fun `can start with a first track`() {
        assertEquals(listOf(kick), PlaylistEditing.create("A", emptyList(), 1L, firstTrack = kick)!!.tracks)
    }

    @Test
    fun `ids stay unique when two playlists are made in the same millisecond`() {
        val first = PlaylistEditing.create("A", emptyList(), 5L)!!
        val second = PlaylistEditing.create("B", listOf(first), 5L)!!
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `an imported playlist is not local`() {
        assertFalse(PlaylistEditing.isLocal(SavedPlaylist("u", "T", sourceUrl = "https://soundcloud.com/a/sets/b", tracks = emptyList())))
    }

    @Test
    fun `adds a track once`() {
        val base = PlaylistEditing.create("A", emptyList(), 1L)!!
        val one = PlaylistEditing.addTrack(base, kick)
        val again = PlaylistEditing.addTrack(one, kick)

        assertEquals(listOf(kick), one.tracks)
        assertSame(one, again)
        assertEquals(listOf(kick, snare), PlaylistEditing.addTrack(one, snare).tracks)
    }

    @Test
    fun `removes a track`() {
        val playlist =
            PlaylistEditing
                .create(
                    "A",
                    emptyList(),
                    1L,
                )!!
                .let { PlaylistEditing.addTrack(PlaylistEditing.addTrack(it, kick), snare) }

        val result = PlaylistEditing.removeTrack(playlist, 1L)

        assertEquals(listOf(snare), result.tracks)
        assertFalse(PlaylistEditing.contains(result, 1L))
        assertTrue(PlaylistEditing.contains(result, 2L))
    }

    @Test
    fun `merging keeps the order and each track once`() {
        val first = PlaylistEditing.create("A", emptyList(), 1L, firstTrack = kick)!!.let { PlaylistEditing.addTrack(it, snare) }
        val second =
            PlaylistEditing.create("B", listOf(first), 2L, firstTrack = snare)!!.let {
                PlaylistEditing.addTrack(it, SavedTrack(3L, "Hat", "DJ C"))
            }

        val merged = PlaylistEditing.merge("Alles", listOf(first, second), listOf(first, second), 3L)!!

        assertEquals(listOf(1L, 2L, 3L), merged.tracks.map { it.id })
        assertEquals("Alles", merged.title)
        assertEquals(2, first.tracks.size)
        assertTrue(PlaylistEditing.isLocal(merged))
    }

    @Test
    fun `merging nothing or with a blank name makes nothing`() {
        val one = PlaylistEditing.create("A", emptyList(), 1L)!!
        assertNull(PlaylistEditing.merge("X", emptyList(), emptyList(), 1L))
        assertNull(PlaylistEditing.merge("  ", listOf(one), listOf(one), 2L))
    }
}
