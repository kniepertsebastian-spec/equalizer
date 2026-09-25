package com.soundcloud.equalizer.player.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soundcloud.equalizer.player.service.AudioPlayerService

/**
 * Receives the "turn off" signal from HardBass EQ's master on/off bar so this player
 * stops and releases its audio effect session when the user switches the equalizer off,
 * without the equalizer needing an explicit (same-signature) bind to our service.
 */
class EqualizerControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_FROM_EQUALIZER = "com.soundcloud.equalizer.player.ACTION_STOP_FROM_EQUALIZER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_FROM_EQUALIZER) return

        val stopIntent = Intent(context, AudioPlayerService::class.java).apply {
            action = AudioPlayerService.ACTION_STOP
        }
        context.startService(stopIntent)
    }
}
