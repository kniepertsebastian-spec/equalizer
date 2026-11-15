package com.auracurve.app.playlist

/**
 * Tracks that carry their own stream address and are played from it directly, without a lookup:
 * files on the device and - in the full flavor - internet radio stations and Audius tracks
 * (AuraCurve roadmap 04/06). Like files on the device they get a negative id (see [LocalTrackId]),
 * so they never collide with SoundCloud's positive ids.
 */
object DirectStream {
    /** A stable id for an entry of an online catalog, e.g. `idOf("radio", stationUuid)`. */
    fun idOf(
        source: String,
        key: String,
    ): Long = LocalTrackId.of("$source:$key")

    /**
     * Whether [address] is played as it is: a file on the device, or an encrypted web address of a
     * track with an id of its own (negative). Plain `http://` is never played directly.
     */
    fun playsDirectly(
        trackId: Long,
        address: String?,
    ): Boolean {
        if (LocalTrackId.isLocalAddress(address)) return true
        return LocalTrackId.isLocal(trackId) && address != null && address.startsWith("https://")
    }
}
