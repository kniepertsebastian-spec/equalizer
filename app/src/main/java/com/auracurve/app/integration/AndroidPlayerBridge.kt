package com.auracurve.app.integration

import android.content.Context
import com.auracurve.app.player.PlayerLaunch
import com.auracurve.app.player.playback.PlayerCurve
import com.auracurve.app.player.playback.PlayerCurveState
import com.auracurve.app.player.playback.VirtualBassState
import com.auracurve.app.player.service.PlayerCommandSender
import com.auracurve.app.player.service.PlayerSessionCommands
import com.auracurve.app.preset.TargetPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPlayerBridge
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PlayerBridge {
        // stopPlayer and togglePlayback are called from the background too (quick tile, widget,
        // remote server): they reach the player over its media session, never with
        // startService(), which Android 12+ refuses in the background (see PlayerSessionCommands).
        // Replaced in tests.
        internal var playerCommands: PlayerCommandSender = PlayerSessionCommands.sender(context)

        // SoundCloud is the only source and the player screen is the same for every source, so
        // [source] needs no extra: the Compose player in MainActivity is brought forward.
        override fun launchPlayer(source: PlayerSource) {
            context.startActivity(PlayerLaunch.openPlayerIntent(context))
        }

        // No track loaded: no player runs (or it holds nothing), there is nothing to stop - the
        // service is not started only to be stopped again.
        override fun stopPlayer() {
            if (playerCommands.hasLoadedTrack) playerCommands.send(PlayerSessionCommands.STOP)
        }

        // Nothing loaded: nothing to play or pause (the service ignored that all along).
        override fun togglePlayback() {
            if (playerCommands.hasLoadedTrack) playerCommands.send(PlayerSessionCommands.TOGGLE_PLAYBACK)
        }

        override fun setVirtualBassMix(mix: Float) {
            VirtualBassState.mix = mix.coerceIn(0f, 1f)
        }

        override fun setPlayerCurve(
            curve: List<TargetPoint>,
            subsonicCutoffHz: Float,
            headroomSafetyRatio: Float,
        ) {
            PlayerCurveState.current = PlayerCurve(curve, subsonicCutoffHz, headroomSafetyRatio.coerceIn(0f, 1f))
        }
    }
