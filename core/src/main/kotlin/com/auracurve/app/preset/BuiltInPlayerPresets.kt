package com.auracurve.app.preset

/**
 * Roadmap 15: the speaker-safe genre presets for the built-in player ([PresetSourceScope.OWN_PLAYER]).
 *
 * The player runs its own chain (29-band pre-EQ, bass mono, soft clipper, then the 5-band MBC and
 * limiter) with no loudness normalization in front of it, so the system-EQ genre tunings
 * ([BuiltInGenrePresets]) move a speaker cone much further there. Each player base keeps its genre's
 * shape with less deep sub, and always carries:
 * - a subsonic high-pass at 35 Hz or above (nothing musical below it, only cone travel),
 * - a limiter ceiling of -1.0 dB or lower instead of -0.3 dB,
 * - no virtual bass (synthesized harmonics would add level the speaker has to play).
 *
 * Roadmap 18: with the deep sub filtered, the kick (80-170 Hz) carried the whole bass and came out
 * as a hard "knock" on loud masters. Every base's kick points are 25 % lower and its Punch macro
 * 35 % lower than in roadmap 15; the 250-300 Hz cut stays, so the low mids do not turn dull.
 *
 * Ids are `player_<genre id>` (e.g. `player_genre_uptempo`), their use cases
 * `player_<genre id>_use_<use case>` ([GenreUseCaseTuning.presetId]), so they never collide with the
 * system-EQ presets and [GenrePresetGroups.counterpartPresetId] can map between the two.
 * Flat has no player version: it fits every source as it is.
 *
 * Deliberately does not reference [BuiltInPresets] (that object folds this one in).
 */
object BuiltInPlayerPresets {
    const val ID_PREFIX = "player_"

    /** The id of the player version of the genre with [genreId] (a [BuiltInGenrePresets] id). */
    fun idFor(genreId: String): String = "$ID_PREFIX$genreId"

    private fun p(
        hz: Float,
        db: Float,
    ) = TargetPoint(hz, db)

    private fun player(
        genre: GenrePreset,
        curve: List<TargetPoint>,
        bass: Float,
        punch: Float,
        haerte: Float,
        headroom: Float,
        mbcThreshold: Float,
        mbcRatio: Float,
        limiterDb: Float,
        subsonicHz: Float,
    ): GenrePreset {
        val id = idFor(genre.id)
        return GenrePreset(
            id = id,
            displayName = genre.displayName,
            base =
                Preset(
                    id = id,
                    name = "${genre.displayName} – Player",
                    targetCurve = curve,
                    macroBassDb = bass,
                    macroPunchDb = punch,
                    macroHaerteDb = haerte,
                    requestedHeadroomDb = headroom,
                    mbcEnabled = true,
                    mbcThresholdDb = mbcThreshold,
                    mbcRatio = mbcRatio,
                    limiter = LimiterConfig(enabled = true, thresholdDb = limiterDb),
                    subsonicCutoffHz = subsonicHz,
                    virtualBassMix = 0f,
                    // Same genre key as the system-EQ version, so the screen keeps the genre's look.
                    metadata =
                        PresetMetadata(
                            genre = genre.base.metadata.genre,
                            builtIn = true,
                            sourceScope = PresetSourceScope.OWN_PLAYER,
                        ),
                ),
        )
    }

    val Uptempo =
        player(
            BuiltInGenrePresets.Uptempo,
            listOf(p(55f, 1.2f), p(105f, 1.1f), p(280f, -2.0f), p(3500f, 0.8f), p(8500f, -1.0f)),
            bass = 0.3f,
            punch = 0.7f,
            haerte = 0.3f,
            headroom = 4.0f,
            mbcThreshold = -9.0f,
            mbcRatio = 2.6f,
            limiterDb = -1.0f,
            subsonicHz = 38f,
        )

    val Gabber =
        player(
            BuiltInGenrePresets.Gabber,
            listOf(p(50f, 1.0f), p(120f, 1.5f), p(300f, -2.5f), p(1200f, 0.8f), p(5000f, -0.5f)),
            bass = 0.2f,
            punch = 0.8f,
            haerte = 0.3f,
            headroom = 4.0f,
            mbcThreshold = -9.0f,
            mbcRatio = 2.8f,
            limiterDb = -1.0f,
            subsonicHz = 40f,
        )

