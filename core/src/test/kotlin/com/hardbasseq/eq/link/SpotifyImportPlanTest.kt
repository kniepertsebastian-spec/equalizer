package com.hardbasseq.eq.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyImportPlanTest {
    private fun tracks(count: Int) = (1..count).map { SpotifyTrack("Song $it", "Artist") }

    @Test
    fun `batches of 100 until everything is done`() {
        var state = SpotifyImportPlan.start("k", "Mix", tracks(250))
        val sizes = mutableListOf<Int>()

        while (!state.isDone) {
            val batch = SpotifyImportPlan.nextBatch(state)
            sizes.add(batch.size)
            state = SpotifyImportPlan.advance(state, batch.size, found = batch.size / 2, wrotePart = true)
        }

        assertEquals(listOf(100, 100, 50), sizes)
        assertEquals(250, state.nextIndex)
        assertEquals(3, state.partsWritten)
        assertEquals(125, state.foundSoFar)
        assertTrue(SpotifyImportPlan.nextBatch(state).isEmpty())
    }

    @Test
    fun `the next batch starts where the saved position is`() {
        val state = SpotifyImportPlan.start("k", "Mix", tracks(150)).copy(nextIndex = 100)

        assertEquals(tracks(150).drop(100), SpotifyImportPlan.nextBatch(state))
        assertEquals(2, SpotifyImportPlan.nextPartNumber(state))
    }

    @Test
    fun `a block without any hit does not count as a written part`() {
        val state = SpotifyImportPlan.advance(SpotifyImportPlan.start("k", "Mix", tracks(10)), 10, found = 0, wrotePart = false)

        assertEquals(0, state.partsWritten)
        assertTrue(state.isDone)
    }

    @Test
    fun `part count and names`() {
        assertEquals(0, SpotifyImportPlan.totalParts(0))
        assertEquals(1, SpotifyImportPlan.totalParts(100))
        assertEquals(2, SpotifyImportPlan.totalParts(101))
        assertEquals(4, SpotifyImportPlan.totalParts(400))
        assertEquals("Mix (from Spotify)", SpotifyImportPlan.playlistName("Mix", 1, 1))
        assertEquals("Mix – Part 2 of 4 (from Spotify)", SpotifyImportPlan.playlistName("Mix", 2, 4))
    }

    @Test
    fun `the key joins the playlist ids`() {
        assertEquals("a+b", SpotifyImportPlan.keyFor(listOf("a", "b")))
    }

    @Test
    fun `the state survives json and bad data reads as nothing pending`() {
        val state = SpotifyImportPlan.start("k", "Mix", tracks(3)).copy(nextIndex = 2, partsWritten = 1, foundSoFar = 2)

        assertEquals(state, SpotifyImportJson.decode(SpotifyImportJson.encode(state)))
        assertNull(SpotifyImportJson.decode(null))
        assertNull(SpotifyImportJson.decode(""))
        assertNull(SpotifyImportJson.decode("{broken"))
        assertFalse(state.isDone)
    }
}
