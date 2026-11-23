package com.auracurve.app.player.playback

import android.content.Context
import com.auracurve.app.player.auth.SoundCloudLoginActivity
import com.auracurve.app.player.soundcloud.SoundCloudClient
import java.io.IOException

// Asks SoundCloud with the signed-in account (if any): with a Go subscription that is what
// makes the full-length streams appear at all. Suspends on the client's own IO dispatcher: no
// thread is blocked on the lookup, and a cancelled lookup stops at its next suspension point.
class SoundCloudStreamResolver(
    private val context: Context,
    private val client: SoundCloudClient = SoundCloudClient(),
) : StreamResolver {
    override suspend fun resolve(trackId: Long): ResolvedStream {
        client.setUserAuthToken(SoundCloudLoginActivity.getSavedToken(context))
        val track = client.getTrack(trackId) ?: throw IOException("SoundCloud knows no track $trackId")
        val url = track.streamUrl ?: throw IOException("SoundCloud offers no stream for track $trackId")
        return ResolvedStream(url = url, isHls = track.isHlsStream, isPreview = track.isPreview)
    }
}
