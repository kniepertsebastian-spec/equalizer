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
