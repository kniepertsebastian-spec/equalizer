package com.auracurve.app.remote.playback

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.auracurve.app.integration.PlayerController
import com.auracurve.app.player.playback.RepeatSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The built-in player, through the same PlayerController the player screen uses. */
class PlayerPlaybackBackend(
    private val controller: PlayerController,
) : PlaybackBackend {
    override val source = RemoteSource.PLAYER

    override fun unavailableReason(): String? = null

    override fun snapshot(volume: VolumeLevel): NowPlayingSnapshot? {
        val now = controller.nowPlaying.value ?: return null
        return NowPlayingSnapshot(
            source = source,
            title = now.title,
            artist = now.artist,
            isPlaying = now.isPlaying,
            positionMs = now.positionMs,
            durationMs = now.durationMs,
            shuffle = now.shuffle,
            repeat = now.repeat.name,
            volume = volume,
            coverUrl = now.artworkUrl,
        )
    }

    override fun isPlaying(): Boolean = controller.nowPlaying.value?.isPlaying == true

    // nowPlaying also moves with the position: only what playback.changed is about counts.
    override val changeSignals: Flow<Unit> =
        controller.nowPlaying
            .map { now -> now?.let { listOf(it.title, it.artist, it.isPlaying, it.shuffle, it.repeat, it.durationMs) } }
            .distinctUntilChanged()
            .map { }

    override fun playPause() = controller.togglePlayback()

    override fun pause() {
        if (isPlaying()) controller.togglePlayback()
    }

    override fun next() = controller.next()

    override fun previous() = controller.previous()

    override fun setShuffle(enabled: Boolean): Boolean {
        controller.setShuffle(enabled)
        return true
    }

    override fun setRepeat(mode: String): Boolean {
        controller.setRepeat(RepeatSetting.valueOf(mode))
        return true
    }
}

/**
 * Spotify in user 0 through its MediaSession (U-1: notification access). Reading other apps'
 * sessions needs the user to allow AuraCurve's notification listener once in Android's settings.
 * Shuffle and repeat exist only as Spotify's custom actions (PoC finding 17.11.2026); they are
 * tried by name and otherwise refused.
 */
class SpotifyPlaybackBackend(
    private val context: Context,
) : PlaybackBackend {
    override val source = RemoteSource.SPOTIFY_MAIN
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, RemoteNotificationListener::class.java)

    internal fun accessGranted(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    internal fun spotify(): MediaController? =
        if (!accessGranted()) {
            null
        } else {
            runCatching { sessions.getActiveSessions(listener).firstOrNull { it.packageName == SPOTIFY_PACKAGE } }.getOrNull()
        }

    override fun unavailableReason(): String? =
        when {
            !accessGranted() -> "Benachrichtigungszugriff fehlt"
            spotify() == null -> "Spotify läuft nicht"
            else -> null
        }

    override fun snapshot(volume: VolumeLevel): NowPlayingSnapshot? {
        val controller = spotify() ?: return null
        val metadata = controller.metadata
        val state = controller.playbackState
        val playing = state != null && NowPlayingSnapshot.isPlayingState(state.state)
        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        return NowPlayingSnapshot(
            source = source,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            isPlaying = playing,
            positionMs =
                state?.let {
                    NowPlayingSnapshot.sessionPosition(
                        it.position,
                        it.lastPositionUpdateTime,
                        it.playbackSpeed,
                        playing,
                        SystemClock.elapsedRealtime(),
                        durationMs,
                    )
                } ?: 0L,
            durationMs = durationMs,
            // Not readable from a framework MediaSession: left out of now-playing.
            shuffle = null,
            repeat = null,
            volume = volume,
        )
    }

    override fun isPlaying(): Boolean = spotify()?.playbackState?.let { NowPlayingSnapshot.isPlayingState(it.state) } == true

    /**
     * Spotify's own session callbacks (state, metadata, session gone) plus Android's list of active
     * sessions, so a Spotify that starts or stops is picked up too. Without notification access
     * Android refuses the session listener: then only the API's poll notices changes.
     */
    override val changeSignals: Flow<Unit> =
        callbackFlow {
            val handler = Handler(Looper.getMainLooper())
            var watched: MediaController? = null
            val callback =
                object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        trySend(Unit)
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        trySend(Unit)
                    }

                    override fun onSessionDestroyed() {
                        trySend(Unit)
                    }
                }

            fun watch(controllers: List<MediaController>?) {
                val next = controllers?.firstOrNull { it.packageName == SPOTIFY_PACKAGE }
                if (next?.sessionToken == watched?.sessionToken) return
                watched?.unregisterCallback(callback)
                watched = next
                next?.registerCallback(callback, handler)
                trySend(Unit)
            }

            val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
            val listening =
                accessGranted() && runCatching { sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler) }.isSuccess
            watch(listOfNotNull(spotify()))
            awaitClose {
                if (listening) runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsChanged) }
                watched?.unregisterCallback(callback)
            }
        }.flowOn(Dispatchers.Main.immediate)

    override fun playPause() {
        val controller = spotify() ?: return
        if (controller.playbackState?.let { NowPlayingSnapshot.isPlayingState(it.state) } == true) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    override fun pause() {
        spotify()?.transportControls?.pause()
    }

    override fun next() {
        spotify()?.transportControls?.skipToNext()
    }

    override fun previous() {
        spotify()?.transportControls?.skipToPrevious()
    }

    override fun setShuffle(enabled: Boolean): Boolean = sendCustomAction("shuffle")

    override fun setRepeat(mode: String): Boolean = sendCustomAction("repeat")

    // Spotify only offers toggles here, so the requested state cannot be guaranteed: one press.
    private fun sendCustomAction(keyword: String): Boolean {
        val controller = spotify() ?: return false
        val action: PlaybackState.CustomAction =
            controller.playbackState?.customActions?.firstOrNull { it.action.contains(keyword, ignoreCase = true) } ?: return false
        controller.transportControls.sendCustomAction(action, null)
        return true
    }

    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}

/** STREAM_MUSIC through AudioManager; changes from the volume keys arrive through Settings.System. */
class AndroidVolumeBackend(
    private val context: Context,
) : VolumeBackend {
    private val audio = context.getSystemService(AudioManager::class.java)

    override fun current(): VolumeLevel =
        VolumeLevel(audio.getStreamVolume(AudioManager.STREAM_MUSIC), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))

    override fun set(level: Int) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
    }

    override val changes: Flow<VolumeLevel> =
        callbackFlow {
            trySend(current())
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(current())
                    }
                }
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }
}
