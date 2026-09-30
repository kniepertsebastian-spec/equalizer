package com.soundcloud.equalizer.player.playback

import android.content.Context
import com.soundcloud.equalizer.player.auth.SoundCloudLoginActivity
import com.soundcloud.equalizer.player.model.ResolvedLink
import com.soundcloud.equalizer.player.soundcloud.SoundCloudClient

// Entry point for turning a shared SoundCloud link into tracks, for callers outside
// this module. Keeps SoundCloudClient (and the OkHttp types in its constructor)
// inside :player, so :app only ever sees ResolvedLink.
class LinkResolver(
    private val context: Context,
) {
    private val client = SoundCloudClient()

    suspend fun resolve(url: String): ResolvedLink {
        SoundCloudLoginActivity.getSavedToken(context)?.let { client.setUserAuthToken(it) }
        return client.resolveLink(url)
    }
}
