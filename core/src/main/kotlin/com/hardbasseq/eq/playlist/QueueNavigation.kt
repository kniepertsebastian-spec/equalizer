package com.hardbasseq.eq.playlist

// How the player steps through the queue. ExoPlayer holds the queue and does the stepping
// itself (see AudioPlayerService); what stays here is the rule it is configured with.
object QueueNavigation {
    // Elapsed time after which "previous" restarts the current track instead of
    // going back one, like most players.
    const val RESTART_THRESHOLD_MS = 3_000L
}
