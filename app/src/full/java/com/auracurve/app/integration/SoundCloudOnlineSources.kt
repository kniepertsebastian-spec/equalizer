package com.auracurve.app.integration

import android.content.Context
import android.content.Intent
import com.auracurve.app.R
import com.auracurve.app.link.SpotifyPlaylist
import com.auracurve.app.player.auth.SoundCloudLoginActivity
import com.auracurve.app.player.model.ExternalTrackInfo
import com.auracurve.app.player.model.LibraryOverview
import com.auracurve.app.player.model.ResolvedLink
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.player.playback.LibraryLoader
import com.auracurve.app.player.playback.LinkResolver
import com.auracurve.app.player.spotify.SpotifyApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

// Flavor "full": SoundCloud through its website's interface, Spotify and shared links.
@Singleton
class SoundCloudOnlineSources
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : OnlineSources {
        private val linkResolver = LinkResolver(context)
        private val libraryLoader = LibraryLoader(context)
        private val spotifyApi = SpotifyApiClient.get(context)

        override suspend fun resolveLink(url: String): LinkImportResult =
            try {
                when (val resolved = linkResolver.resolve(url)) {
                    is ResolvedLink.SingleTrack -> LinkImportResult.Track(resolved.track)
                    is ResolvedLink.Playlist -> LinkImportResult.Playlist(resolved.title, resolved.sourceUrl, resolved.tracks)
                    is ResolvedLink.Unsupported -> LinkImportResult.Failed(resolved.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LinkImportResult.Failed(
                    context.getString(R.string.link_load_failed, e.message ?: context.getString(R.string.unknown_error)),
                )
            }

        override fun isSoundCloudSignedIn(): Boolean = SoundCloudLoginActivity.getSavedToken(context) != null

        override suspend fun describeExternalLink(url: String): LoadResult<ExternalTrackInfo> = load { linkResolver.describeExternal(url) }

        override suspend fun fetchSpotifyTrackPage(trackId: String): LoadResult<String> =
            load { linkResolver.fetchSpotifyTrackPage(trackId) }

        override suspend fun fetchSpotifyPlaylistPage(playlistId: String): LoadResult<String> =
            load { linkResolver.fetchSpotifyPlaylistPage(playlistId) }

        override suspend fun searchSoundCloud(
            query: String,
            limit: Int,
        ): LoadResult<List<TrackItem>> = load { linkResolver.searchTracks(query, limit) }

        override suspend fun loadLibrary(): LoadResult<LibraryOverview> = load { libraryLoader.overview() }

        override suspend fun loadLikedTracks(): LoadResult<List<TrackItem>> = load { libraryLoader.likedTracks() }

        override suspend fun pushPlaylistToSoundCloud(
            title: String,
            trackIds: List<Long>,
            existingId: Long?,
        ): LoadResult<Long> = load { libraryLoader.pushPlaylist(title, trackIds, existingId) }

        override suspend fun loadPlaylistTracks(playlistId: Long): LoadResult<List<TrackItem>> =
            load { libraryLoader.playlistTracks(playlistId) }

        private suspend fun <T> load(block: suspend () -> T): LoadResult<T> =
            try {
                LoadResult.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error(context.getString(R.string.generic_failed, e.message ?: context.getString(R.string.unknown_error)))
            }

        override fun openSoundCloudSignIn() {
            val intent =
                Intent(context, SoundCloudLoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }

        override fun signOutSoundCloud() = SoundCloudLoginActivity.clearToken(context)

        override fun spotifyClientId(): String = spotifyApi.clientId

        override fun saveSpotifyClientId(clientId: String) = spotifyApi.saveClientId(clientId)

        override fun isSpotifySignedIn(): Boolean = spotifyApi.isSignedIn()

        override fun openSpotifySignIn(): String? {
            val intent = spotifyApi.loginIntent() ?: return context.getString(R.string.spotify_enter_client_id)
            return try {
                context.startActivity(intent)
                null
            } catch (e: android.content.ActivityNotFoundException) {
                context.getString(R.string.spotify_no_browser)
            }
        }

        override fun signOutSpotify() = spotifyApi.signOut()

        override suspend fun fetchSpotifyPlaylistViaApi(playlistId: String): LoadResult<SpotifyPlaylist> =
            try {
                LoadResult.Ok(spotifyApi.readPlaylist(playlistId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error(e.message ?: context.getString(R.string.spotify_no_answer))
            }
    }
