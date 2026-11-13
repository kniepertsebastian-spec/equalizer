package com.hardbasseq.eq.integration

import android.content.Context
import android.content.Intent
import com.hardbasseq.eq.preset.TargetPoint
import com.soundcloud.equalizer.player.PlayerLaunch
import com.soundcloud.equalizer.player.playback.PlayerCurve
import com.soundcloud.equalizer.player.playback.PlayerCurveState
import com.soundcloud.equalizer.player.playback.VirtualBassState
import com.soundcloud.equalizer.player.service.AudioPlayerService
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
