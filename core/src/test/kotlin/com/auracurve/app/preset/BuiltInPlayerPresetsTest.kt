package com.auracurve.app.preset

import com.auracurve.app.dsp.EqualizerInterpolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

// Roadmap 15: source-scoped presets, the speaker-safe player presets and their use cases.
class BuiltInPlayerPresetsTest {
    private val grid = generateSequence(20f) { it * 2f.pow(1f / 12f) }.takeWhile { it <= 20_000f }.toList()

    private fun gainAt(
        preset: Preset,
        hz: Float,
    ) = EqualizerInterpolator.resolveGainAtFrequency(
        hz,
        preset.targetCurve.sortedBy { it.frequencyHz },
        preset.macroBassDb,
        preset.macroPunchDb,
        preset.macroHaerteDb,
    )

    private val newGenres =
        listOf(
            BuiltInGenrePresets.Hardtekk,
            BuiltInGenrePresets.Techno,
            BuiltInGenrePresets.HardTechno,
            BuiltInGenrePresets.IndustrialTechno,
            BuiltInGenrePresets.Schranz,
        )
    private val olderGenres = BuiltInGenrePresets.all - newGenres - BuiltInGenrePresets.Flat

    private val ownPlayerPresets = BuiltInPresets.all.filter { it.metadata.sourceScope == PresetSourceScope.OWN_PLAYER }

    private fun useCaseOf(preset: Preset): GenreUseCase? = GenreUseCase.entries.firstOrNull { preset.id.endsWith("_use_${it.id}") }

    // --- data model --------------------------------------------------------------------------

    @Test
    fun `scope fits its own source, ALL fits every source`() {
        PresetSourceScope.entries.forEach { scope ->
            assertTrue(PresetSourceScope.ALL.fits(scope))
            assertTrue(scope.fits(PresetSourceScope.ALL))
            assertTrue(scope.fits(scope))
        }
        assertTrue(!PresetSourceScope.OWN_PLAYER.fits(PresetSourceScope.EXTERNAL_APPS))
        assertTrue(!PresetSourceScope.EXTERNAL_APPS.fits(PresetSourceScope.OWN_PLAYER))
        assertEquals(PresetSourceScope.ALL, PresetMetadata().sourceScope)
    }

    @Test
    fun `system-EQ presets are EXTERNAL_APPS, Flat and the listening modes fit every source`() {
        val external =
            olderGenres.flatMap { genre ->
                PresetIntensity.entries.map { PresetIntensityResolver.resolve(genre, it) } +
                    BuiltInGenreVariants.variantsOf(genre).map { it.preset }
            } +
                listOf(
                    BuiltInPresets.CleanPunch,
                    BuiltInPresets.DeepRumble,
                    BuiltInPresets.KickAttack,
                    BuiltInPresets.Balanced,
                    BuiltInPresets.RawPower,
                    BuiltInPresets.FastAttack,
                    BuiltInPresets.MaximumDistortion,
                    BuiltInPresets.FinalSmash,
                )
        external.forEach { assertEquals(it.id, PresetSourceScope.EXTERNAL_APPS, it.metadata.sourceScope) }

        val everywhere =
            listOf(BuiltInPresets.Flat) +
                PresetIntensity.entries.map { PresetIntensityResolver.resolve(BuiltInGenrePresets.Flat, it) } +
                BuiltInGenreVariants.variantsOf(BuiltInGenrePresets.Flat).map { it.preset } +
                BuiltInContextPresets.all
        everywhere.forEach { assertEquals(it.id, PresetSourceScope.ALL, it.metadata.sourceScope) }
    }

    @Test
    fun `the five roadmap 14 genres are player-only, with every intensity, character variant and use case`() {
        newGenres.forEach { genre ->
            val family =
                PresetIntensity.entries.map { PresetIntensityResolver.resolve(genre, it) } +
                    BuiltInGenreVariants.variantsOf(genre).map { it.preset }
            family.forEach { assertEquals(it.id, PresetSourceScope.OWN_PLAYER, it.metadata.sourceScope) }
        }
    }

