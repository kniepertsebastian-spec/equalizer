package com.auracurve.app.preset

import com.auracurve.app.dsp.EqualizerInterpolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

// Roadmap 14: Hardtekk, Techno, Hard Techno, Industrial Techno and Schranz.
class ElectronicGenrePresetsTest {
    private val genres =
        listOf(
            BuiltInGenrePresets.Hardtekk,
            BuiltInGenrePresets.Techno,
            BuiltInGenrePresets.HardTechno,
            BuiltInGenrePresets.IndustrialTechno,
            BuiltInGenrePresets.Schranz,
        )

    private val grid = generateSequence(20f) { it * 2f.pow(1f / 12f) }.takeWhile { it <= 20_000f }.toList()

    private fun strongestBoost(preset: Preset): Float {
        val sorted = preset.targetCurve.sortedBy { it.frequencyHz }
        return grid.maxOf {
            EqualizerInterpolator.resolveGainAtFrequency(it, sorted, preset.macroBassDb, preset.macroPunchDb, preset.macroHaerteDb)
        }
    }

    private fun allPresetsOf(genre: GenrePreset): List<Preset> =
        PresetIntensityResolver.resolve(genre, PresetIntensity.MODERATE) +
            BuiltInGenreVariants.variantsOf(genre).map { it.preset }

    private operator fun Preset.plus(others: List<Preset>) = listOf(this) + others

    @Test
    fun `the five genres have the ids from the roadmap`() {
        assertEquals(
            listOf("genre_hardtekk", "genre_techno", "genre_hard_techno", "genre_industrial_techno", "genre_schranz"),
            genres.map { it.id },
        )
        genres.forEach { assertTrue(it.id, it.allowsIntensity) }
    }

    @Test
    fun `every new genre is findable in all, BuiltInPresets and the groups`() {
        val builtInIds = BuiltInPresets.all.map { it.id }.toSet()
        genres.forEach { genre ->
            assertTrue(genre.id, genre in BuiltInGenrePresets.all)
            assertTrue(genre.id, GenrePresetGroups.all.any { it.genre.id == genre.id })
            PresetIntensity.entries.forEach { intensity ->
                assertTrue(genre.id, PresetIntensityResolver.resolvedId(genre.id, intensity) in builtInIds)
            }
            BuiltInGenreVariants.variantsOf(genre).forEach { assertTrue(it.preset.id, it.preset.id in builtInIds) }
        }
    }

    @Test
    fun `electronic genres stay together and flat stays last`() {
        val ids = BuiltInGenrePresets.all.map { it.id }
        val start = ids.indexOf(BuiltInGenrePresets.Hardtekk.id)
        assertEquals(genres.map { it.id }, ids.subList(start, start + genres.size))
        assertEquals(BuiltInGenrePresets.Dance.id, ids[start - 1])
        assertEquals(BuiltInGenrePresets.Flat.id, ids.last())
    }

    @Test
    fun `each genre has two character variants with their roadmap keys`() {
        val expected =
            mapOf(
                "genre_hardtekk" to listOf("hollow", "dark"),
                "genre_techno" to listOf("rumble", "peak"),
                "genre_hard_techno" to listOf("warehouse", "dry"),
                "genre_industrial_techno" to listOf("heavy", "tamed"),
                "genre_schranz" to listOf("grinder", "session"),
            )
        genres.forEach { genre ->
            val styles = BuiltInGenreVariants.styles.getValue(genre.id)
            assertEquals(expected.getValue(genre.id).map { "${genre.id}_style_$it" }, styles.map { it.preset.id })
            styles.forEach { assertTrue(it.preset.name, it.preset.name.startsWith("${genre.displayName} – ")) }
        }
    }

    @Test
    fun `base, character variants and use cases boost less than 9 dB with the macros`() {
        genres.flatMap(::allPresetsOf).forEach { preset ->
            val strongest = strongestBoost(preset)
            assertTrue("${preset.id} boosts ${"%.1f".format(strongest)} dB", strongest < PresetUsageAdvisor.VERY_STRONG_BOOST_DB)
        }
    }

