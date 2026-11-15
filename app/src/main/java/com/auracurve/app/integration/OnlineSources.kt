package com.auracurve.app.integration

import com.auracurve.app.player.model.ExternalTrackInfo
import com.auracurve.app.player.model.LibraryOverview
import com.auracurve.app.player.model.TrackItem

// The online services behind the player: link import, SoundCloud (search, account, library) and
// Spotify. Which implementation is built in depends on the flavor (AuraCurve roadmap 02, 1.3):
// "full" has SoundCloudOnlineSources, "store" has NoOnlineSources, which offers none of them.
interface OnlineSources {
    suspend fun resolveLink(url: String): LinkImportResult

    // The SoundCloud account the player streams with. Signed in, SoundCloud serves
    // a Go subscription's full-length tracks (and no ads); signed out, Go tracks
    // are only previews.
    fun isSoundCloudSignedIn(): Boolean

    // Title and channel of the song behind a YouTube / Spotify link (public preview data).
    suspend fun describeExternalLink(url: String): LoadResult<ExternalTrackInfo>

    // The public page of a Spotify playlist (HTML); SpotifyPlaylistPage reads the tracks from it.
    suspend fun fetchSpotifyPlaylistPage(playlistId: String): LoadResult<String>

    // The public page of a single Spotify track (HTML); SpotifyTrackPage reads the artist from it.
    suspend fun fetchSpotifyTrackPage(trackId: String): LoadResult<String>

    // Plain SoundCloud search, without resolving streams.
    suspend fun searchSoundCloud(
        query: String,
        limit: Int,
    ): LoadResult<List<TrackItem>>

    // The signed-in user's library. Each call asks SoundCloud again.
    suspend fun loadLibrary(): LoadResult<LibraryOverview>

    suspend fun loadLikedTracks(): LoadResult<List<TrackItem>>

    suspend fun loadPlaylistTracks(playlistId: Long): LoadResult<List<TrackItem>>

    // Writes a playlist into the signed-in SoundCloud account (private); with [existingId] that one
    // is updated. Returns the SoundCloud playlist id.
    suspend fun pushPlaylistToSoundCloud(
        title: String,
        trackIds: List<Long>,
        existingId: Long?,
    ): LoadResult<Long>

    // Spotify sign-in with the user's own client id (see SpotifyApiClient): reads all songs of
    // the user's own playlists, which the public page cannot.
    fun spotifyClientId(): String

    fun saveSpotifyClientId(clientId: String)

    fun isSpotifySignedIn(): Boolean

    // Opens the Spotify sign-in in the browser; returns what is wrong (no client id saved) or null.
    fun openSpotifySignIn(): String?

    fun signOutSpotify()

    fun openSoundCloudSignIn()

    fun signOutSoundCloud()
}
