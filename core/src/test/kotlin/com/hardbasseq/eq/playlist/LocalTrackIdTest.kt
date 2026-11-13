package com.hardbasseq.eq.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTrackIdTest {
    @Test
    fun idsAreNegativeAndStable() {
        val uri = "content://com.android.providers.media.documents/document/audio%3A42"

        assertTrue(LocalTrackId.of(uri) < 0L)
        assertEquals(LocalTrackId.of(uri), LocalTrackId.of(uri))
        assertTrue(LocalTrackId.isLocal(LocalTrackId.of(uri)))
    }

    @Test
    fun differentFilesGetDifferentIds() {
        assertNotEquals(LocalTrackId.of("content://x/1"), LocalTrackId.of("content://x/2"))
    }

    @Test
    fun soundCloudIdsAreNeverLocal() {
        assertFalse(LocalTrackId.isLocal(1_234_567_890L))
        assertFalse(LocalTrackId.isLocal(0L))
    }

    @Test
    fun idsRoundTripThroughTheQueueMediaId() {
        val id = LocalTrackId.of("content://x/1")

        assertEquals(id, QueueMediaId.trackId(QueueMediaId.of(id)))
    }

    @Test
    fun onlyDeviceAddressesCountAsLocal() {
        assertTrue(LocalTrackId.isLocalAddress("content://media/external/audio/1"))
        assertTrue(LocalTrackId.isLocalAddress("file:///sdcard/Music/a.mp3"))
        assertFalse(LocalTrackId.isLocalAddress("https://cf-media.sndcdn.com/x.mp3"))
        assertFalse(LocalTrackId.isLocalAddress("hardbasseq-sc://track/5"))
        assertFalse(LocalTrackId.isLocalAddress(null))
    }
}
