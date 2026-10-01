package com.hardbasseq.eq.integration

import android.content.Context
import android.content.Intent
import com.soundcloud.equalizer.player.auth.SoundCloudLoginActivity
import com.soundcloud.equalizer.player.model.ExternalTrackInfo
import com.soundcloud.equalizer.player.model.LibraryOverview
import com.soundcloud.equalizer.player.model.ResolvedLink
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.playback.LibraryLoader
import com.soundcloud.equalizer.player.playback.LinkResolver
import com.soundcloud.equalizer.player.playback.NowPlaying
import com.soundcloud.equalizer.player.playback.NowPlayingState
import com.soundcloud.equalizer.player.playback.PlaybackQueueState
import com.soundcloud.equalizer.player.service.AudioPlayerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LinkImportResult {
    data class Track(
        val track: TrackItem,
    ) : LinkImportResult

    data class Playlist(
        val title: String,
        val sourceUrl: String,
        val tracks: List<TrackItem>,
    ) : LinkImportResult

    data class Failed(
        val message: String,
    ) : LinkImportResult
}

// Result of loading something from the user's SoundCloud account: the value, or a
// message that can be shown as is.
sealed interface LoadResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : LoadResult<T>

    data class Error(
        val message: String,
    ) : LoadResult<Nothing>
}

// What the full player screen needs from the built-in player: its state, a queue
// to step through, and link resolution. Separate from PlayerBridge (the master
// bar's launch/stop/toggle contract) so that one stays small.
interface PlayerController {
    val nowPlaying: StateFlow<NowPlaying?>
    val queue: StateFlow<List<TrackItem>>

    fun playQueue(
        tracks: List<TrackItem>,
        startIndex: Int,
    )

    fun skipToIndex(index: Int)

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun togglePlayback()

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

    fun openSoundCloudSignIn()

    fun signOutSoundCloud()
}

@Singleton
class AndroidPlayerController
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PlayerController {
        private val linkResolver = LinkResolver(context)
        private val libraryLoader = LibraryLoader(context)

        override val nowPlaying: StateFlow<NowPlaying?> = NowPlayingState.current
        override val queue: StateFlow<List<TrackItem>> = PlaybackQueueState.queue

        override fun playQueue(
            tracks: List<TrackItem>,
            startIndex: Int,
        ) {
            if (tracks.isEmpty()) return
            PlaybackQueueState.setQueue(tracks)
            skipToIndex(startIndex.coerceIn(0, tracks.lastIndex))
        }

        override fun skipToIndex(index: Int) =
            send(AudioPlayerService.ACTION_PLAY_INDEX) { putExtra(AudioPlayerService.EXTRA_QUEUE_INDEX, index) }

        override fun next() = send(AudioPlayerService.ACTION_NEXT)

        override fun previous() = send(AudioPlayerService.ACTION_PREVIOUS)

        override fun seekTo(positionMs: Long) =
            send(AudioPlayerService.ACTION_SEEK_TO) {
                putExtra(AudioPlayerService.EXTRA_POSITION_MS, positionMs)
            }

        override fun togglePlayback() = send(AudioPlayerService.ACTION_TOGGLE_PLAYBACK)

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
                LinkImportResult.Failed("Link konnte nicht geladen werden: ${e.message ?: "unbekannter Fehler"}")
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

        override suspend fun loadPlaylistTracks(playlistId: Long): LoadResult<List<TrackItem>> =
            load { libraryLoader.playlistTracks(playlistId) }

        private suspend fun <T> load(block: suspend () -> T): LoadResult<T> =
            try {
                LoadResult.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error("Das hat nicht geklappt: ${e.message ?: "unbekannter Fehler"}")
            }

        override fun openSoundCloudSignIn() {
            val intent =
                Intent(context, SoundCloudLoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }

        override fun signOutSoundCloud() = SoundCloudLoginActivity.clearToken(context)

        private fun send(
            action: String,
            configure: Intent.() -> Unit = {},
        ) {
            val intent =
                Intent(context, AudioPlayerService::class.java).apply {
                    this.action = action
                    configure()
                }
            context.startService(intent)
        }
    }
