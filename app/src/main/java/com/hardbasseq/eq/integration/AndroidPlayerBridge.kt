package com.hardbasseq.eq.integration

import android.content.Context
import android.content.Intent
import com.soundcloud.equalizer.player.PlayerActivity
import com.soundcloud.equalizer.player.service.AudioPlayerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPlayerBridge
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PlayerBridge {
        override fun launchPlayer(source: PlayerSource) {
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
    }
