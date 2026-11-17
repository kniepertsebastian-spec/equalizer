package com.auracurve.app.remote.playback

import kotlinx.coroutines.flow.Flow

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
}

/** The system media volume, STREAM_MUSIC (contract Q-S06). */
interface VolumeBackend {
    fun current(): VolumeLevel

    fun set(level: Int)

    /** Every change, also from the volume keys or another app. */
    val changes: Flow<VolumeLevel>
}
