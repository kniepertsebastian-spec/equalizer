package com.hardbasseq.eq.link

enum class LinkSource {
    SOUNDCLOUD,
    SPOTIFY,
    YOUTUBE,
    OTHER,
}

// What the share sheet / clipboard hands over is usually "Check this out: <url>"
// rather than a bare URL, and each service decorates its links differently
// (tracking query parameters, mobile hosts, short links). This only cleans up and
// classifies - resolving a link to actual tracks needs the network and lives in
// the player module. Only SoundCloud links can be played directly: Spotify and
// YouTube Music streams are DRM-protected and their terms forbid taking the
// audio out of their own apps, so those are recognized only to say so clearly.
object ShareLink {
    private val urlPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    // Punctuation a sentence puts after a pasted URL that is not part of it.
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}"

    fun extractUrl(text: String): String? =
        urlPattern
            .find(text)
            ?.value
            ?.trimEnd { it in TRAILING_PUNCTUATION }
            ?.takeIf { hostOf(it) != null }

    fun classify(url: String): LinkSource {
        val host = hostOf(url) ?: return LinkSource.OTHER
        return when {
            host.isSoundCloudHost() -> LinkSource.SOUNDCLOUD
            host == "spotify.link" || host == "spotify.com" || host.endsWith(".spotify.com") -> LinkSource.SPOTIFY
            host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") -> LinkSource.YOUTUBE
            else -> LinkSource.OTHER
        }
    }

    // Short links (on.soundcloud.com/...) only redirect to the real page; they have
    // to be followed over the network before they can be resolved.
    fun isSoundCloudShortLink(url: String): Boolean = hostOf(url) == "on.soundcloud.com"

    // Canonical form for the resolve API: desktop host, no query string or fragment
    // (tracking parameters such as ?si=..., ?utm_source=...). Private-playlist
    // links carry their secret in the path (.../s-XXXX), which is kept.
    fun normalizeSoundCloud(url: String): String {
        val withoutFragment = url.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?')
        val schemeEnd = withoutQuery.indexOf("://")
        if (schemeEnd < 0) return withoutQuery
        val rest = withoutQuery.substring(schemeEnd + 3)
        val host = rest.substringBefore('/')
        val path = rest.removePrefix(host)
        val cleanHost = if (host.lowercase().let { it == "m.soundcloud.com" || it == "www.soundcloud.com" }) "soundcloud.com" else host
        return "https://$cleanHost${path.trimEnd('/')}"
    }

    private val youtubeVideoIdPattern = Regex("[A-Za-z0-9_-]{11}")
    private val spotifyTrackIdPattern = Regex("[A-Za-z0-9]{22}")

    // The video behind a YouTube / YouTube Music link, or null for anything that is not
    // a single video (a playlist-only link, a channel, the home page). The playlist
    // part of a "watch?v=...&list=..." link is ignored - it is the video that was
    // shared.
    fun youtubeVideoId(url: String): String? {
        val host = hostOf(url) ?: return null
        val segments = pathSegments(url)
        val id =
            when {
                host == "youtu.be" -> segments.firstOrNull()
                host == "youtube.com" || host.endsWith(".youtube.com") ->
                    when (segments.firstOrNull()) {
                        "watch" -> queryParam(url, "v")
                        "shorts", "embed", "live", "v" -> segments.getOrNull(1)
                        else -> null
                    }
                else -> null
            }
        return id?.takeIf { youtubeVideoIdPattern.matches(it) }
    }

    fun canonicalYouTubeUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    // The track behind an open.spotify.com link (also the "intl-de/track/..." form), or
    // null for playlists, albums, artists and short links.
    fun spotifyTrackId(url: String): String? {
        if (hostOf(url) != "open.spotify.com") return null
        val segments = pathSegments(url).let { if (it.firstOrNull()?.startsWith("intl-") == true) it.drop(1) else it }
        if (segments.firstOrNull() != "track") return null
        return segments.getOrNull(1)?.takeIf { spotifyTrackIdPattern.matches(it) }
    }

    fun canonicalSpotifyTrackUrl(trackId: String): String = "https://open.spotify.com/track/$trackId"

    // The public embed page of a single track; unlike the oEmbed preview it names the artist.
    fun spotifyTrackEmbedUrl(trackId: String): String = "https://open.spotify.com/embed/track/$trackId"

    // The playlist behind an open.spotify.com link, or null for anything else.
    fun spotifyPlaylistId(url: String): String? {
        if (hostOf(url) != "open.spotify.com") return null
        val segments = pathSegments(url).let { if (it.firstOrNull()?.startsWith("intl-") == true) it.drop(1) else it }
        if (segments.firstOrNull() != "playlist") return null
        return segments.getOrNull(1)?.takeIf { spotifyTrackIdPattern.matches(it) }
    }

    // The public embed page of a playlist (what Spotify shows inside other sites); it
    // lists the playlist's tracks without a login.
    fun spotifyPlaylistEmbedUrl(playlistId: String): String = "https://open.spotify.com/embed/playlist/$playlistId"

    private fun pathSegments(url: String): List<String> =
        url
            .substringAfter("://", missingDelimiterValue = "")
            .substringAfter('/', missingDelimiterValue = "")
            .substringBefore('?')
            .substringBefore('#')
            .split('/')
            .filter { it.isNotEmpty() }

    private fun queryParam(
        url: String,
        name: String,
    ): String? =
        url
            .substringAfter('?', missingDelimiterValue = "")
            .substringBefore('#')
            .split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }

    private fun String.isSoundCloudHost(): Boolean = this == "soundcloud.com" || endsWith(".soundcloud.com") || this == "snd.sc"

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return null
        val host =
            afterScheme
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfter('@')
                .substringBefore(':')
                .lowercase()
        return host.takeIf { it.contains('.') }
    }
}