    // --- player presets ----------------------------------------------------------------------

    @Test
    fun `no duplicate ids in BuiltInPresets all`() {
        val ids = BuiltInPresets.all.map { it.id }
        assertEquals(
            ids
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .toString(),
            ids.size,
            ids.toSet().size,
        )
    }

    @Test
    fun `all 13 genres have a player base and all five use cases, resolvable by id`() {
        val ids = BuiltInPresets.all.map { it.id }.toSet()
        val expectedGenres = BuiltInGenrePresets.all - BuiltInGenrePresets.Flat
        assertEquals(13, BuiltInPlayerPresets.genres.size)
        assertEquals(expectedGenres.map { "player_${it.id}" }, BuiltInPlayerPresets.genres.map { it.id })
        BuiltInPlayerPresets.genres.forEach { genre ->
            assertEquals(genre.id, genre.base.id)
            assertTrue(genre.id, genre.base.id in ids)
            val useCases = BuiltInPlayerPresets.useCases(genre)
            assertEquals(GenreUseCase.entries, useCases.map { it.useCase })
            useCases.forEach { variant ->
                assertEquals("${genre.id}_use_${variant.useCase!!.id}", variant.preset.id)
                assertTrue(variant.preset.id, variant.preset.id in ids)
                assertTrue(variant.description.length > 40)
            }
        }
        assertEquals(13 * 6, BuiltInPlayerPresets.allPresets.size)
        assertNull(BuiltInPlayerPresets.forGenre(BuiltInGenrePresets.Flat.id))
    }

    @Test
    fun `player presets are built-in OWN_PLAYER presets with the genre's look`() {
        BuiltInPlayerPresets.genres.forEach { genre ->
            val systemEq = BuiltInGenrePresets.all.first { BuiltInPlayerPresets.idFor(it.id) == genre.id }
            assertEquals(systemEq.displayName, genre.displayName)
            (listOf(genre.base) + BuiltInPlayerPresets.useCases(genre).map { it.preset }).forEach { preset ->
                assertEquals(preset.id, PresetSourceScope.OWN_PLAYER, preset.metadata.sourceScope)
                assertTrue(preset.id, preset.metadata.builtIn)
                assertEquals(preset.id, systemEq.base.metadata.genre, preset.metadata.genre)
                assertEquals(preset.id, PresetDesign.forPreset(systemEq.base), PresetDesign.forPreset(preset))
                assertNull(preset.id, PresetIntensityResolver.parse(preset.id, BuiltInGenrePresets.all))
            }
        }
    }

