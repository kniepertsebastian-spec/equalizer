package com.hardbasseq.eq.link

import java.text.Normalizer

// What to look for on SoundCloud when someone shares a song from YouTube or Spotify:
// an artist (when known) and a title, cleaned of the decoration those services put
// into titles ("(Official Video)", "[HD]", "| Channel", ...).
data class TrackQuery(
    val artist: String?,
    val title: String,
) {
    val searchText: String get() = listOfNotNull(artist, title).joinToString(" ")

    // For "Artist - Title" style display.
    val label: String get() = if (artist.isNullOrBlank()) title else "$artist - $title"
}

object TrackQueryBuilder {
    // Bracketed decoration that says nothing about which song it is. Version markers
    // that do (remix, edit, vip, bootleg, ...) are deliberately not in this list.
    private val noise =
        Regex(
            "official|video|audio|lyric|lyrics|visuali[sz]er|\\bhd\\b|\\bhq\\b|\\b4k\\b|\\bmv\\b|clip|explicit|" +
                "out now|free download|premiere|full (song|track|version)|with lyrics|music",
            RegexOption.IGNORE_CASE,
        )
    private val bracketGroup = Regex("[(\\[{]([^)\\]}]*)[)\\]}]")
    private val dashLike = Regex("\\s*[\\u2013\\u2014\\u2012\\u2015]\\s*")
    private val channelSuffix = Regex("\\s*(-\\s*topic|vevo|official|records|music)\\s*$", RegexOption.IGNORE_CASE)

    fun fromYouTube(
        videoTitle: String,
        channel: String?,
    ): TrackQuery {
        val cleaned = cleanTitle(videoTitle)
        val dash = cleaned.indexOf(" - ")
        if (dash > 0) {
            val artist = cleaned.substring(0, dash).trim()
            val title = cleaned.substring(dash + 3).trim()
            if (artist.isNotEmpty() && title.isNotEmpty()) return TrackQuery(artist, title)
        }
        val pipe = cleaned.indexOf(" | ")
        if (pipe > 0) {
            val title = cleaned.substring(0, pipe).trim()
            val artist = cleaned.substring(pipe + 3).trim()
            if (artist.isNotEmpty() && title.isNotEmpty()) return TrackQuery(artist, title)
        }
        val artist = channel?.let { cleanChannel(it) }?.takeIf { it.isNotBlank() }
        return TrackQuery(artist, cleaned.ifBlank { videoTitle.trim() })
    }

    // Spotify's preview data carries the track name only, no artist.
    fun fromTitleOnly(title: String): TrackQuery = TrackQuery(null, cleanTitle(title).ifBlank { title.trim() })

    private fun cleanTitle(raw: String): String {
        val normalizedDashes = raw.replace(dashLike, " - ")
        val withoutNoise =
            bracketGroup.replace(normalizedDashes) { match ->
                if (noise.containsMatchIn(match.groupValues[1])) " " else match.value
            }
        return withoutNoise
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '\'', '“', '”', '|', '-', ' ')
    }

    private fun cleanChannel(raw: String): String = channelSuffix.replace(raw.trim(), "").trim()
}

// Ranks SoundCloud search results for a TrackQuery. The service's search is fuzzy and
// returns plenty of loosely related tracks, so the results are scored by how many of
// the query's words they actually contain. Uploader names often differ from the
// artist (labels, promo channels), so the artist may match in the title or the
// uploader.
object TrackMatcher {
    private val stopWords = setOf("the", "a", "an", "and", "of", "feat", "ft", "featuring", "official", "video", "audio", "lyrics")

    // Score at or above which the best result is trusted enough to start playing by
    // itself - only ever with a known artist; title-only queries always ask.
    const val CONFIDENT_SCORE = 0.8

    fun <T> rank(
        query: TrackQuery,
        candidates: List<T>,
        title: (T) -> String,
        uploader: (T) -> String,
    ): List<Pair<T, Double>> =
        candidates
            .map { it to score(query, title(it), uploader(it)) }
            .sortedByDescending { it.second }

    // Whether every word of the artist name appears in the track's title or uploader.
    fun mentionsArtist(
        artist: String,
        title: String,
        uploader: String,
    ): Boolean {
        val wanted = tokens(artist)
        if (wanted.isEmpty()) return false
        return tokens("$title $uploader").containsAll(wanted)
    }

    fun isConfident(
        query: TrackQuery,
        score: Double,
    ): Boolean = !query.artist.isNullOrBlank() && score >= CONFIDENT_SCORE

    fun score(
        query: TrackQuery,
        candidateTitle: String,
        candidateUploader: String,
    ): Double {
        val candidate = tokens("$candidateTitle $candidateUploader")
        val titleTokens = tokens(query.title)
        if (titleTokens.isEmpty() || candidate.isEmpty()) return 0.0

        val titleCoverage = coverage(titleTokens, candidate)
        val artistTokens = tokens(query.artist.orEmpty())
        val artistCoverage = if (artistTokens.isEmpty()) titleCoverage else coverage(artistTokens, candidate)

        // Words the result has that the query does not: a hint it is a different
        // (longer) track, a mix, a compilation.
        val queryTokens = titleTokens + artistTokens
        val extraFraction = (candidate - queryTokens).size.toDouble() / candidate.size
        return (0.65 * titleCoverage + 0.35 * artistCoverage - 0.25 * extraFraction).coerceIn(0.0, 1.0)
    }

    private fun coverage(
        wanted: Set<String>,
        available: Set<String>,
    ): Double = wanted.count { it in available }.toDouble() / wanted.size

    private fun tokens(text: String): Set<String> =
        Normalizer
            .normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in stopWords }
            .toSet()
}
