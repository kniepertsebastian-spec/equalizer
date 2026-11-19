package com.auracurve.app.integration

import com.auracurve.app.player.playback.QueueRequest
import com.auracurve.app.player.playback.QueueRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerServiceStartTest {
    @Test
    fun `a start Android allows goes through`() {
        var started = 0
        assertTrue(PlayerServiceStart.start(start = { started++ }, onRefused = { throw AssertionError("not refused") }))
        assertEquals(1, started)
    }

    @Test
    fun `a start refused in the background is dropped instead of crashing`() {
        // What Android 8+ throws for startService from the background
        // (BackgroundServiceStartNotAllowedException extends IllegalStateException).
        val refusal = IllegalStateException("Not allowed to start service Intent: app is in background")
        var reported: IllegalStateException? = null
        assertFalse(PlayerServiceStart.start(start = { throw refusal }, onRefused = { reported = it }))
        assertSame(refusal, reported)
    }

    @Test
    fun `a withdrawn queue request is not applied later`() {
        val dropped = QueueRequest.Append(emptyList())
        val later = QueueRequest.PlayNext(emptyList())
        QueueRequests.submit(dropped)
        QueueRequests.withdraw(dropped)
        QueueRequests.submit(later)
        assertSame(later, QueueRequests.take())
        assertNull(QueueRequests.take())
    }
}