    @Test
    fun `player bases follow the roadmap tuning table`() {
        // Roadmap 15 table A with the roadmap 18 kick and Punch values.
        data class Row(
            val curve: List<Pair<Float, Float>>,
            val macros: Triple<Float, Float, Float>,
            val headroom: Float,
            val mbc: Pair<Float, Float>,
            val limiter: Float,
            val subsonic: Float,
        )
        val table =
            mapOf(
                BuiltInPlayerPresets.Uptempo to
                    Row(
                        listOf(55f to 1.2f, 105f to 1.1f, 280f to -2.0f, 3500f to 0.8f, 8500f to -1.0f),
                        Triple(0.3f, 0.7f, 0.3f),
                        4.0f,
                        -9.0f to 2.6f,
                        -1.0f,
                        38f,
                    ),
                BuiltInPlayerPresets.Terror to
                    Row(
                        listOf(50f to 1.5f, 90f to 0.8f, 300f to -3.5f, 2500f to -1.2f, 6000f to -2.0f, 12000f to -1.0f),
                        Triple(0.3f, 0.3f, -1.0f),
                        5.0f,
                        -11.0f to 3.2f,
                        -1.2f,
                        42f,
                    ),
                BuiltInPlayerPresets.Pop to
                    Row(
                        listOf(60f to 0.8f, 150f to 0.2f, 1000f to 0.0f, 3000f to 0.2f, 10000f to 0.8f),
                        Triple(0.0f, 0.1f, 0.1f),
                        2.0f,
                        -9.0f to 1.8f,
                        -1.0f,
                        30f,
                    ),
                BuiltInPlayerPresets.Schranz to
                    Row(
                        listOf(
                            50f to 0.8f,
                            105f to 1.4f,
                            160f to 0.6f,
                            300f to -3.5f,
                            2000f to 0.4f,
                            4000f to -1.8f,
                            7000f to -1.5f,
                            12000f to -0.5f,
                        ),
                        Triple(0.2f, 0.8f, -0.5f),
                        4.5f,
                        -11.0f to 3.4f,
                        -1.2f,
                        42f,
                    ),
            )
        table.forEach { (genre, row) ->
            val base = genre.base
            assertEquals(genre.id, row.curve, base.targetCurve.map { it.frequencyHz to it.gainDb })
            assertEquals(genre.id, row.macros, Triple(base.macroBassDb, base.macroPunchDb, base.macroHaerteDb))
            assertEquals(genre.id, row.headroom, base.requestedHeadroomDb, 0f)
            assertEquals(genre.id, row.mbc, base.mbcThresholdDb to base.mbcRatio)
            assertEquals(genre.id, row.limiter, base.limiter.thresholdDb, 0f)
            assertEquals(genre.id, row.subsonic, base.subsonicCutoffHz, 0f)
        }
        // The headroom and limiter columns, for every genre.
        val headroom =
            mapOf(
                "uptempo" to 4.0f,
                "gabber" to 4.0f,
                "frenchcore" to 4.5f,
                "early" to 4.0f,
                "terror" to 5.0f,
                "dance" to 3.0f,
                "rap" to 3.5f,
                "pop" to 2.0f,
                "hardtekk" to 4.0f,
                "techno" to 3.5f,
                "hard_techno" to 4.5f,
                "industrial_techno" to 4.5f,
                "schranz" to 4.5f,
            )
        BuiltInPlayerPresets.genres.forEach { genre ->
            assertEquals(genre.id, headroom.getValue(genre.id.removePrefix("player_genre_")), genre.base.requestedHeadroomDb, 0f)
        }
    }

    @Test
    fun `speaker presets have a subsonic filter, a defensive limiter and no virtual bass`() {
        // Every OWN_PLAYER preset that may play on speakers: in-ears and quiet listening move no
        // cone far enough to need it.
        val speakers = ownPlayerPresets.filter { useCaseOf(it) != GenreUseCase.HEADPHONES && useCaseOf(it) != GenreUseCase.QUIET }
        assertTrue(speakers.size > 13 * 4)
        speakers.forEach { preset ->
            val useCase = useCaseOf(preset)
            assertTrue(preset.id, preset.subsonicCutoffHz >= 30f)
            when (useCase) {
                null ->
                    assertTrue(
                        "${preset.id}: ${preset.subsonicCutoffHz} Hz",
                        preset.subsonicCutoffHz >= 35f || preset.metadata.genre == "pop",
                    )
                GenreUseCase.LOUD -> assertTrue(preset.id, preset.subsonicCutoffHz >= 42f)
                else -> assertTrue(preset.id, preset.subsonicCutoffHz >= 35f)
            }
            assertTrue(preset.id, preset.limiter.enabled && preset.limiter.thresholdDb <= -1.0f)
            assertTrue(preset.id, preset.mbcEnabled)
            if (useCase == GenreUseCase.BLUETOOTH_SPEAKER) {
                assertTrue(preset.id, preset.virtualBassMix > 0f)
            } else {
                assertEquals(preset.id, 0f, preset.virtualBassMix, 0f)
            }
        }
        // Headphones and quiet listening still keep a subsonic filter and no virtual bass.
        (ownPlayerPresets - speakers.toSet()).forEach { preset ->
            assertTrue(preset.id, preset.subsonicCutoffHz >= 20f)
            assertEquals(preset.id, 0f, preset.virtualBassMix, 0f)
            assertTrue(preset.id, preset.limiter.enabled && preset.limiter.thresholdDb < 0f)
        }
    }