    val Frenchcore =
        player(
            BuiltInGenrePresets.Frenchcore,
            listOf(p(50f, 1.0f), p(100f, 1.4f), p(260f, -2.5f), p(2000f, 0.8f), p(4500f, 1.0f), p(9000f, -1.0f)),
            bass = 0.2f,
            punch = 1.0f,
            haerte = 0.8f,
            headroom = 4.5f,
            mbcThreshold = -8.5f,
            mbcRatio = 3.0f,
            limiterDb = -1.0f,
            subsonicHz = 40f,
        )

    val Early =
        player(
            BuiltInGenrePresets.Early,
            listOf(p(45f, 1.5f), p(90f, 0.9f), p(300f, -2.0f), p(1500f, 0.8f), p(5000f, 0.3f)),
            bass = 0.4f,
            punch = 0.7f,
            haerte = 0.5f,
            headroom = 4.0f,
            mbcThreshold = -9.0f,
            mbcRatio = 2.6f,
            limiterDb = -1.0f,
            subsonicHz = 36f,
        )

    // Already distorted masters: the highs are tamed further, the limiter sits lowest.
    val Terror =
        player(
            BuiltInGenrePresets.Terror,
            listOf(p(50f, 1.5f), p(90f, 0.8f), p(300f, -3.5f), p(2500f, -1.2f), p(6000f, -2.0f), p(12000f, -1.0f)),
            bass = 0.3f,
            punch = 0.3f,
            haerte = -1.0f,
            headroom = 5.0f,
            mbcThreshold = -11.0f,
            mbcRatio = 3.2f,
            limiterDb = -1.2f,
            subsonicHz = 42f,
        )

    val Dance =
        player(
            BuiltInGenrePresets.Dance,
            listOf(p(65f, 1.0f), p(120f, 0.6f), p(300f, -1.5f), p(4000f, 0.5f)),
            bass = 0.0f,
            punch = 0.3f,
            haerte = 0.2f,
            headroom = 3.0f,
            mbcThreshold = -8.0f,
            mbcRatio = 2.0f,
            limiterDb = -1.0f,
            subsonicHz = 35f,
        )

    val Rap =
        player(
            BuiltInGenrePresets.Rap,
            listOf(p(50f, 1.5f), p(90f, 0.8f), p(250f, -1.0f), p(2500f, 0.8f), p(5000f, 0.4f), p(9000f, -0.5f)),
            bass = 0.3f,
            punch = 0.2f,
            haerte = 0.0f,
            headroom = 3.5f,
            mbcThreshold = -9.5f,
            mbcRatio = 2.2f,
            limiterDb = -1.0f,
            subsonicHz = 35f,
        )

    val Pop =
        player(
            BuiltInGenrePresets.Pop,
            listOf(p(60f, 0.8f), p(150f, 0.2f), p(1000f, 0.0f), p(3000f, 0.2f), p(10000f, 0.8f)),
            bass = 0.0f,
            punch = 0.1f,
            haerte = 0.1f,
            headroom = 2.0f,
            mbcThreshold = -9.0f,
            mbcRatio = 1.8f,
            limiterDb = -1.0f,
            subsonicHz = 30f,
        )

    val Hardtekk =
        player(
            BuiltInGenrePresets.Hardtekk,
            listOf(p(50f, 0.8f), p(95f, 1.5f), p(150f, 0.8f), p(300f, -3.0f), p(2500f, 1.0f), p(5000f, -0.8f), p(10000f, -0.5f)),
            bass = 0.2f,
            punch = 0.8f,
            haerte = 0.4f,
            headroom = 4.0f,
            mbcThreshold = -9.0f,
            mbcRatio = 3.0f,
            limiterDb = -1.0f,
            subsonicHz = 40f,
        )

    val Techno =
        player(
            BuiltInGenrePresets.Techno,
            listOf(p(45f, 1.2f), p(90f, 0.9f), p(250f, -1.5f), p(1200f, 0.0f), p(3500f, 0.4f), p(8000f, 0.8f), p(13000f, 0.5f)),
            bass = 0.3f,
            punch = 0.4f,
            haerte = 0.1f,
            headroom = 3.5f,
            mbcThreshold = -9.5f,
            mbcRatio = 2.2f,
            limiterDb = -1.0f,
            subsonicHz = 35f,
        )

    val HardTechno =
        player(
            BuiltInGenrePresets.HardTechno,
            listOf(p(48f, 1.5f), p(100f, 1.2f), p(280f, -2.5f), p(1800f, 0.4f), p(3500f, 0.6f), p(6500f, -1.2f), p(11000f, 0.4f)),
            bass = 0.4f,
            punch = 0.7f,
            haerte = 0.2f,
            headroom = 4.5f,
            mbcThreshold = -9.5f,
            mbcRatio = 2.8f,
            limiterDb = -1.0f,
            subsonicHz = 38f,
        )

