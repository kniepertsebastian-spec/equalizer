package com.auracurve.app.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The names (SSIDs) of the Wi-Fi networks the phone is a client of, for the list of trusted Wi-Fi
 * names (NetworkZone). Android only tells an app the name with the location permission, and since
 * Android 12 only to a network callback that asks for location info. Without the permission every
 * name is null and only the hotspot and the switch "Also reachable in Wi-Fi" count, as before.
 */
@Singleton
class WifiNames
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val names = ConcurrentHashMap<Network, String>()
        private val _current = MutableStateFlow<String?>(null)

        /** The name of the Wi-Fi the phone is connected to right now, if Android tells it. */
        val current: StateFlow<String?> = _current.asStateFlow()

        @Volatile
        private var registered = false

        fun hasPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** The Wi-Fi name of [network], if it is a Wi-Fi Android told the name of. */
        fun nameOf(network: Network): String? {
            ensureRegistered()
            return names[network]
        }

        /** Registers the callback once the permission is there; asking again later is cheap. */
        @Synchronized
        fun ensureRegistered() {
            if (registered || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !hasPermission()) return
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
            val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            val callback =
                object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        val name = NetworkZone.cleanWifiName((capabilities.transportInfo as? WifiInfo)?.ssid)
                        if (name == null) names.remove(network) else names[network] = name
                        _current.value = name ?: names.values.firstOrNull()
                    }

                    override fun onLost(network: Network) {
                        names.remove(network)
                        _current.value = names.values.firstOrNull()
                    }
                }
            runCatching { connectivity.registerNetworkCallback(request, callback) }
                .onSuccess { registered = true }
                .onFailure { Log.w(TAG, "No Wi-Fi callback: ${it.javaClass.simpleName}") }
        }

        private companion object {
            const val TAG = "WifiNames"
        }
    }