    // Headroom in line with the strongest boost, as for the existing genres: enough to keep the
    // boost from leaning on the limiter, never more than the boost itself plus a safety margin.
    @Test
    fun `requested headroom matches the strongest boost of every base and character variant`() {
        genres.forEach { genre ->
            val presets = listOf(genre.base) + BuiltInGenreVariants.styles.getValue(genre.id).map { it.preset }
            presets.forEach { preset ->
                val strongest = strongestBoost(preset)
                assertTrue(
                    "${preset.id}: headroom ${preset.requestedHeadroomDb} vs boost $strongest",
                    preset.requestedHeadroomDb in (strongest * 0.5f)..(strongest + 1f),
                )
            }
        }
    }

    @Test
    fun `bases follow the roadmap tuning table`() {
        data class Row(
            val macros: Triple<Float, Float, Float>,
            val headroom: Float,
            val mbc: Pair<Float, Float>,
            val limiter: Float,
            val subsonic: Float,
        )
        val table =
            mapOf(
                BuiltInGenrePresets.Hardtekk to Row(Triple(0.8f, 2.5f, 0.8f), 4.5f, -7.0f to 3.0f, -0.3f, 30f),
                BuiltInGenrePresets.Techno to Row(Triple(1.2f, 1.2f, 0.2f), 3.5f, -8.0f to 2.2f, -0.3f, 25f),
                BuiltInGenrePresets.HardTechno to Row(Triple(1.5f, 2.0f, 0.5f), 4.5f, -7.5f to 2.8f, -0.3f, 28f),
                BuiltInGenrePresets.IndustrialTechno to Row(Triple(1.5f, 1.5f, -0.5f), 5.0f, -9.0f to 3.2f, -0.5f, 28f),
                BuiltInGenrePresets.Schranz to Row(Triple(0.8f, 2.5f, -0.5f), 4.5f, -9.5f to 3.4f, -0.5f, 32f),
            )
        table.forEach { (genre, row) ->
            val base = genre.base
            assertEquals(genre.id, row.macros, Triple(base.macroBassDb, base.macroPunchDb, base.macroHaerteDb))
            assertEquals(genre.id, row.headroom, base.requestedHeadroomDb, 0f)
            assertEquals(genre.id, row.mbc, base.mbcThresholdDb to base.mbcRatio)
            assertTrue(genre.id, base.mbcEnabled && base.limiter.enabled)
            assertEquals(genre.id, row.limiter, base.limiter.thresholdDb, 0f)
            assertEquals(genre.id, row.subsonic, base.subsonicCutoffHz, 0f)
            assertTrue(genre.id, base.metadata.builtIn)
            val freqs = base.targetCurve.map { it.frequencyHz }
            assertEquals(genre.id, freqs.sorted(), freqs)
        }
    }

    @Test
    fun `the distorted genres tame their highs rather than lift them`() {
        listOf(BuiltInGenrePresets.IndustrialTechno, BuiltInGenrePresets.Schranz).forEach { genre ->
            (listOf(genre.base) + BuiltInGenreVariants.styles.getValue(genre.id).map { it.preset }).forEach { preset ->
                val sorted = preset.targetCurve.sortedBy { it.frequencyHz }
                listOf(4000f, 6000f).forEach { hz ->
                    assertTrue("${preset.id} at $hz Hz", EqualizerInterpolator.interpolateFrequency(hz, sorted) < 0f)
                }
                assertTrue(preset.id, preset.macroHaerteDb <= 0f)
            }
        }
    }

    @Test
    fun `no new genre falls back to the flat design`() {
        genres.flatMap(::allPresetsOf).forEach { preset ->
            assertNotEquals(preset.id, PresetDesign.FLAT, PresetDesign.forPreset(preset))
        }
    }
}
