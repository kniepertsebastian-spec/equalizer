package com.auracurve.app.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pixel-Car module 04e (U-2): starts the remote-control server after a reboot when "Start at boot"
 * is on. BOOT_COMPLETED only arrives after the first unlock (the Pixel has a screen lock); Direct
 * Boot is not part of v0. The server's foreground type specialUse may be started from here on
 * Android 15, unlike mediaPlayback.
 */
@AndroidEntryPoint
class RemoteServerBootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var settings: RemoteServerSettings

    @Inject
    lateinit var control: AndroidRemoteServerControl

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                if (BootStart.shouldStart(settings.startAtBoot.first(), control.isAvailable)) control.start()
            } catch (e: Exception) {
                Log.w("RemoteServerBoot", "Remote control server not started at boot: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}

/** Whether a boot starts the server: only when asked for, and only for the main user (R-10). */
object BootStart {
    fun shouldStart(
        startAtBoot: Boolean,
        mainUser: Boolean,
    ): Boolean = startAtBoot && mainUser
}
