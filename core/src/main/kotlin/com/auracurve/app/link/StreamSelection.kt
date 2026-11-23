package com.auracurve.app.link

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
//
// What SoundCloud sends (roadmap 10, P10-4, as of November 2026; sources: soundcloud/api
// issue 441 and other clients' fixes): AAC over HLS is the only format SoundCloud keeps
// developing. Progressive MP3 was switched off in August 2026; MP3 over HLS stays until the
// remaining user uploads are transcoded, Opus is on its way out. For most tracks the list is
// now "aac_160k", "aac_96k" and "abr_sq", all HLS; "aac_256k" only for Go+ listeners, and so
// far not through the public API. "abr_sq" is an adaptive playlist that can step down to
// 96 kbit/s on its own and that other clients report as broken for some tracks.
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

    // An adaptive playlist ("abr_sq") is only the last resort: its quality is not fixed and
    // it does not always play. Above unknown codecs, below every fixed stream.
    private const val ADAPTIVE_SCORE = 1

    // Playlist types: they say a stream is HLS, nothing about the codec inside.
    private val PLAYLIST_MIME_TYPES =
        setOf("audio/mpegurl", "audio/x-mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl")

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
        // "audio/mpeg; ..." without its parameters; "audio/mpegurl" is a playlist, not MP3.
        val baseMime =
            mime
                .substringBefore(';')
                .trim()
                .takeUnless { it in PLAYLIST_MIME_TYPES }
                .orEmpty()
        return when {
            preset.startsWith("aac") || "mp4a" in mime || "aac" in mime || baseMime == "audio/mp4" -> StreamCodec.AAC
            preset.startsWith("opus") || "opus" in mime || "ogg" in mime -> StreamCodec.OPUS
            preset.startsWith("mp3") || baseMime == "audio/mpeg" || baseMime == "audio/mp3" -> StreamCodec.MP3
            // Nothing said about the codec: progressive is MP3 on SoundCloud.
            option.protocol == PROGRESSIVE -> StreamCodec.MP3
            else -> StreamCodec.UNKNOWN
        }
    }

    /** An adaptive playlist ("abr_sq", "abr_hq"): the player picks the bitrate, not we. */
    fun isAdaptive(option: StreamOption): Boolean = option.preset.lowercase(Locale.ROOT).startsWith("abr")

    // Comparable quality score, higher is better. Unknown codecs rank last.
    fun quality(option: StreamOption): Int =
        if (isAdaptive(option)) {
            if (codec(option) == StreamCodec.OPUS) 0 else ADAPTIVE_SCORE
        } else {
            fixedQuality(option)
        }

    /** One line for the log: what was offered or chosen, e.g. "hls aac_160k (AAC ~160 kbit/s)". */
    fun describe(option: StreamOption): String {
        val codec = codec(option)
        val rate =
            when {
                isAdaptive(option) -> "adaptive"
                codec == StreamCodec.AAC -> "~${bitrateKbps(option.preset) ?: AAC_DEFAULT_KBPS} kbit/s"
                codec == StreamCodec.MP3 -> "~$MP3_KBPS kbit/s"
                else -> "?"
            }
        val preview = if (option.snipped) ", preview" else ""
        return "${option.protocol} ${option.preset.ifBlank { "-" }} ($codec $rate$preview)"
    }

    private fun fixedQuality(option: StreamOption): Int =
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
