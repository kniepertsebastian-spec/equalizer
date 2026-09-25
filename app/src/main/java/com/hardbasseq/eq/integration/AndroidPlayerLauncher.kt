package com.hardbasseq.eq.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPlayerLauncher
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PlayerLauncher {
        companion object {
            const val PLAYER_PACKAGE = "com.soundcloud.equalizer.player"
            const val ACTION_STOP_FROM_EQUALIZER = "com.soundcloud.equalizer.player.ACTION_STOP_FROM_EQUALIZER"
            const val EXTRA_LAUNCHED_FROM_EQUALIZER = "com.hardbasseq.eq.EXTRA_LAUNCHED_FROM_EQUALIZER"
        }

        override fun isPlayerInstalled(): Boolean =
            try {
                context.packageManager.getPackageInfo(PLAYER_PACKAGE, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }

        override fun launchPlayer() {
            val launchIntent =
                context.packageManager.getLaunchIntentForPackage(PLAYER_PACKAGE) ?: return
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.putExtra(EXTRA_LAUNCHED_FROM_EQUALIZER, true)
            context.startActivity(launchIntent)
        }

        override fun stopPlayer() {
            val stopIntent =
                Intent(ACTION_STOP_FROM_EQUALIZER).apply {
                    setPackage(PLAYER_PACKAGE)
                }
            context.sendBroadcast(stopIntent)
        }
    }
