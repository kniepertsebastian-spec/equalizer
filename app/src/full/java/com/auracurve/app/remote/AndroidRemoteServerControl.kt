package com.auracurve.app.remote

import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts and stops [RemoteServerService] and tells the rest of the app whether it runs
 * (Pixel-Car module 04a). Off by default: nothing starts the server but the switch.
 */
@Singleton
class AndroidRemoteServerControl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RemoteServerControl {
        // R-10: only the main user (user 0) runs the server; a second user's copy of the app stays off.
        override val isAvailable: Boolean =
            (context.getSystemService(Context.USER_SERVICE) as UserManager).isSystemUser

        private val _running = MutableStateFlow(false)
        override val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _address = MutableStateFlow<String?>(null)

        /** The phone's private IPv4 address while the server runs, for the pairing link. */
        val address: StateFlow<String?> = _address.asStateFlow()

        private val _startError = MutableStateFlow<String?>(null)

        /** Why the last start failed (port taken, not allowed in the background), or null. */
        val startError: StateFlow<String?> = _startError.asStateFlow()

        /** Call while the app is on screen: Android only allows starting a foreground service then. */
        fun start() {
            if (!isAvailable) return
            _startError.value = null
            ContextCompat.startForegroundService(context, Intent(context, RemoteServerService::class.java))
        }

        fun stop() {
            context.stopService(Intent(context, RemoteServerService::class.java))
        }

        internal fun onServerStarted(address: String?) {
            _address.value = address
            _running.value = true
        }

        internal fun onAddressChanged(address: String?) {
            _address.value = address
        }

        internal fun onServerStopped(error: String? = null) {
            _running.value = false
            _address.value = null
            if (error != null) _startError.value = error
        }
    }
