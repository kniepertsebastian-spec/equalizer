package com.hardbasseq.eq.link

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Reads the song and its artist out of a Spotify track's public embed page. The oEmbed
// preview names only the title, which makes the SoundCloud search much less precise; the
// embed page's JSON also has the artist. Like SpotifyPlaylistPage this is not an official
// interface: the pieces are found by name, and null means "could not read it".
object SpotifyTrackPage {
    fun parse(html: String): SpotifyTrack? {
        val root = SpotifyPlaylistPage.pageData(html) ?: return null
        return findTrack(root)
    }

    // The first object that has both a name/title and an artist (an "artists" list or a "subtitle").
    private fun findTrack(element: JsonElement): SpotifyTrack? =
        when (element) {
            is JsonObject -> toTrack(element) ?: element.values.firstNotNullOfOrNull { findTrack(it) }
            is JsonArray -> element.firstNotNullOfOrNull { findTrack(it) }
            else -> null
        }

    private fun toTrack(entry: JsonObject): SpotifyTrack? {
        val artist = SpotifyPlaylistPage.firstArtistName(entry["artists"]) ?: SpotifyPlaylistPage.text(entry["subtitle"]) ?: return null
        val title = SpotifyPlaylistPage.text(entry["name"]) ?: SpotifyPlaylistPage.text(entry["title"]) ?: return null
        return SpotifyTrack(title, artist)
    }
}
