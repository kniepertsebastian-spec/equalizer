package com.auracurve.app.preset

/** Every built-in preset of one genre, as the expandable genre list shows it. */
data class GenrePresetGroup(
    val genre: GenrePreset,
    val entries: List<GenreVariant>,
)

// The genre list: per genre its base sound ("Moderate", the intensity steps adjust it), the
// older hand-authored presets that belong to it, its character variants and its use cases.
object GenrePresetGroups {
    private val baseDescriptions: Map<String, String> =
        mapOf(
            BuiltInGenrePresets.Terror.id to
                "The reference terror sound: heavy low end, the already distorted highs tamed. A good start " +
                "on most speakers and headphones; the intensity steps add or take away bass.",
            BuiltInGenrePresets.Uptempo.id to
                "The reference uptempo sound: deep, clean kick with a little attack. An all-rounder for " +
                "speakers and headphones; the intensity steps add or take away bass.",
            BuiltInGenrePresets.Gabber.id to
                "The reference gabber sound: the kick body at 120 Hz in front, the boxy mids scooped. Works " +
                "on most systems; the intensity steps add or take away bass.",
            BuiltInGenrePresets.Early.id to
                "The reference early hardcore sound: deep rolling bass with some bite. An all-rounder; the " +
                "intensity steps add or take away bass.",
            BuiltInGenrePresets.Frenchcore.id to
                "The reference frenchcore sound: fast, punchy kick with presence. An all-rounder; the " +
                "intensity steps add or take away bass.",
            BuiltInGenrePresets.Dance.id to
                "The reference hard dance sound: moderate bass and a slight scoop. Safe on every system; the " +
                "intensity steps add or take away bass.",
            BuiltInGenrePresets.Pop.id to
                "A gentle smile curve for pop and mixed playlists. Fits almost everything; the intensity " +
                "steps add or take away bass.",
            BuiltInGenrePresets.Rap.id to
                "The reference rap sound: deep bass and clear vocals. An all-rounder; the intensity steps " +
                "add or take away bass.",
            BuiltInGenrePresets.Flat.id to
                "No coloration: a clean reference to compare against, or the base for a headphone " +
                "correction on its own.",
        )

    // The original uptempo presets, kept by id for profiles bound to them. Clean Punch is the
    // Uptempo base itself, so it is not listed twice.
    private val legacy: Map<String, List<GenreVariant>> =
        mapOf(
            BuiltInGenrePresets.Uptempo.id to
                listOf(
                    GenreVariant(
                        BuiltInPresets.DeepRumble,
                        "Deep, rumbling sub with a softer kick: for headphones, soundbars and a subwoofer. " +
                            "Booms in a car or on a small speaker.",
                    ),
                    GenreVariant(
                        BuiltInPresets.KickAttack,
                        "Punch at the kick instead of the sub, with a sharper attack: for the car, speakers " +
                            "that boom and loud listening.",
                    ),
                    GenreVariant(
                        BuiltInPresets.FinalSmash,
                        "The strongest uptempo preset, everything up: for short, loud sessions on capable " +
                            "speakers. If it pumps or distorts, step down to Kick Attack.",
                    ),
                ),
        )

    private fun groupOf(genre: GenrePreset): GenrePresetGroup {
        val base =
            GenreVariant(
                PresetIntensityResolver.resolve(genre, PresetIntensity.MODERATE),
                baseDescriptions[genre.id].orEmpty(),
            )
        return GenrePresetGroup(genre, listOf(base) + legacy[genre.id].orEmpty() + BuiltInGenreVariants.variantsOf(genre))
    }

    val all: List<GenrePresetGroup> = BuiltInGenrePresets.all.map(::groupOf)

    /** The groups for the given genres, in their order (the screen passes its own genre list). */
    fun forGenres(genres: List<GenrePreset>): List<GenrePresetGroup> =
        genres.map { genre -> all.firstOrNull { it.genre.id == genre.id } ?: groupOf(genre) }

    /** The genre whose list holds this preset id, or null (custom presets, context presets). */
    fun genreOf(presetId: String): GenrePreset? = all.firstOrNull { group -> group.entries.any { it.preset.id == presetId } }?.genre
}