    @Test
    fun `player bases boost the deep sub less than their system-EQ counterpart`() {
        olderGenres.forEach { genre ->
            val player = BuiltInPlayerPresets.forGenre(genre.id)!!.base
            listOf(40f, 50f, 60f).forEach { hz ->
                assertTrue("${genre.id} at $hz Hz", gainAt(player, hz) < gainAt(genre.base, hz))
            }
            assertTrue(genre.id, player.limiter.thresholdDb < genre.base.limiter.thresholdDb)
            assertTrue(genre.id, player.subsonicCutoffHz > genre.base.subsonicCutoffHz)
        }
    }

    @Test
    fun `player bases knock less in the kick range than in roadmap 15`() {
        // Roadmap 18: kick points (80-170 Hz) -25 %, Punch -35 %, the 250-300 Hz cut kept.
        val roadmap15 =
            mapOf(
                "uptempo" to (1.5f to 1.0f),
                "gabber" to (2.0f to 1.2f),
                "frenchcore" to (1.8f to 1.5f),
                "early" to (1.2f to 1.0f),
                "terror" to (1.0f to 0.5f),
                "dance" to (0.8f to 0.4f),
                "rap" to (1.0f to 0.3f),
                "pop" to (0.3f to 0.2f),
                "hardtekk" to (2.0f to 1.2f),
                "techno" to (1.2f to 0.6f),
                "hard_techno" to (1.6f to 1.0f),
                "industrial_techno" to (1.2f to 0.8f),
                "schranz" to (1.8f to 1.2f),
            )
        BuiltInPlayerPresets.genres.forEach { genre ->
            val base = genre.base
            val (oldKick, oldPunch) = roadmap15.getValue(genre.id.removePrefix("player_genre_"))
            val kick = base.targetCurve.filter { it.frequencyHz in 80f..170f && it.gainDb > 0f }.maxOf { it.gainDb }
            assertEquals(genre.id, oldKick * 0.75f, kick, 0.051f)
            assertEquals(genre.id, oldPunch * 0.65f, base.macroPunchDb, 0.051f)
            // Kick over 1 kHz: up to 4.2 dB in roadmap 15 (Schranz, Hardtekk, Terror), now below 3.8 dB.
            val kickOverMids = grid.filter { it in 80f..170f }.maxOf { gainAt(base, it) } - gainAt(base, 1000f)
            assertTrue("${genre.id}: ${"%.1f".format(kickOverMids)} dB", kickOverMids < 3.8f)
        }
    }

    @Test
    fun `no player preset boosts 9 dB or more with the macros`() {
        // The retired intensity steps (Aggressive, Very Aggressive) are only kept resolvable for old
        // profiles and play as the base plus the "More intensity" switch (PresetIntensityBoost.legacy).
        val offered = ownPlayerPresets.filterNot { PresetIntensityBoost.isRetiredStep(it.id) }
        assertTrue(offered.size >= 13 * 6)
        offered.forEach { preset ->
            val freqs = preset.targetCurve.map { it.frequencyHz }
            assertEquals(preset.id, freqs.sorted(), freqs)
            val strongest = grid.maxOf { gainAt(preset, it) }
            assertTrue("${preset.id} boosts ${"%.1f".format(strongest)} dB", strongest < PresetUsageAdvisor.VERY_STRONG_BOOST_DB)
        }
    }

    // --- player use-case equivalents --------------------------------------------------------

