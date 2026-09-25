package com.hardbasseq.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryBackoffTest {
    @Test
    fun delayMillisFor_followsTheSpecifiedExponentialSchedule() {
        assertEquals(2_000L, RetryBackoff.delayMillisFor(1))
        assertEquals(4_000L, RetryBackoff.delayMillisFor(2))
        assertEquals(8_000L, RetryBackoff.delayMillisFor(3))
        assertEquals(16_000L, RetryBackoff.delayMillisFor(4))
        assertEquals(30_000L, RetryBackoff.delayMillisFor(5))
    }

    @Test
    fun delayMillisFor_capsAtTheLastIntervalBeyondMaxAttempts() {
        assertEquals(30_000L, RetryBackoff.delayMillisFor(6))
        assertEquals(30_000L, RetryBackoff.delayMillisFor(100))
    }

    @Test
    fun delayMillisFor_neverGrowsUnboundedAcrossAttemptSequence() {
        val delays = (1..MAX_RETRY_ATTEMPTS).map { RetryBackoff.delayMillisFor(it) }
        assertEquals(delays, delays.sorted())
        assertEquals(30_000L, delays.max())
    }
}
