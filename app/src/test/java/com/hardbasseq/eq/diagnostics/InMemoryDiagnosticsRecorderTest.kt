package com.hardbasseq.eq.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryDiagnosticsRecorderTest {
    @Test
    fun `record appends timestamped events in order`() {
        val recorder = InMemoryDiagnosticsRecorder()

        recorder.record("first")
        recorder.record("second")

        val events = recorder.events.value
        assertEquals(listOf("first", "second"), events.map { it.message })
        assertTrue(events.all { it.timestampMillis > 0 })
    }

    @Test
    fun `record keeps only the most recent 50 events`() {
        val recorder = InMemoryDiagnosticsRecorder()

        repeat(60) { recorder.record("event $it") }

        val events = recorder.events.value
        assertEquals(50, events.size)
        assertEquals("event 10", events.first().message)
        assertEquals("event 59", events.last().message)
    }
}
