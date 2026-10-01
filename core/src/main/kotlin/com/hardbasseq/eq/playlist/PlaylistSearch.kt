package com.hardbasseq.eq.playlist

import java.text.Normalizer

// Searching inside one playlist: every word the user typed has to appear somewhere in the title
// or the artist (any case, accents ignored, parts of words count, so "angerf" finds "Angerfist").
object PlaylistSearch {
    // [index] is the track's place in the whole playlist, kept so that playing a hit starts the
    // playlist at that spot and not at the top of the filtered list.
    data class Hit(
        val index: Int,
        val track: SavedTrack,
    )

    fun filter(
        tracks: List<SavedTrack>,
        query: String,
    ): List<Hit> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        val all = tracks.mapIndexed { index, track -> Hit(index, track) }
        if (words.isEmpty()) return all
        return all.filter { hit ->
            val haystack = normalize("${hit.track.title} ${hit.track.artist}")
            words.all { it in haystack }
        }
    }

    private fun normalize(text: String): String =
        Normalizer
            .normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
