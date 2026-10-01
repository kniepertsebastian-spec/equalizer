package com.hardbasseq.eq.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkTest {
    @Test
    fun `extracts the url from a shared sentence and strips trailing punctuation`() {
        assertEquals(
            "https://soundcloud.com/artist/track",
            ShareLink.extractUrl("Check this out: https://soundcloud.com/artist/track."),
        )
        assertEquals(
            "https://on.soundcloud.com/abc123",
            ShareLink.extractUrl("Hör mal rein (https://on.soundcloud.com/abc123)"),
        )
    }

    @Test
    fun `returns null when there is no usable url`() {
        assertNull(ShareLink.extractUrl("no link here"))
        assertNull(ShareLink.extractUrl(""))
        assertNull(ShareLink.extractUrl("http://localhost/x"))
    }

    @Test
    fun `classifies the services`() {
        assertEquals(LinkSource.SOUNDCLOUD, ShareLink.classify("https://soundcloud.com/a/b"))
        assertEquals(LinkSource.SOUNDCLOUD, ShareLink.classify("https://m.soundcloud.com/a/b"))
        assertEquals(LinkSource.SOUNDCLOUD, ShareLink.classify("https://on.soundcloud.com/xyz"))
        assertEquals(LinkSource.SPOTIFY, ShareLink.classify("https://open.spotify.com/playlist/37i9dQ"))
        assertEquals(LinkSource.SPOTIFY, ShareLink.classify("https://spotify.link/abc"))
        assertEquals(LinkSource.YOUTUBE, ShareLink.classify("https://music.youtube.com/playlist?list=PL1"))
        assertEquals(LinkSource.YOUTUBE, ShareLink.classify("https://youtu.be/abc"))
        assertEquals(LinkSource.OTHER, ShareLink.classify("https://example.com/soundcloud.com"))
    }

    @Test
    fun `lookalike hosts are not treated as a known service`() {
        assertEquals(LinkSource.OTHER, ShareLink.classify("https://notsoundcloud.com/a"))
        assertEquals(LinkSource.OTHER, ShareLink.classify("https://soundcloud.com.evil.example/a"))
        assertEquals(LinkSource.OTHER, ShareLink.classify("https://evil.example/?u=https://soundcloud.com/a"))
    }

    @Test
    fun `short links are recognized`() {
        assertTrue(ShareLink.isSoundCloudShortLink("https://on.soundcloud.com/abc"))
        assertFalse(ShareLink.isSoundCloudShortLink("https://soundcloud.com/a/b"))
    }

    @Test
    fun `normalizes mobile host, query and fragment but keeps private playlist secrets`() {
        assertEquals(
            "https://soundcloud.com/artist/sets/mix",
            ShareLink.normalizeSoundCloud("https://m.soundcloud.com/artist/sets/mix?si=abc&utm_source=clipboard#t=1"),
        )
        assertEquals(
            "https://soundcloud.com/artist/sets/mix/s-Ab12Cd",
            ShareLink.normalizeSoundCloud("https://www.soundcloud.com/artist/sets/mix/s-Ab12Cd/"),
        )
    }

    @Test
    fun `finds the video behind youtube links in all their forms`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://youtu.be/$id?si=abc",
            "https://music.youtube.com/watch?v=$id&list=RDAMVM$id",
            "https://m.youtube.com/watch?feature=share&v=$id",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
        ).forEach { url -> assertEquals(url, id, ShareLink.youtubeVideoId(url)) }
        assertEquals("https://www.youtube.com/watch?v=$id", ShareLink.canonicalYouTubeUrl(id))
    }

    @Test
    fun `playlists channels and lookalikes have no video id`() {
        assertNull(ShareLink.youtubeVideoId("https://www.youtube.com/playlist?list=PL123"))
        assertNull(ShareLink.youtubeVideoId("https://www.youtube.com/@artist"))
        assertNull(ShareLink.youtubeVideoId("https://www.youtube.com/watch?v=short"))
        assertNull(ShareLink.youtubeVideoId("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(ShareLink.youtubeVideoId("https://soundcloud.com/a/b"))
    }

    @Test
    fun `finds the spotify track and refuses other spotify links`() {
        val id = "4cOdK2wGLETKBW3PvgPWqT"
        assertEquals(id, ShareLink.spotifyTrackId("https://open.spotify.com/track/$id?si=abc"))
        assertEquals(id, ShareLink.spotifyTrackId("https://open.spotify.com/intl-de/track/$id"))
        assertEquals("https://open.spotify.com/track/$id", ShareLink.canonicalSpotifyTrackUrl(id))
        assertNull(ShareLink.spotifyTrackId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(ShareLink.spotifyTrackId("https://open.spotify.com/album/$id"))
        assertNull(ShareLink.spotifyTrackId("https://spotify.link/abc"))
    }
}