    @Test
    fun `player use cases follow the roadmap table`() {
        BuiltInPlayerPresets.genres.forEach { genre ->
            val base = genre.base
            val byUseCase = BuiltInPlayerPresets.useCases(genre).associate { it.useCase!! to it.preset }

            // Kräftiger Bass bei lauter Musik.
            val loud = byUseCase.getValue(GenreUseCase.LOUD)
            assertEquals(genre.id, base.macroBassDb - 0.3f, loud.macroBassDb, 1e-4f)
            assertEquals(genre.id, base.macroPunchDb + 0.2f, loud.macroPunchDb, 1e-4f)
            assertEquals(genre.id, base.macroHaerteDb - 0.6f, loud.macroHaerteDb, 1e-4f)
            assertEquals(genre.id, base.mbcThresholdDb - 2f, loud.mbcThresholdDb, 1e-4f)
            assertEquals(genre.id, base.mbcRatio + 0.5f, loud.mbcRatio, 1e-4f)
            assertEquals(-1.2f, loud.limiter.thresholdDb, 0f)
            assertEquals(maxOf(base.subsonicCutoffHz, 42f), loud.subsonicCutoffHz, 0f)
            assertEquals(0f, loud.loudnessMaxBoostDb, 0f)
            assertTrue(genre.id, gainAt(loud, 35f) < gainAt(base, 35f))
            assertTrue(genre.id, gainAt(loud, 4500f) < gainAt(base, 4500f))

            // Moderat bei leiser Musik.
            val quiet = byUseCase.getValue(GenreUseCase.QUIET)
            assertEquals(
                Triple(base.macroBassDb, base.macroPunchDb, base.macroHaerteDb),
                Triple(quiet.macroBassDb, quiet.macroPunchDb, quiet.macroHaerteDb),
            )
            assertEquals(genre.id, base.mbcThresholdDb + 1f, quiet.mbcThresholdDb, 1e-4f)
            assertEquals(genre.id, base.mbcRatio - 0.2f, quiet.mbcRatio, 1e-4f)
            assertEquals(-0.8f, quiet.limiter.thresholdDb, 0f)
            assertEquals(30f, quiet.subsonicCutoffHz, 0f)
            assertEquals(7.5f, quiet.loudnessMaxBoostDb, 0f)
            assertTrue(genre.id, gainAt(quiet, 12000f) > gainAt(base, 12000f))

            // Besser bei In-Ears.
            val inEar = byUseCase.getValue(GenreUseCase.HEADPHONES)
            assertEquals(genre.id, base.macroBassDb + 0.3f, inEar.macroBassDb, 1e-4f)
            assertEquals(genre.id, base.macroPunchDb, inEar.macroPunchDb, 0f)
            assertTrue(genre.id, inEar.macroHaerteDb <= 0f)
            assertEquals(genre.id, base.mbcThresholdDb + 1.5f, inEar.mbcThresholdDb, 1e-4f)
            assertEquals(genre.id, base.mbcRatio - 0.3f, inEar.mbcRatio, 1e-4f)
            assertEquals(-0.6f, inEar.limiter.thresholdDb, 0f)
            assertEquals(22f, inEar.subsonicCutoffHz, 0f)
            assertTrue(genre.id, gainAt(inEar, 30f) > gainAt(base, 30f))
            assertTrue(genre.id, gainAt(inEar, 6500f) < gainAt(base, 6500f))

            // Car: the sub below 60 Hz is capped, the cabin adds it.
            val car = byUseCase.getValue(GenreUseCase.CAR)
            assertTrue(genre.id, car.targetCurve.none { it.frequencyHz < 60f && it.gainDb > 0f })
            assertEquals(genre.id, base.macroBassDb - 0.3f, car.macroBassDb, 1e-4f)
            assertEquals(genre.id, base.mbcThresholdDb - 1f, car.mbcThresholdDb, 1e-4f)
            assertEquals(genre.id, base.mbcRatio + 0.3f, car.mbcRatio, 1e-4f)
            assertEquals(-1.0f, car.limiter.thresholdDb, 0f)
            assertEquals(36f, car.subsonicCutoffHz, 0f)
            assertEquals(6f, car.loudnessMaxBoostDb, 0f)
            assertTrue(genre.id, gainAt(car, 3000f) > gainAt(base, 3000f))

            // Small Bluetooth speaker.
            val speaker = byUseCase.getValue(GenreUseCase.BLUETOOTH_SPEAKER)
            assertTrue(genre.id, speaker.targetCurve.none { it.frequencyHz < 75f && it.gainDb > 0f })
            assertEquals(genre.id, base.macroBassDb * 0.25f, speaker.macroBassDb, 1e-4f)
            assertEquals(genre.id, base.mbcThresholdDb - 2f, speaker.mbcThresholdDb, 1e-4f)
            assertEquals(genre.id, base.mbcRatio + 0.5f, speaker.mbcRatio, 1e-4f)
            assertEquals(-1.2f, speaker.limiter.thresholdDb, 0f)
            assertEquals(65f, speaker.subsonicCutoffHz, 0f)
            assertEquals(6f, speaker.loudnessMaxBoostDb, 0f)
            assertEquals(0.45f, speaker.virtualBassMix, 0f)
            val kick = EqualizerInterpolator.interpolateFrequency(115f, speaker.targetCurve)
            assertTrue(genre.id, kick > EqualizerInterpolator.interpolateFrequency(115f, base.targetCurve))
            val box = EqualizerInterpolator.interpolateFrequency(400f, speaker.targetCurve)
            assertTrue(genre.id, box < EqualizerInterpolator.interpolateFrequency(400f, base.targetCurve))
        }
    }

