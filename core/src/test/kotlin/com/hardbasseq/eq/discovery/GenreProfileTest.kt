package com.hardbasseq.eq.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreProfileTest {
    @Test
    fun `too few tracks give no profile`() {
        assertTrue(GenreProfile.derive(List(4) { "Hardcore" to "" }).isEmpty())
    }

    @Test
    fun `recurring genres and tags become the taste, most frequent first`() {
        val tracks =
            List(12) { "Uptempo Hardcore" to "uptempo gabber" } +
                List(6) { "Hardcore" to "terror" } +
                List(2) { "Pop" to "" }

        val profile = GenreProfile.derive(tracks)

        assertTrue("uptempo" in profile && "gabber" in profile && "uptempo hardcore" in profile && "hardcore" in profile)
        // The 12-track styles outrank the 6-track one.
        assertTrue(profile.indexOf("hardcore") > profile.indexOf("uptempo"))
        assertFalse("pop" in profile)
    }

    @Test
    fun `generic words and years are not a taste`() {
        val tracks = List(10) { "Electronic" to "remix \"free download\" 2025 edm" }

        assertTrue(GenreProfile.derive(tracks).isEmpty())
    }

    @Test
    fun `at most six genres`() {
        val tracks = (1..8).flatMap { g -> List(5) { "genre$g" to "" } }

        assertEquals(6, GenreProfile.derive(tracks).size)
    }
}