    val IndustrialTechno =
        player(
            BuiltInGenrePresets.IndustrialTechno,
            listOf(p(42f, 1.6f), p(90f, 0.9f), p(280f, -3.0f), p(1500f, 0.4f), p(3500f, -1.2f), p(6000f, -2.0f), p(11000f, 0.8f)),
            bass = 0.4f,
            punch = 0.5f,
            haerte = -0.5f,
            headroom = 4.5f,
            mbcThreshold = -10.5f,
            mbcRatio = 3.2f,
            limiterDb = -1.0f,
            subsonicHz = 38f,
        )

    val Schranz =
        player(
            BuiltInGenrePresets.Schranz,
            listOf(
                p(50f, 0.8f),
                p(105f, 1.4f),
                p(160f, 0.6f),
                p(300f, -3.5f),
                p(2000f, 0.4f),
                p(4000f, -1.8f),
                p(7000f, -1.5f),
                p(12000f, -0.5f),
            ),
            bass = 0.2f,
            punch = 0.8f,
            haerte = -0.5f,
            headroom = 4.5f,
            mbcThreshold = -11.0f,
            mbcRatio = 3.4f,
            limiterDb = -1.2f,
            subsonicHz = 42f,
        )

    private val byGenreId: Map<String, GenrePreset> =
        listOf(
            BuiltInGenrePresets.Terror to Terror,
            BuiltInGenrePresets.Uptempo to Uptempo,
            BuiltInGenrePresets.Gabber to Gabber,
            BuiltInGenrePresets.Early to Early,
            BuiltInGenrePresets.Frenchcore to Frenchcore,
            BuiltInGenrePresets.Dance to Dance,
            BuiltInGenrePresets.Hardtekk to Hardtekk,
            BuiltInGenrePresets.Techno to Techno,
            BuiltInGenrePresets.HardTechno to HardTechno,
            BuiltInGenrePresets.IndustrialTechno to IndustrialTechno,
            BuiltInGenrePresets.Schranz to Schranz,
            BuiltInGenrePresets.Pop to Pop,
            BuiltInGenrePresets.Rap to Rap,
        ).associate { (genre, player) -> genre.id to player }

    /** The 13 player genres, in [BuiltInGenrePresets.all] order (Flat has none). */
    val genres: List<GenrePreset> = BuiltInGenrePresets.all.mapNotNull { byGenreId[it.id] }

    /** The player version of the [BuiltInGenrePresets] genre with [genreId], or null (Flat, unknown ids). */
    fun forGenre(genreId: String): GenrePreset? = byGenreId[genreId]

    /** The five use-case variants of a player genre, in [GenreUseCase] order, with the player texts. */
    fun useCases(genre: GenrePreset): List<GenreVariant> =
        GenreUseCase.entries.map { useCase ->
            GenreVariant(GenreUseCaseTuning.applyForPlayer(genre, useCase), useCase.playerDescription, useCase)
        }

    /**
     * Roadmap 18: [preset] without the LOUD variant's extra kick, when it is a built-in player LOUD
     * variant - of a player base here or of a player-only genre ([BuiltInGenrePresets], roadmap 14).
     * Null for every other preset, which has no extra kick to take away.
     */
    fun withoutLoudExtraKick(preset: Preset): Preset? =
        loudGenreOf(preset)?.let { GenreUseCaseTuning.applyForPlayer(it, GenreUseCase.LOUD, loudExtraKick = false) }

    /** True when [preset] is a player LOUD variant whose extra kick the switch can take away. */
    fun hasLoudExtraKick(preset: Preset): Boolean = loudGenreOf(preset) != null

    // The player genre whose LOUD variant [preset] is, or null.
    private fun loudGenreOf(preset: Preset): GenrePreset? {
        if (!preset.metadata.builtIn || preset.metadata.sourceScope != PresetSourceScope.OWN_PLAYER) return null
        if (!preset.id.endsWith("_use_${GenreUseCase.LOUD.id}")) return null
        return (genres + BuiltInGenrePresets.all).firstOrNull {
            it.base.metadata.sourceScope == PresetSourceScope.OWN_PLAYER &&
                GenreUseCaseTuning.presetId(it, GenreUseCase.LOUD) == preset.id
        }
    }

    /** Every player preset: the 13 bases and their use cases (65), all [PresetSourceScope.OWN_PLAYER]. */
    val allPresets: List<Preset> = genres.flatMap { genre -> listOf(genre.base) + useCases(genre).map { it.preset } }
}
