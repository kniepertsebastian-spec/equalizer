package com.hardbasseq.eq.link

import java.util.Locale

// One way SoundCloud offers to fetch a track's audio (an entry of the track's
// "transcodings"). `snipped` marks a ~30 second preview: for tracks that need a
// SoundCloud Go subscription SoundCloud lists only snipped streams unless the
// request is made with a subscribed account's token.
//
// `preset` ("aac_160k", "aac_256k", "mp3_1_0", "opus_0_0", ...) and `mimeType`
// ("audio/mpeg", "audio/mp4; codecs=\"mp4a.40.2\"", "audio/ogg; codecs=\"opus\"")
// tell the codec and quality apart; both are optional because SoundCloud does not
// always send them.
data class StreamOption(
    val protocol: String,
    val snipped: Boolean,
    val url: String,
    val preset: String = "",
    val mimeType: String = "",
)

enum class StreamCodec { AAC, MP3, OPUS, UNKNOWN }

object StreamSelection {
    private const val PROGRESSIVE = "progressive"
    private const val HLS = "hls"

    // SoundCloud's MP3 is 128 kbit/s; a preset that names no bitrate gets this guess.
    private const val MP3_KBPS = 128
    private const val AAC_DEFAULT_KBPS = 160

    // AAC needs fewer bits than MP3 for the same quality, so one kbit/s of AAC counts
    // more. Chosen so that AAC 256 > AAC 160 > MP3 128 > AAC 96 (what the roadmap asks
    // for); a lower AAC step is only picked when nothing better is offered.
    private const val AAC_WEIGHT_PERCENT = 125

    // Full-length beats preview. Within that the best codec and bitrate wins
    // (AAC 256, AAC 160, MP3 128), and only if that ties a plain progressive download
    // beats HLS. Opus is skipped: it comes as Ogg segments inside HLS, which
    // ExoPlayer's HLS source cannot play. A snipped stream is only chosen when nothing
    // full-length is offered - the caller then knows it is playing a preview and can
    // say so.
    fun pick(options: List<StreamOption>): StreamOption? =
        options
            .filter { (it.protocol == PROGRESSIVE || it.protocol == HLS) && codec(it) != StreamCodec.OPUS }
            .minWithOrNull(
                compareBy<StreamOption> { it.snipped }
                    .thenByDescending { quality(it) }
                    .thenBy { it.protocol != PROGRESSIVE },
            )

    fun codec(option: StreamOption): StreamCodec {
        val preset = option.preset.lowercase(Locale.ROOT)
        val mime = option.mimeType.lowercase(Locale.ROOT)
        return when {
            preset.startsWith("aac") || "mp4a" in mime || "aac" in mime || "audio/mp4" in mime -> StreamCodec.AAC
            preset.startsWith("opus") || "opus" in mime || "ogg" in mime -> StreamCodec.OPUS
            preset.startsWith("mp3") || mime.startsWith("audio/mpeg") || mime.startsWith("audio/mp3") -> StreamCodec.MP3
            // Nothing said about the codec: progressive is MP3 on SoundCloud.
            option.protocol == PROGRESSIVE -> StreamCodec.MP3
            else -> StreamCodec.UNKNOWN
        }
    }

    // Comparable quality score, higher is better. Unknown codecs rank last.
    fun quality(option: StreamOption): Int =
        when (codec(option)) {
            StreamCodec.AAC -> (bitrateKbps(option.preset) ?: AAC_DEFAULT_KBPS) * AAC_WEIGHT_PERCENT / 100
            StreamCodec.MP3 -> MP3_KBPS
            StreamCodec.OPUS, StreamCodec.UNKNOWN -> 0
        }

    // "aac_256k" -> 256, "aac_160k" -> 160; null when the preset names no bitrate.
    private fun bitrateKbps(preset: String): Int? =
        Regex("(\\d{2,3})k", RegexOption.IGNORE_CASE)
            .find(preset)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
}
