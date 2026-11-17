package com.auracurve.app.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Flavor "store": no remote-control server (Pixel-Car module 04 is "full" only).
object NoRemoteServerControl : RemoteServerControl {
    override val isAvailable: Boolean = false
    override val running: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
}
