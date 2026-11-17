package com.auracurve.app.remote

import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared code needs to know about the local remote-control server (Pixel-Car module 04).
 * The server itself exists only in the "full" flavor (src/full); the store flavor binds
 * [NoRemoteServerControl], which is never available and never running.
 */
interface RemoteServerControl {
    /** False in the store flavor, and in "full" outside the main Android user (R-10). */
    val isAvailable: Boolean

    /**
     * True while the server runs. The session service does not close itself for idleness then:
     * a phone in the car must keep the equalizer on Spotify for as long as it can be controlled.
     */
    val running: StateFlow<Boolean>
}
