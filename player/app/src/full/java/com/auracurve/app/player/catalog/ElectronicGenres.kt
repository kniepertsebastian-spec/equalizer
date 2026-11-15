package com.auracurve.app.player.catalog

// The electronic genres the radio and Audius tabs offer (AuraCurve: hard dance first). One genre is
// active at a time; ALL merges every genre of the list. Labels are genre names and stay English.

// Radio Browser: free-form station tags, matched exactly.
enum class RadioGenre(
    val label: String,
    val tags: List<String>,
) {
    ALL("All electronic", emptyList()),
    HARDSTYLE("Hardstyle", listOf("hardstyle")),
    HARDCORE("Hardcore", listOf("hardcore", "frenchcore")),
    TECHNO("Techno", listOf("techno")),
    TRANCE("Trance", listOf("trance")),
    HOUSE("House", listOf("house")),
    DRUM_AND_BASS("Drum & Bass", listOf("drum and bass", "dnb")),
    DUBSTEP("Dubstep", listOf("dubstep")),
    EDM("EDM", listOf("edm")),
    ;

    /** The tags to query: the genre's own, or those of every genre for ALL. */
    fun queryTags(): List<String> = if (this == ALL) entries.flatMap { it.tags } else tags
}

// Audius: its fixed genre names (checked against the live API on 15.11.2026).
enum class AudiusGenre(
    val label: String,
    val apiNames: List<String>,
) {
    ALL("All electronic", emptyList()),
    HARDSTYLE("Hardstyle", listOf("Hardstyle")),
    HARDCORE("Hardcore", listOf("Hardcore")),
    HARD_DANCE("Hard Dance", listOf("Hard Dance")),
    TECHNO("Techno", listOf("Techno")),
    TRANCE("Trance", listOf("Trance")),
    HOUSE("House", listOf("House", "Tech House", "Deep House", "Progressive House")),
    DRUM_AND_BASS("Drum & Bass", listOf("Drum & Bass", "Jungle")),
    DUBSTEP("Dubstep", listOf("Dubstep")),
    TRAP("Trap", listOf("Trap")),
    ;

    /** The Audius genres this choice covers; ALL covers every genre of the list plus "Electronic". */
    fun apiGenres(): List<String> = if (this == ALL) listOf("Electronic") + entries.flatMap { it.apiNames } else apiNames
}
