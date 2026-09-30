package com.hardbasseq.eq.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackQueryTest {
    @Test
    fun `splits artist and title and drops decoration`() {
        val q = TrackQueryBuilder.fromYouTube("Katy Perry - Roar (Official Video)", "KatyPerryVEVO")
        assertEquals("Katy Perry", q.artist)
        assertEquals("Roar", q.title)
        assertEquals("Katy Perry Roar", q.searchText)
        assertEquals("Katy Perry - Roar", q.label)
    }

    @Test
    fun `drops several noise groups but keeps version markers`() {
        val q = TrackQueryBuilder.fromYouTube("Angerfist - Criminally Insane [HD] (Miss K8 Remix) [Free Download]", null)
        assertEquals("Angerfist", q.artist)
        assertEquals("Criminally Insane (Miss K8 Remix)", q.title)
    }

    @Test
    fun `handles en dashes`() {
        val q = TrackQueryBuilder.fromYouTube("Artist – Song Name (Lyrics)", null)
        assertEquals("Artist", q.artist)
        assertEquals("Song Name", q.title)
    }

    @Test
    fun `uses the channel as artist when the title has none and strips topic suffixes`() {
        val q = TrackQueryBuilder.fromYouTube("Roar", "Katy Perry - Topic")
        assertEquals("Katy Perry", q.artist)
        assertEquals("Roar", q.title)
    }

    @Test
    fun `pipe separated titles read title first`() {
        val q = TrackQueryBuilder.fromYouTube("Roar | Katy Perry", null)
        assertEquals("Katy Perry", q.artist)
        assertEquals("Roar", q.title)
    }

    @Test
    fun `a title with no artist anywhere stays title only`() {
        val q = TrackQueryBuilder.fromYouTube("Some Unreleased Banger", null)
        assertNull(q.artist)
        assertEquals("Some Unreleased Banger", q.title)
    }

    @Test
    fun `spotify titles have no artist`() {
        val q = TrackQueryBuilder.fromTitleOnly("Roar (Official Audio)")
        assertNull(q.artist)
        assertEquals("Roar", q.title)
    }

    @Test
    fun `an exact match scores higher than an unrelated or longer result`() {
        val query = TrackQuery("Katy Perry", "Roar")
        val exact = TrackMatcher.score(query, "Katy Perry - Roar", "KatyPerryVEVO")
        val mix = TrackMatcher.score(query, "Top 100 Pop Hits 2013 full mix Roar Royals Wrecking Ball Blank Space", "PopMix")
        val unrelated = TrackMatcher.score(query, "Completely different song", "Someone")
        assertTrue("exact=$exact", exact > 0.8)
        assertTrue("exact=$exact mix=$mix", exact > mix)
        assertTrue(unrelated < 0.2)
    }

    @Test
    fun `an artist may match through the uploader name`() {
        val query = TrackQuery("Angerfist", "Criminally Insane")
        val viaUploader = TrackMatcher.score(query, "Criminally Insane", "Angerfist")
        val wrongUploader = TrackMatcher.score(query, "Criminally Insane", "Random Uploader")
        assertTrue(viaUploader > wrongUploader)
    }

    @Test
    fun `accents and case do not matter`() {
        val query = TrackQuery("Beyoncé", "Halo")
        assertTrue(TrackMatcher.score(query, "BEYONCE - halo", "x") > 0.7)
    }

    @Test
    fun `rank orders best first and confidence needs an artist`() {
        val query = TrackQuery("Katy Perry", "Roar")
        val ranked = TrackMatcher.rank(query, listOf("Other Song", "Katy Perry - Roar"), { it }, { "" })
        assertEquals("Katy Perry - Roar", ranked.first().first)
        assertTrue(TrackMatcher.isConfident(query, ranked.first().second))
        assertFalse(TrackMatcher.isConfident(TrackQuery(null, "Roar"), 1.0))
        assertFalse(TrackMatcher.isConfident(query, 0.5))
    }

    @Test
    fun `no title tokens means no match`() {
        assertEquals(0.0, TrackMatcher.score(TrackQuery(null, "the"), "anything", "x"), 0.0)
    }
}
