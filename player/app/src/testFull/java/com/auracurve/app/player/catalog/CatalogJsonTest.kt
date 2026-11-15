package com.auracurve.app.player.catalog

import com.auracurve.app.playlist.DirectStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Against real answers of both APIs (saved 15.11.2026, trimmed; one synthetic entry each to check
// the filters: a plain-http station and a gated Audius track).
class CatalogJsonTest {
    private fun resource(name: String): String = javaClass.getResource("/catalog/$name")!!.readText()

    @Test
    fun `radio stations keep only https and become playable tracks`() {
        val stations = CatalogJson.radioStations(resource("radio_hardstyle.json"))

        assertEquals(3, stations.size)
        assertTrue(stations.all { it.streamUrl.startsWith("https://") })
        assertFalse(stations.any { it.uuid == "plain-http" })
        val first = stations.first().toTrack()
        assertEquals(DirectStream.idOf("radio", stations.first().uuid), first.id)
        assertTrue(DirectStream.playsDirectly(first.id, first.streamUrl))
        assertEquals(0L, first.durationMs)
    }

    @Test
    fun `station names lose their decoration`() {
        val names = CatalogJson.radioStations(resource("radio_hardstyle.json")).map { it.name }

        assertTrue(names.none { it.startsWith("*") || it.startsWith("-") || it.startsWith(" ") })
        assertTrue(names.any { it.startsWith("FRENCHCORE") })
    }

    @Test
    fun `audius tracks skip gated ones and play from the stable stream address`() {
        val tracks = CatalogJson.audiusTracks(resource("audius_trending_hardstyle.json")) { id -> "https://api.audius.co/v1/tracks/$id/stream?app_name=AuraCurve" }

        assertEquals(3, tracks.size)
        assertFalse(tracks.any { it.track.title == "Gated" })
        val first = tracks.first()
        assertEquals("Hardstyle", first.genre)
        assertTrue(first.track.durationMs > 0)
        assertTrue(first.track.artist.isNotBlank())
        assertTrue(first.track.streamUrl!!.endsWith("/stream?app_name=AuraCurve"))
        assertTrue(DirectStream.playsDirectly(first.track.id, first.track.streamUrl))
    }

    @Test
    fun `audius stream address is built with the app name`() {
        val client = AudiusClient(appName = "AuraCurve", userAgent = "AuraCurve/test")

        assertEquals("https://api.audius.co/v1/tracks/A8g307/stream?app_name=AuraCurve", client.streamUrl("A8g307"))
    }

    @Test
    fun `all electronic covers every genre of the list`() {
        assertTrue(RadioGenre.ALL.queryTags().containsAll(listOf("hardstyle", "hardcore", "techno", "drum and bass")))
        assertTrue(AudiusGenre.ALL.apiGenres().containsAll(listOf("Electronic", "Hardstyle", "Hard Dance", "Tech House")))
        assertEquals(listOf("hardstyle"), RadioGenre.HARDSTYLE.queryTags())
    }
}
