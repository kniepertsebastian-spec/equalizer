package com.hardbasseq.eq.preset

// Chat feature: turns a (GenrePreset, PresetIntensity) pair into a concrete
// Preset. The resolved id encodes both halves, so a device profile's
// boundPresetId can reference a specific combination directly - no
// DeviceProfileEntity schema change needed to persist the genre/intensity
// choice separately, and the selector UI can restore its own state by parsing
// the currently active preset's id back apart (see parse()).
object PresetIntensityResolver {
    // Preset ids elsewhere in the app use single underscores (e.g.
    // builtin_clean_punch), so a double underscore stays unambiguous to split
    // back apart in parse().
    private const val SEPARATOR = "__"

    fun resolvedId(
        genreId: String,
        intensity: PresetIntensity,
    ): String = "$genreId$SEPARATOR${intensity.id}"

    fun resolve(
        genre: GenrePreset,
        intensity: PresetIntensity,
    ): Preset {
        val id = resolvedId(genre.id, intensity)
        val name = "${genre.displayName} – ${intensity.displayName}"
        val base = genre.base
        if (!genre.allowsIntensity) {
            return base.copy(id = id, name = name)
        }
        return base.copy(
            id = id,
            name = name,
            macroBassDb = base.macroBassDb + intensity.macroBassDeltaDb,
            macroPunchDb = base.macroPunchDb + intensity.macroPunchDeltaDb,
            mbcThresholdDb = (base.mbcThresholdDb + intensity.mbcThresholdDeltaDb).coerceIn(-30f, 0f),
            mbcRatio = (base.mbcRatio + intensity.mbcRatioDelta).coerceIn(1f, 6f),
        )
    }

    // Reverses resolve()'s id scheme so the UI can figure out which genre and
    // intensity a persisted or freshly resolved preset id came from, without
    // storing that pair a second time anywhere.
    fun parse(
        presetId: String,
        genres: List<GenrePreset>,
    ): Pair<GenrePreset, PresetIntensity>? {
        val separatorIndex = presetId.lastIndexOf(SEPARATOR)
        if (separatorIndex < 0) return null
        val genreId = presetId.substring(0, separatorIndex)
        val intensityId = presetId.substring(separatorIndex + SEPARATOR.length)
        val genre = genres.find { it.id == genreId } ?: return null
        val intensity = PresetIntensity.fromId(intensityId) ?: return null
        return genre to intensity
    }
}
