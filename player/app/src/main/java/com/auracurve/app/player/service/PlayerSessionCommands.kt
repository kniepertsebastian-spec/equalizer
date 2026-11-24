package com.auracurve.app.player.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.auracurve.app.player.playback.NowPlayingState

/**
 * Commands for the player that may come from the background - the master bar switched off by
 * the quick tile, play/pause from a widget or the remote-control server. They go over the
 * player's media session (a MediaController binds to AudioPlayerService) instead of
 * `startService()`: Android 12+ refuses that for an app in the background and throws
 * BackgroundServiceStartNotAllowedException, binding is allowed at any time.
 *
 * Nothing is sent while no player has a track loaded: starting (or binding) the service only to
 * stop it, or to play an empty queue, would do nothing but cost a service start.
 */
interface PlayerCommandSender {
    /** True while the player service runs with a track loaded (it publishes NowPlayingState). */
    val hasLoadedTrack: Boolean

    /** Hands [command] (one of [PlayerSessionCommands.COMMANDS]) to the running player. */
    fun send(command: String)
}

object PlayerSessionCommands {
    // The same actions the app's screens send as intents; AudioPlayerService handles both alike.
    const val STOP = AudioPlayerService.ACTION_STOP
    const val TOGGLE_PLAYBACK = AudioPlayerService.ACTION_TOGGLE_PLAYBACK

    // Skipping from the remote-control server (roadmap 11, step 3 of the ADR 0004 addendum).
    const val NEXT = AudioPlayerService.ACTION_NEXT
    const val PREVIOUS = AudioPlayerService.ACTION_PREVIOUS

    /** What AudioPlayerService grants this app's own controllers (and nobody else) as custom commands. */
    val COMMANDS: Set<String> = setOf(STOP, TOGGLE_PLAYBACK, NEXT, PREVIOUS)

    private const val TAG = "PlayerSessionCommands"

    fun sender(context: Context): PlayerCommandSender = MediaControllerSender(context.applicationContext)

    // One short-lived controller per command: these come rarely (a tile tap, a widget, a remote
    // call), and a controller kept connected would keep the service bound and alive.
    private class MediaControllerSender(
        private val context: Context,
    ) : PlayerCommandSender {
        override val hasLoadedTrack: Boolean
            get() = NowPlayingState.current.value != null

        override fun send(command: String) {
            val token = SessionToken(context, ComponentName(context, AudioPlayerService::class.java))
            // The remote server calls from its own threads, which have no Looper: the controller
            // lives on the main thread, and is only touched there.
            val connecting =
                MediaController
                    .Builder(context, token)
                    .setApplicationLooper(Looper.getMainLooper())
                    .buildAsync()
            val mainThread = ContextCompat.getMainExecutor(context)
            connecting.addListener(
                {
                    val controller =
                        try {
                            connecting.get()
                        } catch (e: Exception) {
                            Log.w(TAG, "Player command $command dropped: no connection to the player", e)
                            return@addListener
                        }
                    controller
                        .sendCustomCommand(SessionCommand(command, Bundle.EMPTY), Bundle.EMPTY)
                        .addListener({ controller.release() }, mainThread)
                },
                mainThread,
            )
        }
    }
}
