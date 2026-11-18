package com.auracurve.app.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the "Remote control (local)" card shows. */
data class RemoteServerUiState(
    val available: Boolean = false,
    val running: Boolean = false,
    val address: String? = null,
    val error: String? = null,
    val startAtBoot: Boolean = false,
    val hostName: String = MdnsName.DEFAULT,
    /** Module 05: the code shown for pairing one phone, while it is valid. */
    val pairing: PairingCode? = null,
    val devices: List<RemoteDevice> = emptyList(),
    /** Module 05 (E-016): also answer in the home Wi-Fi. */
    val allowWifi: Boolean = false,
) {
    /** 04e (R-19): the fixed name, so the link survives a new hotspot address. */
    val nameUrl: String get() = LocalAddress.url(MdnsName.host(hostName))

    val pairingLink: String? get() = linkTo(MdnsName.host(hostName))

    /** The same link with the IP, for networks that do not resolve `.local` (QR dialog). */
    val ipPairingLink: String? get() = address?.let(::linkTo)

    private fun linkTo(host: String): String? = pairing?.let { if (address != null) LocalAddress.pairingLink(host, it.code) else null }
}

@HiltViewModel
class RemoteServerViewModel
    @Inject
    constructor(
        private val control: AndroidRemoteServerControl,
        private val settings: RemoteServerSettings,
        private val registry: RemoteDeviceRegistry,
    ) : ViewModel() {
        private val devices = registry.devices

        val state: StateFlow<RemoteServerUiState> =
            combine(
                control.running,
                control.address,
                control.startError,
                settings.startAtBoot,
                settings.hostName,
            ) { running, address, error, boot, name ->
                RemoteServerUiState(control.isAvailable, running, address, error, boot, name)
            }.combine(devices.pairing) { state, code -> state.copy(pairing = code) }
                .combine(devices.devices) { state, list -> state.copy(devices = list) }
                .combine(settings.allowWifi) { state, wifi -> state.copy(allowWifi = wifi) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteServerUiState(available = control.isAvailable))

        private var expiry: Job? = null

        init {
            viewModelScope.launch { registry.migrateLegacyToken() }
        }

        fun setEnabled(enabled: Boolean) {
            if (enabled) {
                control.start()
            } else {
                control.stop()
                devices.cancelPairing()
                // The boot switch is only shown while the server is on: switching the server off
                // by hand also stops it from coming back at the next boot.
                viewModelScope.launch { settings.setStartAtBoot(false) }
            }
        }

        fun setStartAtBoot(enabled: Boolean) {
            viewModelScope.launch { settings.setStartAtBoot(enabled) }
        }

        fun setHostName(name: String) {
            viewModelScope.launch { settings.setHostName(name) }
        }

        fun setAllowWifi(enabled: Boolean) {
            viewModelScope.launch { settings.setAllowWifi(enabled) }
        }

        /** Shows a new code; it disappears when used, after five minutes, or with [cancelPairing]. */
        fun startPairing() {
            val code = devices.startPairing()
            expiry?.cancel()
            expiry =
                viewModelScope.launch {
                    delay((code.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L) + 100L)
                    devices.expirePairing()
                }
        }

        fun cancelPairing() {
            expiry?.cancel()
            devices.cancelPairing()
        }

        fun removeDevice(id: String) = devices.revoke(id)

        fun removeAllDevices() = devices.revokeAll()

        override fun onCleared() {
            // A code nobody can see any more should not stay valid.
            devices.cancelPairing()
        }
    }
