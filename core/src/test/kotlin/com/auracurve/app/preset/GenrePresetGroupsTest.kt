package com.auracurve.app.preset

import com.auracurve.app.dsp.EqualizerInterpolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class GenrePresetGroupsTest {
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

    private val newVariants = BuiltInGenrePresets.all.flatMap { BuiltInGenreVariants.variantsOf(it) }

    @Test
    fun `every genre has a group with base, character variants and five use cases`() {
        assertEquals(BuiltInGenrePresets.all.map { it.id }, GenrePresetGroups.all.map { it.genre.id })
        GenrePresetGroups.all.forEach { group ->
            val useCases = group.entries.mapNotNull { it.useCase }
            assertEquals(group.genre.id, GenreUseCase.entries, useCases)
            assertEquals(
                PresetIntensityResolver.resolvedId(group.genre.id, PresetIntensity.MODERATE),
                group.entries
                    .first()
                    .preset.id,
            )
            val expectedStyles = if (group.genre == BuiltInGenrePresets.Flat) 0 else 2
            val expectedLegacy = if (group.genre == BuiltInGenrePresets.Uptempo) 3 else 0
            assertEquals(group.genre.id, 1 + expectedLegacy + expectedStyles + 5, group.entries.size)
        }
    }

    @Test
    fun `every entry explains when it fits best`() {
        GenrePresetGroups.all.flatMap { it.entries }.forEach { entry ->
            assertTrue("${entry.preset.id} has no description", entry.description.length > 40)
        }
    }

    @Test
    fun `ids are unique, resolvable by id and never mistaken for an intensity combination`() {
        val ids = BuiltInPresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        newVariants.forEach { variant ->
            assertTrue("${variant.preset.id} missing from BuiltInPresets.all", variant.preset.id in ids)
            assertNull(PresetIntensityResolver.parse(variant.preset.id, BuiltInGenrePresets.all))
            assertTrue(variant.preset.metadata.builtIn)
        }
    }

    @Test
    fun `genreOf finds the genre of every listed preset`() {
        GenrePresetGroups.all.forEach { group ->
            group.entries.forEach { assertEquals(group.genre.id, GenrePresetGroups.genreOf(it.preset.id)?.id) }
        }
        assertNull(GenrePresetGroups.genreOf(BuiltInContextPresets.Car.id))
        assertNull(GenrePresetGroups.genreOf("custom_123"))
    }

    @Test
    fun `no new variant boosts 9 dB or more, and curves stay sorted`() {
        newVariants.forEach { variant ->
            val preset = variant.preset
            val freqs = preset.targetCurve.map { it.frequencyHz }
            assertEquals(preset.id, freqs.sorted(), freqs)
            val strongest = grid.maxOf { gainAt(preset, it) }
            assertTrue("${preset.id} boosts ${"%.1f".format(strongest)} dB", strongest < PresetUsageAdvisor.VERY_STRONG_BOOST_DB)
        }
    }

    @Test
    fun `with the matching listening mode on top a use case stays inside the 15 dB pre-EQ range`() {
        val overlay =
            mapOf(
                GenreUseCase.CAR to BuiltInContextPresets.Car,
                GenreUseCase.BLUETOOTH_SPEAKER to BuiltInContextPresets.BluetoothSpeaker,
                GenreUseCase.HEADPHONES to BuiltInContextPresets.InEar,
            )
        newVariants.filter { it.useCase in overlay }.forEach { variant ->
            val context = overlay.getValue(variant.useCase!!).targetCurve
            grid.forEach { hz ->
                val total = gainAt(variant.preset, hz) + EqualizerInterpolator.interpolateFrequency(hz, context)
                assertTrue("${variant.preset.id} at $hz Hz: $total dB", total in -15f..15f)
            }
        }
    }

    @Test
    fun `use cases do what their description says`() {
        BuiltInGenrePresets.all.forEach { genre ->
            val base = genre.base
            val byUseCase = BuiltInGenreVariants.useCases(genre).associate { it.useCase!! to it.preset }

            val car = byUseCase.getValue(GenreUseCase.CAR)
            assertTrue(genre.id, gainAt(car, 40f) < gainAt(base, 40f))
            assertTrue(genre.id, gainAt(car, 3000f) > gainAt(base, 3000f))
            assertTrue(car.loudnessMaxBoostDb > 0f)

            val speaker = byUseCase.getValue(GenreUseCase.BLUETOOTH_SPEAKER)
            assertTrue(speaker.targetCurve.none { it.frequencyHz < 75f && it.gainDb > 0f })
            assertTrue(speaker.subsonicCutoffHz >= 60f)
            assertTrue(speaker.virtualBassMix > 0f)
            assertTrue(speaker.limiter.enabled && speaker.limiter.thresholdDb <= -1f)

            val headphones = byUseCase.getValue(GenreUseCase.HEADPHONES)
            assertTrue(genre.id, gainAt(headphones, 6500f) < gainAt(base, 6500f))
            assertTrue(headphones.macroHaerteDb <= 0f)

            val loud = byUseCase.getValue(GenreUseCase.LOUD)
            assertTrue(loud.macroBassDb < base.macroBassDb)
            assertTrue(loud.limiter.enabled && loud.mbcEnabled)
            assertTrue(loud.mbcThresholdDb < base.mbcThresholdDb || base.mbcThresholdDb <= -28f)

            val quiet = byUseCase.getValue(GenreUseCase.QUIET)
            assertTrue(quiet.loudnessMaxBoostDb > 0f)
            assertNotNull(GenreUseCase.fromId(GenreUseCase.QUIET.id))
        }
    }
}