    @Test
    fun `the LOUD extra kick can be taken away, and only from player LOUD variants`() {
        // Roadmap 18: the switch takes the +0.5 dB at 95 Hz and the +0.2 Punch away, nothing else.
        val playerOnlyLoud = BuiltInGenreVariants.useCases(BuiltInGenrePresets.Hardtekk).map { it.preset }
        val louds =
            BuiltInPlayerPresets.genres.map { genre ->
                BuiltInPlayerPresets.useCases(genre).first { it.useCase == GenreUseCase.LOUD }.preset
            } + playerOnlyLoud.filter { useCaseOf(it) == GenreUseCase.LOUD }
        assertEquals(14, louds.size)
        louds.forEach { loud ->
            assertTrue(loud.id, BuiltInPlayerPresets.hasLoudExtraKick(loud))
            val calm = requireNotNull(BuiltInPlayerPresets.withoutLoudExtraKick(loud)) { loud.id }
            assertEquals(loud.id, calm.id)
            assertEquals(loud.name, calm.name)
            assertEquals(loud.metadata, calm.metadata)
            assertEquals(loud.id, loud.macroPunchDb - GenreUseCaseTuning.PLAYER_LOUD_EXTRA_PUNCH, calm.macroPunchDb, 1e-4f)
            // A genre point near 95 Hz (Terror's 90 Hz) takes the delta where it sits, so a little less.
            val kickDelta = gainAt(loud, 95f) - gainAt(calm, 95f)
            val punch = GenreUseCaseTuning.PLAYER_LOUD_EXTRA_PUNCH
            assertTrue(
                "${loud.id}: $kickDelta",
                kickDelta in (punch + 0.3f)..(punch + GenreUseCaseTuning.PLAYER_LOUD_EXTRA_KICK_DB + 0.11f),
            )
            listOf(35f, 4500f, 10000f).forEach { hz -> assertEquals("${loud.id} at $hz Hz", gainAt(loud, hz), gainAt(calm, hz), 0.051f) }
            assertEquals(loud.copy(targetCurve = calm.targetCurve, macroPunchDb = calm.macroPunchDb), calm)
        }
        // Nothing else has an extra kick to take away.
        val others =
            ownPlayerPresets.filter { useCaseOf(it) != GenreUseCase.LOUD } +
                BuiltInGenreVariants.useCases(BuiltInGenrePresets.Uptempo).map { it.preset } +
                listOf(BuiltInPresets.Flat, louds.first().copy(metadata = louds.first().metadata.copy(builtIn = false)))
        others.forEach { assertNull(it.id, BuiltInPlayerPresets.withoutLoudExtraKick(it)) }
    }

