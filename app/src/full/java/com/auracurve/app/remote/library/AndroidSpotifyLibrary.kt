package com.auracurve.app.remote.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.auracurve.app.link.LibraryTrack
import com.auracurve.app.link.PlaylistInfo
import com.auracurve.app.link.SpotifyLibrary
import com.auracurve.app.link.SpotifyLibraryJson
import com.auracurve.app.player.spotify.SpotifyApiClient
import com.auracurve.app.remote.playback.NowPlayingSnapshot
import com.auracurve.app.remote.playback.SpotifyPlaybackBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The Web API sign-in the player's "Linked services" screen already sets up. */
class ApiClientLibraryReader(
    private val client: SpotifyApiClient,
) : SpotifyLibraryReader {
    override fun isSignedIn(): Boolean = client.isSignedIn()

    override suspend fun playlists(): List<PlaylistInfo> = client.readMyPlaylists()

    override suspend fun tracks(playlistId: String): List<LibraryTrack>? = client.readPlaylistTracks(playlistId)
}

/** One JSON file in the app's private storage, written through a temporary file. */
class FileSpotifyLibraryStore(
    private val file: File,
) : SpotifyLibraryStore {
    override suspend fun load(): SpotifyLibrary? =
        withContext(Dispatchers.IO) {
            if (file.isFile) SpotifyLibraryJson.decode(file.readText()) else null
        }

    override suspend fun save(library: SpotifyLibrary) =
        withContext(Dispatchers.IO) {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(SpotifyLibraryJson.encode(library))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            Unit
        }
}

/**
 * Spotify in user 0 through its MediaSession (the same notification access as playback):
 * `playFromUri` with a spotify: address, or `playFromMediaId` when Spotify only offers that.
 * Without a session, an explicit media button press starts Spotify in the background. When
 * Spotify ignores the session command, [openUri] opens the address in the Spotify app; Android
 * lets a background service do that only in some states (e.g. while AuraCurve is on screen).
 */
class AndroidSpotifyStarter(
    private val context: Context,
    private val spotify: SpotifyPlaybackBackend,
) : SpotifyStarter {
    override fun unavailableReason(): String? = if (spotify.accessGranted()) null else "Benachrichtigungszugriff fehlt"

    override fun playUri(uri: String): Boolean {
        val controller = spotify.spotify() ?: return false
        val actions = controller.playbackState?.actions ?: 0L
        val byUri = actions and PlaybackState.ACTION_PLAY_FROM_URI != 0L
        val byMediaId = actions and PlaybackState.ACTION_PLAY_FROM_MEDIA_ID != 0L
        val method = if (byMediaId && !byUri) "playFromMediaId" else "playFromUri"
        Log.i(
            TAG,
            "Spotify session actions=0x${actions.toString(16)}, fromUri=$byUri, fromMediaId=$byMediaId, sending $method($uri, extras=null)",
        )
        if (byMediaId && !byUri) {
            controller.transportControls.playFromMediaId(uri, null)
        } else {
            controller.transportControls.playFromUri(Uri.parse(uri), null)
        }
        return true
    }

    override fun nowPlaying(): SpotifyNow? {
        val controller = spotify.spotify() ?: return null
        val metadata = controller.metadata
        return SpotifyNow(
            mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            playing = controller.playbackState?.let { NowPlayingSnapshot.isPlayingState(it.state) } == true,
        )
    }

    override fun describe(): String {
        val controller = spotify.spotify() ?: return "no session"
        val state = controller.playbackState
        val metadata = controller.metadata
        return buildString {
            append(state?.state?.let(::stateName) ?: "NO_STATE")
            append(" pos=").append(state?.position ?: -1)
            append(" actions=0x").append((state?.actions ?: 0L).toString(16))
            state?.errorMessage?.let { append(" error=").append(it) }
            append(" mediaId=").append(metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
            append(" title=").append(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
        }
    }

    private fun stateName(state: Int): String =
        when (state) {
            PlaybackState.STATE_NONE -> "NONE"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            PlaybackState.STATE_PAUSED -> "PAUSED"
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_FAST_FORWARDING -> "FAST_FORWARDING"
            PlaybackState.STATE_REWINDING -> "REWINDING"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            PlaybackState.STATE_ERROR -> "ERROR"
            PlaybackState.STATE_CONNECTING -> "CONNECTING"
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_TO_PREVIOUS"
            PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIPPING_TO_NEXT"
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "SKIPPING_TO_QUEUE_ITEM"
            else -> "STATE_$state"
        }

    override fun openUri(uri: String): Boolean {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .setPackage(SpotifyPlaybackBackend.SPOTIFY_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://" + context.packageName))
        return runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Spotify did not open $uri", it) }
            .isSuccess
    }

    override fun mayOpenFromBackground(): Boolean = Settings.canDrawOverlays(context)

    override fun wake() {
        val now = SystemClock.uptimeMillis()
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            val intent =
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setPackage(SpotifyPlaybackBackend.SPOTIFY_PACKAGE)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(now, now, action, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
            runCatching { context.sendBroadcast(intent) }
        }
    }

    private companion object {
        const val TAG = "SpotifyStarter"
    }
}
