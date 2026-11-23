package com.auracurve.app.player.playback

import java.io.IOException

// A track's stream as the online source hands it out right now. The address expires after a while.
data class ResolvedStream(
    val url: String,
    val isHls: Boolean,
    // Only SoundCloud's ~30 second preview of a Go track, not the full length.
    val isPreview: Boolean,
)

fun interface StreamResolver {
    /**
     * Looks up the stream of [trackId]. Suspends while it waits for the network, so a lookup
     * that is no longer needed (the user skipped on) can be cancelled instead of holding a
     * thread. Throws an IOException when there is no stream; a lost connection keeps its cause
     * (unknown host, timeout ...), which is how the player tells "offline" from "unplayable".
     */
    @Throws(IOException::class)
    suspend fun resolve(trackId: Long): ResolvedStream
}
