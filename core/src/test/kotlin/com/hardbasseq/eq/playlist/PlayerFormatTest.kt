package com.hardbasseq.eq.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerFormatTest {
    @Test
    fun `formats durations`() {
        assertEquals("0:00", PlayerFormat.duration(0))
        assertEquals("0:00", PlayerFormat.duration(-5_000))
        assertEquals("0:09", PlayerFormat.duration(9_400))
        assertEquals("3:07", PlayerFormat.duration(187_000))
        assertEquals("1:02:03", PlayerFormat.duration(3_723_000))
    }

    @Test
    fun `upgrades soundcloud artwork size and leaves other urls alone`() {
        val small = "https://i1.sndcdn.com/artworks-abc-large.jpg"
        assertEquals("https://i1.sndcdn.com/artworks-abc-t500x500.jpg", PlayerFormat.largeArtwork(small))
        assertEquals("https://example.com/cover.png", PlayerFormat.largeArtwork("https://example.com/cover.png"))
        assertNull(PlayerFormat.largeArtwork(null))
    }

    @Test
    fun `progress is clamped and zero for unknown duration`() {
        assertEquals(0f, PlayerFormat.progress(5_000, 0), 0f)
        assertEquals(0.5f, PlayerFormat.progress(50_000, 100_000), 0.0001f)
        assertEquals(1f, PlayerFormat.progress(150_000, 100_000), 0f)
        assertEquals(0f, PlayerFormat.progress(-1, 100_000), 0f)
    }
}
