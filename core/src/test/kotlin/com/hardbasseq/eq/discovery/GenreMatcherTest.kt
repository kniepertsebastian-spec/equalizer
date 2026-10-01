package com.hardbasseq.eq.discovery

import com.hardbasseq.eq.discovery.GenreMatcher.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreMatcherTest {
    private fun verdict(
        genre: String,
        tags: String,
        vararg wanted: String,
    ) = GenreMatcher.verdict(genre, tags, wanted.toList())

    @Test
    fun `normalizes case accents and punctuation`() {
        assertEquals("beyonce halo", GenreMatcher.normalize("  BEYONCÉ - Halo!! "))
    }

    @Test
    fun `parses quoted and plain tags`() {
        assertEquals(listOf("hardcore", "uptempo hardcore", "gabber"), GenreMatcher.parseTags("hardcore \"uptempo hardcore\" gabber"))
        assertEquals(emptyList<String>(), GenreMatcher.parseTags(""))
    }

    @Test
    fun `no wanted genres means no filtering`() {
        assertEquals(Verdict.MATCH, verdict("Schlager", ""))
    }

    @Test
    fun `a schlager track does not fit a hardcore wish but an uptempo hardcore one does`() {
        assertEquals(Verdict.MISMATCH, verdict("Schlager", "deutsch party", "uptempo hardcore"))
        assertEquals(Verdict.MATCH, verdict("Uptempo Hardcore", "", "uptempo hardcore"))
    }

    @Test
    fun `scene siblings count as the same family`() {
        assertEquals(Verdict.MATCH, verdict("Gabber", "", "uptempo"))
        assertEquals(Verdict.MATCH, verdict("", "terrorcore \"industrial hardcore\"", "hardcore"))
        assertEquals(Verdict.MATCH, verdict("Frenchcore", "", "Uptempo"))
        assertEquals(Verdict.MATCH, verdict("Rawstyle", "", "hardstyle"))
        assertEquals(Verdict.MISMATCH, verdict("Hardstyle", "", "uptempo"))
        assertEquals(Verdict.MATCH, verdict("Neurofunk", "", "dnb"))
    }

    @Test
    fun `hardcore punk and hip hop do not count for the hard dance scene`() {
        assertEquals(Verdict.MISMATCH, verdict("Hardcore Punk", "", "hardcore"))
        assertEquals(Verdict.MISMATCH, verdict("Hip Hop", "hardcore rap", "uptempo"))
        // But a real tag next to a conflicting one still matches.
        assertEquals(Verdict.MATCH, verdict("Hip Hop", "uptempo hardcore", "uptempo"))
    }

    @Test
    fun `matching is by whole words`() {
        assertEquals(Verdict.MISMATCH, verdict("Terrorist Pop", "", "terror"))
        assertEquals(Verdict.MATCH, verdict("Terror", "", "terror"))
    }

    @Test
    fun `an upload without genre or tags is unknown, not a mismatch`() {
        assertEquals(Verdict.UNKNOWN, verdict("", "", "hardcore"))
        assertEquals(Verdict.UNKNOWN, verdict("   ", "  ", "hardcore"))
    }

    @Test
    fun `non scene keywords match literally`() {
        assertEquals(Verdict.MATCH, verdict("Deep House", "", "house"))
        assertEquals(Verdict.MISMATCH, verdict("Techno", "", "house"))
        assertTrue(GenreMatcher.expand("   ").isEmpty())
    }
}
