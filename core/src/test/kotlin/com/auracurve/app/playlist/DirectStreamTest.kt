package com.auracurve.app.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectStreamTest {
    @Test
    fun `catalog ids are negative stable and differ by source`() {
        val radio = DirectStream.idOf("radio", "77330159-8780-443b-b4dd-bcb519b49011")
        assertTrue(radio < 0)
        assertEquals(radio, DirectStream.idOf("radio", "77330159-8780-443b-b4dd-bcb519b49011"))
        assertNotEquals(radio, DirectStream.idOf("audius", "77330159-8780-443b-b4dd-bcb519b49011"))
    }

    @Test
    fun `only files and https addresses of own ids play directly`() {
        val own = DirectStream.idOf("audius", "A8g307")
        assertTrue(DirectStream.playsDirectly(own, "https://api.audius.co/v1/tracks/A8g307/stream?app_name=AuraCurve"))
        assertTrue(DirectStream.playsDirectly(LocalTrackId.of("content://x"), "content://x"))
        assertFalse(DirectStream.playsDirectly(own, "http://stream.example.com/live"))
        assertFalse(DirectStream.playsDirectly(own, null))
        // A SoundCloud track (positive id) is always looked up, even if it carries an address.
        assertFalse(DirectStream.playsDirectly(42L, "https://cf-media.sndcdn.com/abc.mp3"))
    }
}
