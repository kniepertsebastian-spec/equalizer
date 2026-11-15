package com.auracurve.app.player.playback

import android.content.Context
import com.auracurve.app.player.auth.SoundCloudLoginActivity
import com.auracurve.app.player.model.LibraryOverview
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.player.soundcloud.SoundCloudClient

// The signed-in user's SoundCloud library (playlists, likes) for callers outside this
// module - same reason as LinkResolver: keeps SoundCloudClient and its OkHttp types
// inside :player. Every call picks up the current token, so signing in or out in
// between takes effect immediately.
class LibraryLoader(
    private val context: Context,
) {
    private val client = SoundCloudClient()

    private suspend fun <T> withAccount(block: suspend (SoundCloudClient) -> T): T {
        client.setUserAuthToken(SoundCloudLoginActivity.getSavedToken(context))
        return block(client)
    }

    suspend fun overview(): LibraryOverview = withAccount { it.getLibraryOverview() }

    suspend fun likedTracks(): List<TrackItem> = withAccount { it.getMyLikedTracks() }

    suspend fun playlistTracks(playlistId: Long): List<TrackItem> = withAccount { it.getPlaylistTracks(playlistId) }

    // Sends a playlist to the user's account; returns its SoundCloud id (see SoundCloudClient.pushPlaylist).
    suspend fun pushPlaylist(
        title: String,
        trackIds: List<Long>,
        existingId: Long?,
    ): Long = withAccount { it.pushPlaylist(title, trackIds, existingId) }
}
