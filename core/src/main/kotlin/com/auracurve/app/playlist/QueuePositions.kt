package com.auracurve.app.playlist

import kotlin.math.abs

// Where a queue entry is by the time a command for it arrives at the player. A screen names an
// entry by its position in the queue it last saw; a command sent a moment earlier (a removal,
// a move) may have shifted that entry since. Named by position alone, the second of two quick
// removals would hit the neighbour. Pure functions, free of Android/ExoPlayer.
object QueuePositions {
    /** For commands that name no track: the position is taken as it is. */
    const val NO_TRACK = Long.MIN_VALUE

    /**
     * @param trackIds the queue as the player holds it now (null for an entry that is not ours)
     * @param index where the screen saw the entry
     * @param expectedTrackId the track the screen saw there, or [NO_TRACK]
     * @return the entry's position now: [index] when the track is still there, otherwise the
     *   nearest position that holds the track (the lower one when two are equally near, since
     *   entries move up when something before them is removed); null when the track is gone
     *   or [index] is outside the queue and no track was named
     */
    fun resolve(
        trackIds: List<Long?>,
        index: Int,
        expectedTrackId: Long,
    ): Int? {
        if (expectedTrackId == NO_TRACK) return index.takeIf { it in trackIds.indices }
        if (trackIds.getOrNull(index) == expectedTrackId) return index
        var best: Int? = null
        var bestDistance = Int.MAX_VALUE
        trackIds.forEachIndexed { position, trackId ->
            val distance = abs(position - index)
            if (trackId == expectedTrackId && distance < bestDistance) {
                best = position
                bestDistance = distance
            }
        }
        return best
    }
}
