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
}
