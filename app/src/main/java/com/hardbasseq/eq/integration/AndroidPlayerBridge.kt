package com.hardbasseq.eq.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.soundcloud.equalizer.player.PlayerActivity
import com.soundcloud.equalizer.player.playback.VirtualBassState
import com.soundcloud.equalizer.player.service.AudioPlayerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPlayerBridge
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PlayerBridge {
        override fun launchPlayer(source: PlayerSource) {
            // YouTube audio cannot be played by the built-in player (terms of service): the choice opens
            // the YouTube app, and the equalizer attaches if that app announces its audio session.
            if (source == PlayerSource.YOUTUBE) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            }
            val sourceExtra =
                when (source) {
                    PlayerSource.SOUNDCLOUD -> PlayerActivity.SOURCE_SOUNDCLOUD
                    PlayerSource.YOUTUBE -> PlayerActivity.SOURCE_YOUTUBE
                }
            val intent =
                Intent(context, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_SOURCE, sourceExtra)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
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
    }
