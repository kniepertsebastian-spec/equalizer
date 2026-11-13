package com.hardbasseq.eq.playlist

/**
 * Ids of tracks that come from files on the device instead of SoundCloud (roadmap 06, P5-7).
 *
 * The queue, the now-playing state and the car browser all name a track by one `Long`. SoundCloud's
 * ids are positive; a local file gets a negative one derived from its address, so the two can never
 * collide and "is this a file on the device?" needs no extra field.
 */
object LocalTrackId {
    /** What a file's address looks like: `content://` from the system file picker, `file://` for a path. */
    fun isLocalAddress(address: String?): Boolean = address != null && (address.startsWith("content://") || address.startsWith("file://"))

    /** Negative and the same for the same address, also across restarts (it is a hash of the text). */
    fun of(address: String): Long {
        // 64-bit FNV-1a: stable (String.hashCode would do too, but is only 32 bits wide).
        var hash = -0x340d631b7bdddcdbL
        for (char in address) {
            hash = hash xor char.code.toLong()
            hash *= 0x100000001b3L
        }
        // Never 0 and never positive.
        return -(hash and Long.MAX_VALUE) - 1L
    }

    fun isLocal(trackId: Long): Boolean = trackId < 0L
}
