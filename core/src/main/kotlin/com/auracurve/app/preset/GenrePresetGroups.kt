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
            BuiltInGenrePresets.Hardtekk.id to
                "The reference hardtekk sound: the bouncy kick body at 95 Hz in front, the boxy mids scooped " +
                "deep, a touch of click. Works on most systems; the intensity steps add or take away bass.",
            BuiltInGenrePresets.Techno.id to
                "The reference techno sound: deep, rolling sub with clean hi-hats and a light compressor. " +
                "An all-rounder; the intensity steps add or take away bass.",
            BuiltInGenrePresets.HardTechno.id to
                "The reference hard techno sound: sub pressure and a firm kick, the 280 Hz mud scooped. Works " +
                "on most systems; the intensity steps add or take away bass.",
            BuiltInGenrePresets.IndustrialTechno.id to
                "The reference industrial techno sound: dark, heavy sub, the metallic 3.5–6 kHz distortion " +
                "tamed. A good start on most systems; the intensity steps add or take away bass.",
            BuiltInGenrePresets.Schranz.id to
                "The reference schranz sound: driving 105 Hz punch, a deep 300 Hz scoop and the harsh loops " +
                "tamed above 4 kHz. Works on most systems; the intensity steps add or take away bass.",
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

    /** The genre list as it has always been: every [BuiltInGenrePresets] genre, whatever its source scope. */
    val all: List<GenrePresetGroup> = BuiltInGenrePresets.all.map(::groupOf)

    /** The groups for the given genres, in their order (the screen passes its own genre list). */
    fun forGenres(genres: List<GenrePreset>): List<GenrePresetGroup> =
        genres.map { genre -> all.firstOrNull { it.genre.id == genre.id } ?: groupOf(genre) }

    // --- Roadmap 15: presets per playback source -------------------------------------------------

    private fun playerDescription(genre: GenrePreset): String {
        val base = genre.base
        return "The ${genre.displayName} sound for the built-in player, tuned to spare speakers: less deep " +
            "sub, a subsonic filter at ${base.subsonicCutoffHz.toInt()} Hz and a limiter at " +
            "${base.limiter.thresholdDb} dB. The use cases below adapt it to loud, quiet, in-ear, car or " +
            "Bluetooth playback."
    }

    // Player group: the speaker-safe base, the genre's character variants that are themselves tuned
    // for the player (the player-only genres of roadmap 14), then the player use cases. The
    // system-EQ character variants and legacy presets stay in the system-EQ list.
    private fun playerGroupOf(
        genre: GenrePreset,
        player: GenrePreset,
    ): GenrePresetGroup {
        val styles =
            BuiltInGenreVariants.styles[genre.id]
                .orEmpty()
                .filter { it.preset.metadata.sourceScope == PresetSourceScope.OWN_PLAYER }
        val base = GenreVariant(player.base, playerDescription(player))
        return GenrePresetGroup(player, listOf(base) + styles + BuiltInPlayerPresets.useCases(player))
    }

    /** One group per player genre ([BuiltInPlayerPresets.genres]), in genre order. */
    val playerGroups: List<GenrePresetGroup> =
        BuiltInGenrePresets.all.mapNotNull { genre -> BuiltInPlayerPresets.forGenre(genre.id)?.let { playerGroupOf(genre, it) } }

    /**
     * The genre list for one playback source, in genre order:
     * - [PresetSourceScope.OWN_PLAYER]: the player groups, plus every genre that fits all sources and
     *   has no player version (Flat).
     * - [PresetSourceScope.EXTERNAL_APPS]: the system-EQ genres and Flat; the player-only genres are left out.
     * - [PresetSourceScope.ALL]: everything, the system-EQ groups ([all]) followed by the player groups.
     * Within a group only entries that fit the source are kept.
     */
    fun forSource(source: PresetSourceScope): List<GenrePresetGroup> {
        val groups =
            when (source) {
                PresetSourceScope.ALL -> all + playerGroups
                PresetSourceScope.EXTERNAL_APPS ->
                    all.filter {
                        it.genre.base.metadata.sourceScope
                            .fits(source)
                    }
                PresetSourceScope.OWN_PLAYER ->
                    all.mapNotNull { group ->
                        playerGroups.firstOrNull { it.genre.id == BuiltInPlayerPresets.idFor(group.genre.id) }
                            ?: group.takeIf { it.genre.base.metadata.sourceScope == PresetSourceScope.ALL }
                    }
            }
        return groups
            .map { group ->
                group.copy(
                    entries =
                        group.entries.filter {
                            it.preset.metadata.sourceScope
                                .fits(source)
                        },
                )
            }.filter { it.entries.isNotEmpty() }
    }

    /**
     * The genre whose list holds this preset id, or null (custom presets, context presets). For a
     * player preset this is the player genre ([BuiltInPlayerPresets.genres]).
     */
    fun genreOf(presetId: String): GenrePreset? =
        (all + playerGroups).firstOrNull { group -> group.entries.any { it.preset.id == presetId } }?.genre

    // The older hand-authored presets, by the genre whose base reuses or extends their tuning.
    private val legacyGenreIds: Map<String, String> =
        mapOf(
            BuiltInPresets.CleanPunch.id to BuiltInGenrePresets.Uptempo.id,
            BuiltInPresets.DeepRumble.id to BuiltInGenrePresets.Uptempo.id,
            BuiltInPresets.KickAttack.id to BuiltInGenrePresets.Uptempo.id,
            BuiltInPresets.FinalSmash.id to BuiltInGenrePresets.Uptempo.id,
            BuiltInPresets.Balanced.id to BuiltInGenrePresets.Dance.id,
            BuiltInPresets.RawPower.id to BuiltInGenrePresets.Early.id,
            BuiltInPresets.FastAttack.id to BuiltInGenrePresets.Frenchcore.id,
            BuiltInPresets.MaximumDistortion.id to BuiltInGenrePresets.Terror.id,
        )

    /** Which genre ([BuiltInGenrePresets] id) and use case a built-in preset id stands for, in either source. */
    private data class Slot(
        val genreId: String,
        val useCase: GenreUseCase?,
    )

    private fun slotOf(presetId: String): Slot? {
        val id = presetId.removePrefix(BuiltInPlayerPresets.ID_PREFIX)
        BuiltInGenrePresets.all.forEach { genre ->
            if (id == genre.id) return Slot(genre.id, null)
            GenreUseCase.entries.forEach { useCase ->
                if (id == "${genre.id}_use_${useCase.id}") return Slot(genre.id, useCase)
            }
        }
        if (id != presetId) return null
        // System-EQ side only: an intensity step, a character variant or an older hand-authored preset.
        PresetIntensityResolver.parse(presetId, BuiltInGenrePresets.all)?.let { (genre, _) -> return Slot(genre.id, null) }
        legacyGenreIds[presetId]?.let { return Slot(it, null) }
        return all
            .firstOrNull { group -> group.entries.any { it.preset.id == presetId } }
            ?.let { Slot(it.genre.id, null) }
    }

    private fun slotPresetId(
        slot: Slot,
        target: PresetSourceScope,
    ): String? =
        when (target) {
            PresetSourceScope.OWN_PLAYER -> {
                val player = BuiltInPlayerPresets.forGenre(slot.genreId)
                player?.let { if (slot.useCase == null) it.id else GenreUseCaseTuning.presetId(it, slot.useCase) }
            }
            PresetSourceScope.EXTERNAL_APPS -> {
                val genre = BuiltInGenrePresets.all.firstOrNull { it.id == slot.genreId }
                genre?.let {
                    if (slot.useCase == null) {
                        PresetIntensityResolver.resolvedId(it.id, PresetIntensity.MODERATE)
                    } else {
                        GenreUseCaseTuning.presetId(it, slot.useCase)
                    }
                }
            }
            PresetSourceScope.ALL -> null
        }

    private val builtInScopes: Map<String, PresetSourceScope> by lazy {
        BuiltInPresets.all.associate { it.id to it.metadata.sourceScope }
    }

    /**
     * The preset to play instead of [presetId] when playback switches to [target] (roadmap 16):
     * - a preset that already fits [target] (including custom presets and Flat) stays as it is;
     * - a genre base or intensity step maps onto the other source's base (`genre_uptempo__moderate`
     *   ↔ `player_genre_uptempo`), a use case onto the same use case (`genre_uptempo_use_loud` ↔
     *   `player_genre_uptempo_use_loud`), a character variant or older hand-authored preset onto the
     *   player base of its genre;
     * - null when the other source has no such preset (the player-only genres have no system-EQ
     *   version) or the id is unknown.
     *
     * [scope] is the preset's own source scope; leave it out for built-ins, pass it for a custom
     * preset (custom presets are not in [BuiltInPresets.all], so their scope is not known here).
     */
    fun counterpartPresetId(
        presetId: String,
        target: PresetSourceScope,
        scope: PresetSourceScope = builtInScopes[presetId] ?: PresetSourceScope.ALL,
    ): String? {
        if (scope.fits(target)) return presetId
        val slot = slotOf(presetId) ?: return null
        val candidate = slotPresetId(slot, target) ?: return null
        return candidate.takeIf { builtInScopes[it]?.fits(target) == true }
    }
}
