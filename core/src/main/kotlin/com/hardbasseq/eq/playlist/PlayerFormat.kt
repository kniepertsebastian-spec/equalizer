package com.hardbasseq.eq.playlist

// Small presentation helpers for the player screen, kept here so they are unit-testable.
object PlayerFormat {
    // 187_000 -> "3:07", 3_723_000 -> "1:02:03". Negative or unknown (<= 0) reads "0:00".
    fun duration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    // SoundCloud serves artwork as ...-large.jpg (100x100), which looks blurry on the
    // full player; the same image exists at other sizes under a different suffix.
    // Anything that does not follow that pattern is returned unchanged.
    fun largeArtwork(url: String?): String? = url?.replace("-large.", "-t500x500.")

    // 0f..1f progress through a track, 0f while the duration is unknown.
    fun progress(
        positionMs: Long,
        durationMs: Long,
    ): Float = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

// "heute", "gestern", "vor 3 Tagen" for how long ago an upload was.
object AgeFormat {
    private const val MS_PER_DAY = 86_400_000L

    fun daysAgo(
        nowMs: Long,
        thenMs: Long,
    ): String {
        val days = ((nowMs - thenMs) / MS_PER_DAY).coerceAtLeast(0)
        return when (days) {
            0L -> "heute"
            1L -> "gestern"
            else -> "vor $days Tagen"
        }
    }
}
