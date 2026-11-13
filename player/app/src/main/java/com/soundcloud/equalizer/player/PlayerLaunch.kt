package com.soundcloud.equalizer.player

import android.content.Context
import android.content.Intent

/**
 * How anything outside the player screen (the playback notification, the lock screen, the
 * Android Auto session, the master bar's source picker) brings HardBass EQ's Compose player
 * forward. All of them used to start the old View-based PlayerActivity; the player lives in
 * MainActivity now, which answers [ACTION_OPEN_PLAYER] (an intent filter in :app's manifest,
 * the string there must stay the same) by showing the player screen.
 *
 * A dedicated action rather than the launcher intent: when the app is already running, a plain
 * launcher start may only bring the task forward without delivering the intent to MainActivity.
 */
object PlayerLaunch {
    const val ACTION_OPEN_PLAYER = "com.hardbasseq.eq.action.OPEN_PLAYER"

    // Restricted to this app, so only MainActivity can answer it.
    fun openPlayerIntent(context: Context): Intent =
        Intent(ACTION_OPEN_PLAYER)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
