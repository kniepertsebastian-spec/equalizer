package com.hardbasseq.eq.discovery

import java.text.Normalizer

// Decides whether an upload fits the genres the user wants, from the genre and tags
// the uploader set on SoundCloud. Those are free text and inconsistent ("Uptempo
// Hardcore", "uptempo", "Hardcore Techno"), so matching is by whole words and
// related scene names are treated as one family (a wish for "uptempo" also accepts
// "hardcore" or "gabber").
object GenreMatcher {
    enum class Verdict {
        // The upload's genre or tags fit.
        MATCH,

        // The uploader set neither genre nor tags: no evidence either way.
        UNKNOWN,

        // It has a genre or tags and none of them fit (a "Schlager" track by a
        // namesake artist).
        MISMATCH,
    }

    private val hardScene =
        (
            "hardcore,uptempo,gabber,gabba,terror,terrorcore,frenchcore,speedcore," +
                "industrial hardcore,mainstream hardcore,early hardcore,uptempo hardcore,freeform"
        ).split(',')
    private val hardstyleScene = "hardstyle,rawstyle,raw hardstyle,euphoric hardstyle,hard dance,hardtek".split(',')
    private val drumAndBassScene = "drum and bass,drum n bass,dnb,neurofunk,jungle".split(',')
    private val families = listOf(hardScene, hardstyleScene, drumAndBassScene).map { family -> family.map { normalize(it) } }

    // "hardcore" is also punk and hip hop: for the hard-dance scene those tags do not count.
    private val conflicting = "punk,hip hop,hiphop,rap,metal,screamo,emo,post hardcore".split(',').map { normalize(it) }

    fun normalize(text: String): String =
        Normalizer
            .normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    // SoundCloud's tag_list: space separated, multi-word tags in double quotes.
    fun parseTags(raw: String): List<String> =
        Regex("\"([^\"]+)\"|(\\S+)")
            .findAll(raw)
            .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .toList()

    // The wished-for keyword plus its scene siblings, normalized.
    fun expand(keyword: String): Set<String> {
        val normalized = normalize(keyword)
        if (normalized.isEmpty()) return emptySet()
        val words = normalized.split(' ')
        val family = families.firstOrNull { members -> members.any { member -> containsPhrase(words, member.split(' ')) } }
        return (family?.toSet() ?: emptySet()) + normalized
    }

    fun verdict(
        genre: String,
        tagList: String,
        wanted: List<String>,
    ): Verdict {
        val accepted = wanted.flatMap { expand(it) }.toSet()
        if (accepted.isEmpty()) return Verdict.MATCH
        val terms = (listOf(normalize(genre)) + parseTags(tagList)).filter { it.isNotEmpty() }.distinct()
        if (terms.isEmpty()) return Verdict.UNKNOWN

        val wantsHardScene = accepted.any { it in families[0] }
        var usable = terms
        var acceptedNow = accepted
        if (wantsHardScene) {
            // Plain "hardcore" is also punk, metal and rap. When the upload carries such a
            // tag, the bare word is not enough - it needs a specific scene word (uptempo,
            // gabber, terror, ...) - and the conflicting tags themselves never count.
            val clash = terms.any { isConflicting(it) }
            usable = terms.filterNot { isConflicting(it) }
            if (clash) acceptedNow = accepted - "hardcore"
        }
        val fits = usable.any { term -> acceptedNow.any { containsPhrase(term.split(' '), it.split(' ')) } }
        return if (fits) Verdict.MATCH else Verdict.MISMATCH
    }

    private fun isConflicting(term: String): Boolean = conflicting.any { containsPhrase(term.split(' '), it.split(' ')) }

    // Whether `needle` occurs in `haystack` as consecutive whole words.
    private fun containsPhrase(
        haystack: List<String>,
        needle: List<String>,
    ): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        for (start in 0..haystack.size - needle.size) {
            if (needle.indices.all { haystack[start + it] == needle[it] }) return true
        }
        return false
    }
}

// What genres the user listens to, read from tracks they liked: the genre words that
// recur often enough to be a taste rather than noise.
object GenreProfile {
    private const val MIN_TRACKS = 5
    private const val MIN_COUNT = 3
    private const val MIN_SHARE = 0.05
    private const val MAX_GENRES = 6

    // Words that say nothing about the style.
    private val generic =
        (
            "electronic music dance remix mashup bootleg edit free download new original mix edm other " +
                "unreleased mastered preview live set dj"
        ).split(' ').toSet()

    fun derive(tracks: List<Pair<String, String>>): List<String> {
        if (tracks.size < MIN_TRACKS) return emptyList()
        val counts = mutableMapOf<String, Int>()
        for ((genre, tagList) in tracks) {
            val terms =
                (listOf(GenreMatcher.normalize(genre)) + GenreMatcher.parseTags(tagList))
                    .filter { it.length >= 3 && !it.all { c -> c.isDigit() } && !it.split(' ').all { word -> word in generic } }
                    .distinct()
            for (term in terms) counts[term] = (counts[term] ?: 0) + 1
        }
        val threshold = maxOf(MIN_COUNT, (tracks.size * MIN_SHARE).toInt())
        return counts.entries
            .filter { it.value >= threshold }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_GENRES)
            .map { it.key }
    }
}