    @Test
    fun `the source scope picks the use-case tuning`() {
        val player = BuiltInPlayerPresets.Uptempo
        GenreUseCase.entries.forEach { useCase ->
            assertEquals(GenreUseCaseTuning.applyForPlayer(player, useCase), GenreUseCaseTuning.apply(player, useCase))
        }
        // The system-EQ Uptempo keeps its own tuning (e.g. no subsonic floor of 42 Hz when loud).
        val systemLoud = GenreUseCaseTuning.apply(BuiltInGenrePresets.Uptempo, GenreUseCase.LOUD)
        assertEquals(25f, systemLoud.subsonicCutoffHz, 0f)
        assertEquals(PresetSourceScope.EXTERNAL_APPS, systemLoud.metadata.sourceScope)
    }

    // --- groups per source and counterparts --------------------------------------------------

    @Test
    fun `forSource lists the player groups for the player and hides the player-only genres from other apps`() {
        val player = GenrePresetGroups.forSource(PresetSourceScope.OWN_PLAYER)
        assertEquals(BuiltInPlayerPresets.genres.map { it.id } + BuiltInGenrePresets.Flat.id, player.map { it.genre.id })
        player.flatMap { it.entries }.forEach { entry ->
            assertTrue(
                entry.preset.id,
                entry.preset.metadata.sourceScope
                    .fits(PresetSourceScope.OWN_PLAYER),
            )
            assertTrue(entry.preset.id, entry.description.length > 40)
        }
        player.dropLast(1).forEach { group ->
            assertEquals(
                group.genre.base.id,
                group.entries
                    .first()
                    .preset.id,
            )
            assertEquals(group.genre.id, GenreUseCase.entries, group.entries.mapNotNull { it.useCase })
        }
        // The player-only genres keep their roadmap 14 character variants in the player list.
        val hardtekk = player.first { it.genre.id == BuiltInPlayerPresets.Hardtekk.id }
        assertEquals(1 + 2 + 5, hardtekk.entries.size)

        val external = GenrePresetGroups.forSource(PresetSourceScope.EXTERNAL_APPS)
        assertEquals((olderGenres + BuiltInGenrePresets.Flat).map { it.id }.sorted(), external.map { it.genre.id }.sorted())
        external.flatMap { it.entries }.forEach { entry ->
            assertTrue(
                entry.preset.id,
                entry.preset.metadata.sourceScope
                    .fits(PresetSourceScope.EXTERNAL_APPS),
            )
        }
        assertEquals(GenrePresetGroups.all.filter { it.genre !in newGenres }, external)

        val everything = GenrePresetGroups.forSource(PresetSourceScope.ALL)
        assertEquals(GenrePresetGroups.all.size + GenrePresetGroups.playerGroups.size, everything.size)
    }

    @Test
    fun `genreOf finds the player genre of every player preset`() {
        GenrePresetGroups.playerGroups.forEach { group ->
            group.entries.filter { it.preset.id.startsWith(BuiltInPlayerPresets.ID_PREFIX) }.forEach {
                assertEquals(group.genre.id, GenrePresetGroups.genreOf(it.preset.id)?.id)
            }
        }
    }

