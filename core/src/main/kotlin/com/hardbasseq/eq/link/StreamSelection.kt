package com.hardbasseq.eq.link

// One way SoundCloud offers to fetch a track's audio (an entry of the track's
// "transcodings"). `snipped` marks a ~30 second preview: for tracks that need a
// SoundCloud Go subscription SoundCloud lists only snipped streams unless the
// request is made with a subscribed account's token.
data class StreamOption(
    val protocol: String,
    val snipped: Boolean,
    val url: String,
)

object StreamSelection {
    private const val PROGRESSIVE = "progressive"
    private const val HLS = "hls"

    // Full-length beats preview, and within that a plain progressive download beats
    // HLS (simpler and the path that has always worked here). A snipped stream is
    // only chosen when nothing full-length is offered - the caller then knows it is
    // playing a preview and can say so.
    fun pick(options: List<StreamOption>): StreamOption? =
        options
            .filter { it.protocol == PROGRESSIVE || it.protocol == HLS }
            .minByOrNull { rank(it) }

    private fun rank(option: StreamOption): Int = (if (option.snipped) 2 else 0) + (if (option.protocol == PROGRESSIVE) 0 else 1)
}
