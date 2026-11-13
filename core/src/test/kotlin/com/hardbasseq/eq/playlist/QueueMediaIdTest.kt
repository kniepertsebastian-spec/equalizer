package com.hardbasseq.eq.playlist

import com.hardbasseq.eq.auto.AutoMediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueMediaIdTest {
    @Test
    fun mediaId_roundTrips() {
        assertEquals(123456789L, QueueMediaId.trackId(QueueMediaId.of(123456789L)))
    }

    @Test
    fun streamUri_roundTrips() {
        val uri = QueueMediaId.streamUri(42L)

        assertEquals("hardbasseq-sc://track/42", uri)
        assertEquals(42L, QueueMediaId.trackIdFromStreamUri(uri))
    }

    @Test
    fun otherIds_areNotQueueIds() {
        assertNull(QueueMediaId.trackId(""))
        assertNull(QueueMediaId.trackId("42"))
        assertNull(QueueMediaId.trackId("queue|"))
        assertNull(QueueMediaId.trackId("queue|abc"))
        // The car's ids must never read as queue entries (and the other way round).
        assertNull(QueueMediaId.trackId(AutoMediaId.likedTrack(42L)))
        assertNull(AutoMediaId.parse(QueueMediaId.of(42L)))
    }

    @Test
    fun realAddresses_areNotPlaceholders() {
        assertNull(QueueMediaId.trackIdFromStreamUri("https://cf-media.sndcdn.com/abc.128.mp3"))
        assertNull(QueueMediaId.trackIdFromStreamUri("hardbasseq-sc://track/"))
        assertNull(QueueMediaId.trackIdFromStreamUri("hardbasseq-sc://playlist/42"))
    }
}
