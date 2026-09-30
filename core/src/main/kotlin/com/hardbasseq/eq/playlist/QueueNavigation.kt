package com.hardbasseq.eq.playlist

// Index arithmetic for the player queue, kept free of Android/ExoPlayer so the
// edge cases (first/last track, empty queue, stale index) are unit-testable.
object QueueNavigation {
    // Elapsed time after which "previous" restarts the current track instead of
    // going back one, like most players.
    const val RESTART_THRESHOLD_MS = 3_000L

    // null = end of the queue (playback should stop).
    fun next(
        current: Int,
        size: Int,
    ): Int? = if (size <= 0 || current + 1 >= size) null else (current + 1).coerceAtLeast(0)

    // null = restart the current track rather than move.
    fun previous(
        current: Int,
        positionMs: Long,
    ): Int? = if (current <= 0 || positionMs > RESTART_THRESHOLD_MS) null else current - 1

    fun isValid(
        index: Int,
        size: Int,
    ): Boolean = index in 0 until size
}
