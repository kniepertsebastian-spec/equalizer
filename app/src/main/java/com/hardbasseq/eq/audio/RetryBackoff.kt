package com.hardbasseq.eq.audio

// docs/STATE_MACHINE.md §4: exponential, capped backoff - 2s, 4s, 8s, 16s, 30s.
// No jitter needed (no shared service many client instances retry against).
private val RETRY_INTERVALS_MS = longArrayOf(2_000, 4_000, 8_000, 16_000, 30_000)

object RetryBackoff {
    // attempt is 1-based (the first retry is attempt 1). Delay is capped at the
    // last interval for any attempt beyond the table's length, though callers
    // should never exceed MAX_RETRY_ATTEMPTS in practice.
    fun delayMillisFor(attempt: Int): Long {
        val index = (attempt - 1).coerceIn(RETRY_INTERVALS_MS.indices)
        return RETRY_INTERVALS_MS[index]
    }
}
