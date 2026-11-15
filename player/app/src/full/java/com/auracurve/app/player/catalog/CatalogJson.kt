package com.auracurve.app.player.catalog

import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.DirectStream
import org.json.JSONArray
import org.json.JSONObject

// A station from Radio Browser (https://api.radio-browser.info), already reduced to what plays.
data class RadioStation(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String?,
    val tags: List<String>,
    val countryCode: String,
    val codec: String,
    val bitrateKbps: Int,
    val isHls: Boolean,
    val clickCount: Int,
) {
    // A live stream has no length (0) and no artist; the tags say what it plays.
    fun toTrack(): TrackItem =
        TrackItem(
            id = DirectStream.idOf(RADIO_SOURCE, uuid),
            title = name,
            artist = tags.take(3).joinToString(", "),
            artworkUrl = faviconUrl,
            streamUrl = streamUrl,
            durationMs = 0L,
            isHlsStream = isHls,
        )

    companion object {
        const val RADIO_SOURCE = "radio"
    }
}

// Pure parsing of the two catalogs' answers, so it is testable against saved real responses.
object CatalogJson {
    /**
     * Radio Browser's station list. Only stations that play: an encrypted (https) address - plain http
     * is blocked by Android and was decided against - a name, and the last check passed.
     */
    fun radioStations(body: String): List<RadioStation> {
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url_resolved").trim()
            val name = cleanName(o.optString("name"))
            if (!url.startsWith("https://") || name.isEmpty() || o.optInt("lastcheckok", 1) != 1) return@mapNotNull null
            RadioStation(
                uuid = o.optString("stationuuid"),
                name = name,
                streamUrl = url,
                faviconUrl = o.optString("favicon").trim().takeIf { it.startsWith("https://") },
                tags =
                    o
                        .optString("tags")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                countryCode = o.optString("countrycode"),
                codec = o.optString("codec"),
                bitrateKbps = o.optInt("bitrate"),
                isHls = o.optInt("hls") == 1,
                clickCount = o.optInt("clickcount"),
            )
        }.filter { it.uuid.isNotEmpty() }
    }

    /**
     * Audius tracks (`data` of /v1/tracks/trending or /search). Only what streams freely: streamable,
     * not gated, not deleted. [streamUrlFor] builds the stable stream address of an id.
     */
    fun audiusTracks(
        body: String,
        streamUrlFor: (String) -> String,
    ): List<AudiusTrack> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isEmpty() || !o.optBoolean("is_streamable", false) || o.optBoolean("is_stream_gated", false) ||
                o.optBoolean("is_delete", false)
            ) {
                return@mapNotNull null
            }
            val artwork = o.optJSONObject("artwork")
            AudiusTrack(
                genre = o.optString("genre"),
                track =
                    TrackItem(
                        id = DirectStream.idOf(AudiusTrack.AUDIUS_SOURCE, id),
                        title = o.optString("title").trim(),
                        artist =
                            o
                                .optJSONObject("user")
                                ?.optString("name")
                                .orEmpty()
                                .trim(),
                        artworkUrl = artwork?.optString("480x480")?.takeIf { it.isNotEmpty() } ?: artwork?.optString("150x150"),
                        streamUrl = streamUrlFor(id),
                        durationMs = o.optLong("duration") * 1000L,
                    ),
            )
        }
    }

    // Station names often carry decoration: "* FRENCHCORE || …", " - 100 % - HANDSUP <3 - …".
    private fun cleanName(raw: String): String = raw.trim().trim('*', '-', '#', ' ', '|').trim()
}

// An Audius track with its genre, so search results can be narrowed to the chosen genres.
data class AudiusTrack(
    val genre: String,
    val track: TrackItem,
) {
    companion object {
        const val AUDIUS_SOURCE = "audius"
    }
}