    @Test
    fun `every shared genre and use case maps exactly onto its counterpart in the other source`() {
        olderGenres.forEach { genre ->
            val player = BuiltInPlayerPresets.forGenre(genre.id)!!
            val systemBase = PresetIntensityResolver.resolvedId(genre.id, PresetIntensity.MODERATE)
            assertEquals(player.id, GenrePresetGroups.counterpartPresetId(systemBase, PresetSourceScope.OWN_PLAYER))
            assertEquals(systemBase, GenrePresetGroups.counterpartPresetId(player.id, PresetSourceScope.EXTERNAL_APPS))
            GenreUseCase.entries.forEach { useCase ->
                val systemId = GenreUseCaseTuning.presetId(genre, useCase)
                val playerId = GenreUseCaseTuning.presetId(player, useCase)
                assertEquals("player_${genre.id}_use_${useCase.id}", playerId)
                assertEquals(playerId, GenrePresetGroups.counterpartPresetId(systemId, PresetSourceScope.OWN_PLAYER))
                assertEquals(systemId, GenrePresetGroups.counterpartPresetId(playerId, PresetSourceScope.EXTERNAL_APPS))
            }
        }
        assertEquals(
            "player_genre_uptempo",
            GenrePresetGroups.counterpartPresetId("genre_uptempo__moderate", PresetSourceScope.OWN_PLAYER),
        )
        assertEquals(
            "genre_uptempo_use_loud",
            GenrePresetGroups.counterpartPresetId("player_genre_uptempo_use_loud", PresetSourceScope.EXTERNAL_APPS),
        )
    }

    @Test
    fun `presets that already fit stay, the rest fall back sensibly`() {
        val ids = BuiltInPresets.all.map { it.id }.toSet()
        // A preset that fits the target stays as it is.
        BuiltInPresets.all.forEach { preset ->
            PresetSourceScope.entries.filter { preset.metadata.sourceScope.fits(it) }.forEach { target ->
                assertEquals(preset.id, GenrePresetGroups.counterpartPresetId(preset.id, target))
            }
        }
        // Every system-EQ built-in has a player counterpart (base, use case or genre base).
        BuiltInPresets.all.filter { it.metadata.sourceScope == PresetSourceScope.EXTERNAL_APPS }.forEach { preset ->
            val counterpart = GenrePresetGroups.counterpartPresetId(preset.id, PresetSourceScope.OWN_PLAYER)
            assertNotNull(preset.id, counterpart)
            assertTrue(preset.id, counterpart in ids)
            assertTrue(preset.id, counterpart!!.startsWith(BuiltInPlayerPresets.ID_PREFIX))
        }
        assertEquals(
            BuiltInPlayerPresets.Uptempo.id,
            GenrePresetGroups.counterpartPresetId(BuiltInPresets.DeepRumble.id, PresetSourceScope.OWN_PLAYER),
        )
        assertEquals(
            BuiltInPlayerPresets.Terror.id,
            GenrePresetGroups.counterpartPresetId("genre_terror_style_tamed", PresetSourceScope.OWN_PLAYER),
        )
        assertEquals(
            BuiltInPlayerPresets.Early.id,
            GenrePresetGroups.counterpartPresetId("genre_early__very_aggressive", PresetSourceScope.OWN_PLAYER),
        )
        // The player-only genres have no system-EQ version.
        newGenres.forEach { genre ->
            val player = BuiltInPlayerPresets.forGenre(genre.id)!!
            assertNull(GenrePresetGroups.counterpartPresetId(player.id, PresetSourceScope.EXTERNAL_APPS))
            assertNull(GenrePresetGroups.counterpartPresetId("${genre.id}_use_loud", PresetSourceScope.EXTERNAL_APPS))
        }
        // Custom presets: kept when they fit, no counterpart when they do not.
        assertEquals("custom_1", GenrePresetGroups.counterpartPresetId("custom_1", PresetSourceScope.OWN_PLAYER))
        assertNull(
            GenrePresetGroups.counterpartPresetId("custom_1", PresetSourceScope.OWN_PLAYER, scope = PresetSourceScope.EXTERNAL_APPS),
        )
        assertNull(GenrePresetGroups.counterpartPresetId("player_unknown", PresetSourceScope.EXTERNAL_APPS, PresetSourceScope.OWN_PLAYER))
    }
}
