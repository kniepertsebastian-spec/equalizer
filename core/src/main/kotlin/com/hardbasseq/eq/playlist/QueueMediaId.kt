package com.hardbasseq.eq.playlist

// How a track is named inside the player's queue, which ExoPlayer holds as a list of media
// items. Pure functions, free of Android, so the formats are unit-testable.
//
// - The media id ("queue|<trackId>") has a prefix of its own, so it cannot be mistaken for
//   an id of the car's browser (AutoMediaId) or of another app.
// - The address is only a placeholder ("hardbasseq-sc://track/<trackId>"): SoundCloud's
//   stream addresses expire, so the real one is looked up when ExoPlayer loads the entry
//   (see ResolvingTrackMediaSource in :player).
object QueueMediaId {
    const val STREAM_SCHEME = "hardbasseq-sc"
    private const val ID_PREFIX = "queue|"
    private const val STREAM_PREFIX = "$STREAM_SCHEME://track/"

    fun of(trackId: Long): String = "$ID_PREFIX$trackId"

    // null for anything that is not a queue id of ours.
    fun trackId(mediaId: String): Long? = mediaId.removePrefixOrNull(ID_PREFIX)?.toLongOrNull()

    fun streamUri(trackId: Long): String = "$STREAM_PREFIX$trackId"

    // null for a real address (or anything else that is not our placeholder).
    fun trackIdFromStreamUri(uri: String): Long? = uri.removePrefixOrNull(STREAM_PREFIX)?.toLongOrNull()

    private fun String.removePrefixOrNull(prefix: String): String? = if (startsWith(prefix)) substring(prefix.length) else null
}
