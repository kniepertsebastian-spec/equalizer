package com.soundcloud.equalizer.player.soundcloud

import com.soundcloud.equalizer.player.model.TrackItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SoundCloudClientTest {

    @Test
    fun testTrackParsing() {
        val trackJson = JSONObject().apply {
            put("id", 123456L)
            put("title", "Test Track")
            put("duration", 180000L)
            put("artwork_url", "https://example.com/art.jpg")
            put("user", JSONObject().apply {
                put("username", "Test Artist")
            })
        }

        assertEquals(123456L, trackJson.getLong("id"))
        assertEquals("Test Track", trackJson.getString("title"))
        assertEquals("Test Artist", trackJson.getJSONObject("user").getString("username"))
    }
}
