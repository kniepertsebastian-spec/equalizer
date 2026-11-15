package com.auracurve.app.integration

import android.content.Context
import android.content.Intent
import com.auracurve.app.player.PlayerLaunch
import com.auracurve.app.player.playback.PlayerCurve
import com.auracurve.app.player.playback.PlayerCurveState
import com.auracurve.app.player.playback.VirtualBassState
import com.auracurve.app.player.service.AudioPlayerService
import com.auracurve.app.preset.TargetPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPlayerBridge
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PlayerBridge {
        // SoundCloud is the only source and the player screen is the same for every source, so
        // [source] needs no extra: the Compose player in MainActivity is brought forward.
        override fun launchPlayer(source: PlayerSource) {
            context.startActivity(PlayerLaunch.openPlayerIntent(context))
        }

        override fun stopPlayer() {
            val stopIntent =
                Intent(context, AudioPlayerService::class.java).apply {
                    action = AudioPlayerService.ACTION_STOP
                }
            context.startService(stopIntent)
        }

        override fun togglePlayback() {
            val toggleIntent =
                Intent(context, AudioPlayerService::class.java).apply {
                    action = AudioPlayerService.ACTION_TOGGLE_PLAYBACK
                }
            context.startService(toggleIntent)
        }

        override fun setVirtualBassMix(mix: Float) {
            VirtualBassState.mix = mix.coerceIn(0f, 1f)
        }

        override fun setPlayerCurve(
            curve: List<TargetPoint>,
            subsonicCutoffHz: Float,
        ) {
            PlayerCurveState.current = PlayerCurve(curve, subsonicCutoffHz)
        }
    }
