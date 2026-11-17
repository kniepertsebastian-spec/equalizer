package com.auracurve.app.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val token: String? = null,
    val error: String? = null,
    val startAtBoot: Boolean = false,
    val hostName: String = MdnsName.DEFAULT,
) {
    /** 04e (R-19): the fixed name, so the link survives a new hotspot address. */
    val nameUrl: String get() = LocalAddress.url(MdnsName.host(hostName))

    val pairingLink: String? get() = linkTo(MdnsName.host(hostName))

    /** The same link with the IP, for networks that do not resolve `.local` (QR dialog). */
    val ipPairingLink: String? get() = address?.let(::linkTo)

    private fun linkTo(host: String): String? = if (address != null && token != null) LocalAddress.pairingLink(host, token) else null
}

@HiltViewModel
class RemoteServerViewModel
    @Inject
    constructor(
        private val control: AndroidRemoteServerControl,
        private val settings: RemoteServerSettings,
    ) : ViewModel() {
        val state: StateFlow<RemoteServerUiState> =
            combine(
                control.running,
                control.address,
                settings.token,
                control.startError,
                settings.startAtBoot,
            ) { running, address, token, error, boot ->
                RemoteServerUiState(control.isAvailable, running, address, token, error, boot)
            }.combine(settings.hostName) { state, name -> state.copy(hostName = name) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteServerUiState(available = control.isAvailable))

        fun setEnabled(enabled: Boolean) {
            if (enabled) {
                viewModelScope.launch {
                    settings.ensureToken()
                    control.start()
                }
            } else {
                control.stop()
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

        fun regenerateToken() {
            viewModelScope.launch { settings.regenerateToken() }
        }
    }
