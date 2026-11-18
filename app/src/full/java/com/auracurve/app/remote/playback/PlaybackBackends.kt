package com.auracurve.app.remote.playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** One thing the playback endpoints can steer: the built-in player or Spotify in user 0. */
interface PlaybackBackend {
    val source: RemoteSource

    /** Null when it can be used now, else why not (German, shown by the web remote). */
    fun unavailableReason(): String?

    /** What plays there now, or null when nothing is loaded. */
    fun snapshot(volume: VolumeLevel): NowPlayingSnapshot?

    fun isPlaying(): Boolean

    fun playPause()

    fun pause()

    fun next()

    fun previous()

    /** False when this source offers no way to set it (the endpoint answers 409 then). */
    fun setShuffle(enabled: Boolean): Boolean

    fun setRepeat(mode: String): Boolean

    /**
     * A signal whenever the track, play/pause or the session itself may have changed, so
     * playback.changed goes out at once instead of at the next poll. Spurious signals are fine:
     * the API compares the state before sending anything.
     */
    val changeSignals: Flow<Unit> get() = emptyFlow()
}

/** The system media volume, STREAM_MUSIC (contract Q-S06). */
interface VolumeBackend {
    fun current(): VolumeLevel

    fun set(level: Int)

    /** Every change, also from the volume keys or another app. */
    val changes: Flow<VolumeLevel>
}
