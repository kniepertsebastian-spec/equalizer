package com.auracurve.app.integration

import com.auracurve.app.link.SpotifyPlaylist

// Flavor "full": all songs of the signed-in user's own Spotify playlist through Spotify's Web API
// (the public page stops at 100). Not part of OnlineSources, whose types the store flavor shares.
interface SpotifyPlaylistApi {
    suspend fun fetchSpotifyPlaylistViaApi(playlistId: String): LoadResult<SpotifyPlaylist>
}
