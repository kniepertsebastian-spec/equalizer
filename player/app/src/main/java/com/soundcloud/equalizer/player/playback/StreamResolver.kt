package com.soundcloud.equalizer.player.playback

import android.content.Context
import com.soundcloud.equalizer.player.auth.SoundCloudLoginActivity
import com.soundcloud.equalizer.player.soundcloud.SoundCloudClient
import kotlinx.coroutines.runBlocking
import java.io.IOException

// A track's stream as SoundCloud hands it out right now. The address expires after a while.
data class ResolvedStream(
    val url: String,
    val isHls: Boolean,
    // Only SoundCloud's ~30 second preview of a Go track, not the full length.
    val isPreview: Boolean,
)

fun interface StreamResolver {
    /**
     * Looks up the stream of [trackId]. Blocks on the network, so it runs on a thread of its
     * own. Throws an IOException when there is no stream; a lost connection keeps its cause
     * (unknown host, timeout ...), which is how the player tells "offline" from "unplayable".
     */
    @Throws(IOException::class)
    fun resolve(trackId: Long): ResolvedStream
}

// Asks SoundCloud with the signed-in account (if any): with a Go subscription that is what
// makes the full-length streams appear at all.
class SoundCloudStreamResolver(
    private val context: Context,
    private val client: SoundCloudClient = SoundCloudClient(),
) : StreamResolver {
    override fun resolve(trackId: Long): ResolvedStream {
        client.setUserAuthToken(SoundCloudLoginActivity.getSavedToken(context))
        val track = runBlocking { client.getTrack(trackId) } ?: throw IOException("SoundCloud knows no track $trackId")
        val url = track.streamUrl ?: throw IOException("SoundCloud offers no stream for track $trackId")
        return ResolvedStream(url = url, isHls = track.isHlsStream, isPreview = track.isPreview)
    }
}
