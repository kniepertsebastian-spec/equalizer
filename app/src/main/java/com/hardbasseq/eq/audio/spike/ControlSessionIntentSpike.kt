package com.hardbasseq.eq.audio.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Build

data class ControlSessionBroadcastEvent(
    val action: String,
    val audioSessionId: Int,
    val packageName: String?,
)

/**
 * Validates the `AudioEffect.ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION`
 * broadcast plumbing end-to-end using our own test player as the sender:
 * registers a receiver for both actions, then sends the same broadcasts a
 * cooperative player app would send, and reports whether they round-trip
 * correctly with the right extras.
 *
 * This is roadmap M0's "Open/Close-AudioEffect-Control-Intents mit einem
 * eigenen kleinen Testplayer validieren" – it proves *our* receiver-side
 * logic works, which is the plumbing a later milestone needs to detect
 * *other* apps' sessions. It says nothing about whether any particular
 * third-party player actually sends these broadcasts on its own; that
 * remains unverified and is out of scope here.
 *
 * The receiver is registered with `RECEIVER_NOT_EXPORTED` (API 33+): this
 * spike only needs to observe broadcasts this app sends to itself. Actually
 * receiving another app's broadcast would require `RECEIVER_EXPORTED` and a
 * deliberate security review – a later-milestone decision, not made here.
 */
class ControlSessionIntentSpike(private val context: Context) {

    private var receiver: BroadcastReceiver? = null

    fun startListening(onEvent: (ControlSessionBroadcastEvent) -> Unit) {
        stopListening()
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                onEvent(
                    ControlSessionBroadcastEvent(
                        action = intent.action ?: "unknown",
                        audioSessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1),
                        packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME),
                    ),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(newReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(newReceiver, filter)
        }
        receiver = newReceiver
    }

    fun sendTestOpenBroadcast(audioSessionId: Int) {
        send(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId)
    }

    fun sendTestCloseBroadcast(audioSessionId: Int) {
        send(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId)
    }

    private fun send(action: String, audioSessionId: Int) {
        val intent = Intent(action).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun stopListening() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }
}
