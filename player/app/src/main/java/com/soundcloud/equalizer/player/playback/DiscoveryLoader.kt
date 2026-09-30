package com.soundcloud.equalizer.player.playback

import android.content.Context
import com.soundcloud.equalizer.player.auth.SoundCloudLoginActivity
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.soundcloud.SoundCloudClient

// New uploads mentioning an artist, for the weekly "Interesting new uploads" list -
// found by searching SoundCloud for the name (not by following the artist), so
// re-uploads by other accounts count too. Facade like LinkResolver: keeps the client
// and its OkHttp types inside :player.
class DiscoveryLoader(
    private val context: Context,
) {
    private val client = SoundCloudClient()

    suspend fun recentUploads(artist: String): List<TrackItem> {
        client.setUserAuthToken(SoundCloudLoginActivity.getSavedToken(context))
        return client.searchTracks(artist, limit = SEARCH_LIMIT, resolveStreams = false, recentOnly = true)
    }

    private companion object {
        const val SEARCH_LIMIT = 50
    }
}
