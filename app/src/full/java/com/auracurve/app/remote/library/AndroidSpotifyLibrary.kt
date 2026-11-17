package com.auracurve.app.remote.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import com.auracurve.app.link.LibraryTrack
import com.auracurve.app.link.PlaylistInfo
import com.auracurve.app.link.SpotifyLibrary
import com.auracurve.app.link.SpotifyLibraryJson
import com.auracurve.app.player.spotify.SpotifyApiClient
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
 * `playFromUri` with a spotify: address. Without a session, an explicit media button press
 * starts Spotify in the background (no activity start, which a service may not do).
 */
class AndroidSpotifyStarter(
    private val context: Context,
    private val spotify: SpotifyPlaybackBackend,
) : SpotifyStarter {
    override fun unavailableReason(): String? = if (spotify.accessGranted()) null else "Benachrichtigungszugriff fehlt"

    override fun playUri(uri: String): Boolean {
        val controller = spotify.spotify() ?: return false
        controller.transportControls.playFromUri(Uri.parse(uri), null)
        return true
    }

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
}
