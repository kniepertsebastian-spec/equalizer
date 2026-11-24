package com.auracurve.app.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetIntensityBoostTest {
    private val uptempo = BuiltInGenrePresets.Uptempo

    @Test
    fun `the switch adds the former aggressive step`() {
        val base = PresetIntensityResolver.resolve(uptempo, PresetIntensity.MODERATE)
        val aggressive = PresetIntensityResolver.resolve(uptempo, PresetIntensity.AGGRESSIVE)
        val boosted = PresetIntensityBoost.apply(base)
        assertEquals(aggressive.macroBassDb, boosted.macroBassDb)
        assertEquals(aggressive.macroPunchDb, boosted.macroPunchDb)
        assertEquals(aggressive.mbcThresholdDb, boosted.mbcThresholdDb)
        assertEquals(aggressive.mbcRatio, boosted.mbcRatio)
        assertEquals(base.id, boosted.id)
    }

    @Test
    fun `built-in player presets get half the step`() {
        val step = PresetIntensity.AGGRESSIVE
        val players =
            BuiltInPlayerPresets.genres.flatMap { genre ->
                listOf(genre.base) +
                    BuiltInPlayerPresets.useCases(genre).map { it.preset }
            }
        players.forEach { player ->
            val boosted = PresetIntensityBoost.apply(player)
            assertEquals(player.id, 0.5f, PresetIntensityBoost.shareFor(player))
            assertEquals(player.id, player.macroBassDb + step.macroBassDeltaDb / 2f, boosted.macroBassDb, 1e-4f)
            assertEquals(player.id, player.macroPunchDb + step.macroPunchDeltaDb / 2f, boosted.macroPunchDb, 1e-4f)
            assertEquals(player.id, player.mbcThresholdDb + step.mbcThresholdDeltaDb / 2f, boosted.mbcThresholdDb, 1e-4f)
            assertEquals(player.id, player.mbcRatio + step.mbcRatioDelta / 2f, boosted.mbcRatio, 1e-4f)
            assertEquals(player.id, boosted.macroBassDb - player.macroBassDb, PresetIntensityBoost.macroBassDeltaDb(player), 1e-4f)
            assertEquals(player.id, boosted.macroPunchDb - player.macroPunchDb, PresetIntensityBoost.macroPunchDeltaDb(player), 1e-4f)
        }
        // Presets for other apps keep the full step, neutral ones none.
        val system = PresetIntensityResolver.resolve(uptempo, PresetIntensity.MODERATE)
        assertEquals(1f, PresetIntensityBoost.shareFor(system))
        assertEquals(PresetIntensityBoost.macroBassDeltaDb, PresetIntensityBoost.macroBassDeltaDb(system))
        assertEquals(0f, PresetIntensityBoost.shareFor(BuiltInPresets.Flat))
        assertEquals(0f, PresetIntensityBoost.macroPunchDeltaDb(BuiltInPresets.Flat))
    }

    @Test
    fun `neutral presets stay untouched`() {
        assertEquals(BuiltInPresets.Flat, PresetIntensityBoost.apply(BuiltInPresets.Flat))
        val flatCar = BuiltInGenreVariants.useCases(BuiltInGenrePresets.Flat).first().preset
        assertEquals(flatCar, PresetIntensityBoost.apply(flatCar))
    }

    @Test
    fun `old steps map onto the base and the switch`() {
        fun legacy(step: PresetIntensity) = PresetIntensityBoost.legacy(PresetIntensityResolver.resolvedId(uptempo.id, step))!!
        val baseId = PresetIntensityResolver.resolvedId(uptempo.id, PresetIntensity.MODERATE)
        PresetIntensity.entries.forEach { assertEquals(baseId, legacy(it).base.id) }
        assertEquals(false, legacy(PresetIntensity.SUPER_SOFT).moreIntensity)
        assertEquals(false, legacy(PresetIntensity.SOFT).moreIntensity)
        assertNull(legacy(PresetIntensity.MODERATE).moreIntensity)
        assertEquals(true, legacy(PresetIntensity.AGGRESSIVE).moreIntensity)
        assertEquals(true, legacy(PresetIntensity.VERY_AGGRESSIVE).moreIntensity)
        assertEquals(false, PresetIntensityBoost.legacy("genre_flat__very_aggressive")!!.moreIntensity)
        assertNull(PresetIntensityBoost.legacy(BuiltInPresets.KickAttack.id))
    }

    @Test
    fun `only the retired steps count as retired`() {
        assertTrue(PresetIntensityBoost.isRetiredStep("genre_rap__soft"))
        assertFalse(PresetIntensityBoost.isRetiredStep("genre_rap__moderate"))
        assertFalse(PresetIntensityBoost.isRetiredStep("genre_rap_use_car"))
    }
}
