package com.hardbasseq.eq.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyImportLedgerTest {
    private val a = SpotifyTrack("Song A", "Artist")
    private val b = SpotifyTrack("Song B", "Artist")
    private val c = SpotifyTrack("Song C", "Other")

    private fun key(track: SpotifyTrack) = SpotifyImportLedger.songKey(track)

    @Test
    fun `songs imported before are skipped while their track is still in a playlist`() {
        val ledger = SpotifyImportLedger.record(SpotifyImportLedger(), "src", mapOf(key(a) to 10L, key(b) to 11L))

        val todo = SpotifyImportLedger.remaining(ledger, "src", listOf(a, b, c), presentTrackIds = setOf(10L, 11L))

        assertEquals(listOf(c), todo)
    }

    @Test
    fun `a song whose playlist was deleted comes back`() {
        val ledger = SpotifyImportLedger.record(SpotifyImportLedger(), "src", mapOf(key(a) to 10L, key(b) to 11L))

        val todo = SpotifyImportLedger.remaining(ledger, "src", listOf(a, b), presentTrackIds = setOf(11L))

        assertEquals(listOf(a), todo)
    }

    @Test
    fun `songs that were not found are tried again`() {
        val ledger = SpotifyImportLedger.record(SpotifyImportLedger(), "src", mapOf(key(a) to SpotifyImportLedger.NOT_FOUND))

        assertEquals(listOf(a), SpotifyImportLedger.remaining(ledger, "src", listOf(a), presentTrackIds = emptySet()))
    }

    @Test
    fun `another source or case and spacing differences`() {
        val ledger = SpotifyImportLedger.record(SpotifyImportLedger(), "src", mapOf(key(a) to 10L))

        assertEquals(listOf(a), SpotifyImportLedger.remaining(ledger, "other", listOf(a), setOf(10L)))
        val shouting = SpotifyTrack("  SONG A ", "artist")
        assertTrue(SpotifyImportLedger.remaining(ledger, "src", listOf(shouting), setOf(10L)).isEmpty())
    }

    @Test
    fun `recording adds to what is there and the oldest sources go first`() {
        var ledger = SpotifyImportLedger.record(SpotifyImportLedger(), "src", mapOf(key(a) to 1L))
        ledger = SpotifyImportLedger.record(ledger, "src", mapOf(key(b) to 2L))
        assertEquals(mapOf(key(a) to 1L, key(b) to 2L), ledger.sources["src"])

        for (i in 1..60) ledger = SpotifyImportLedger.record(ledger, "s$i", mapOf(key(a) to 1L))
        assertEquals(50, ledger.sources.size)
        assertTrue("s60" in ledger.sources)
        assertTrue("src" !in ledger.sources)
    }

    @Test
    fun `json round trip and bad data`() {
        val ledger = SpotifyImportLedger.record(SpotifyImportLedger(), "src", mapOf(key(a) to 1L))

        assertEquals(ledger, SpotifyImportLedger.decode(SpotifyImportLedger.encode(ledger)))
        assertEquals(SpotifyImportLedger(), SpotifyImportLedger.decode("{broken"))
        assertEquals(SpotifyImportLedger(), SpotifyImportLedger.decode(null))
    }
}
