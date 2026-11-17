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
) {
    val pairingLink: String? get() = if (address != null && token != null) LocalAddress.pairingLink(address, token) else null
}

@HiltViewModel
class RemoteServerViewModel
    @Inject
    constructor(
        private val control: AndroidRemoteServerControl,
        private val settings: RemoteServerSettings,
    ) : ViewModel() {
        val state: StateFlow<RemoteServerUiState> =
            combine(control.running, control.address, settings.token, control.startError) { running, address, token, error ->
                RemoteServerUiState(control.isAvailable, running, address, token, error)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteServerUiState(available = control.isAvailable))

        fun setEnabled(enabled: Boolean) {
            if (enabled) {
                viewModelScope.launch {
                    settings.ensureToken()
                    control.start()
                }
            } else {
                control.stop()
            }
        }

        fun regenerateToken() {
            viewModelScope.launch { settings.regenerateToken() }
        }
    }
