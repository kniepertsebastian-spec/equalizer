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
 *
 * Also after the app updated itself (MY_PACKAGE_REPLACED): the update ends the process and with it
 * the server, and in the car nobody switches it on again (device test 18 Nov 2026, 2.0.9 → 2.1.1).
 * Same condition as at boot; FGS starts are allowed from this broadcast too.
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
        if (!BootStart.handles(intent.action)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                if (BootStart.shouldStart(settings.startAtBoot.first(), control.isAvailable)) control.start()
            } catch (e: Exception) {
                Log.w("RemoteServerBoot", "Remote control server not started (${intent.action}): ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}

/** Whether a boot or an app update starts the server: only when asked for, and only for the main user (R-10). */
object BootStart {
    fun handles(action: String?): Boolean = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED

    fun shouldStart(
        startAtBoot: Boolean,
        mainUser: Boolean,
    ): Boolean = startAtBoot && mainUser
}
