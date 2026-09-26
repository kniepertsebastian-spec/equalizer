package com.hardbasseq.eq.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetIntensityResolverTest {
    @Test
    fun `moderate intensity keeps the genre base's macro and mbc values`() {
        val resolved = PresetIntensityResolver.resolve(BuiltInGenrePresets.Uptempo, PresetIntensity.MODERATE)

        val base = BuiltInGenrePresets.Uptempo.base
        assertEquals(base.macroBassDb, resolved.macroBassDb)
        assertEquals(base.macroPunchDb, resolved.macroPunchDb)
        assertEquals(base.macroHaerteDb, resolved.macroHaerteDb)
        assertEquals(base.mbcThresholdDb, resolved.mbcThresholdDb)
        assertEquals(base.mbcRatio, resolved.mbcRatio)
        assertEquals(base.targetCurve, resolved.targetCurve)
    }

    @Test
    fun `aggressive intensity adds bass and punch on top of the genre base`() {
        val base = BuiltInGenrePresets.Uptempo.base
        val resolved = PresetIntensityResolver.resolve(BuiltInGenrePresets.Uptempo, PresetIntensity.AGGRESSIVE)

        assertEquals(base.macroBassDb + 2.5f, resolved.macroBassDb, 0.001f)
        assertEquals(base.macroPunchDb + 1.5f, resolved.macroPunchDb, 0.001f)
        // Härte is untouched by intensity - the user's own definition of
        // "aggressive" was bass/punch ("Wumms"), not treble.
        assertEquals(base.macroHaerteDb, resolved.macroHaerteDb)
    }

    @Test
    fun `higher intensity tightens the bass compressor, lower intensity loosens it`() {
        val base = BuiltInGenrePresets.Uptempo.base
        val soft = PresetIntensityResolver.resolve(BuiltInGenrePresets.Uptempo, PresetIntensity.SUPER_SOFT)
        val aggressive = PresetIntensityResolver.resolve(BuiltInGenrePresets.Uptempo, PresetIntensity.VERY_AGGRESSIVE)

        // Tighter = lower threshold (catches more) and higher ratio.
        assertTrue(aggressive.mbcThresholdDb < base.mbcThresholdDb)
        assertTrue(aggressive.mbcRatio > base.mbcRatio)
        assertTrue(soft.mbcThresholdDb > base.mbcThresholdDb)
        assertTrue(soft.mbcRatio < base.mbcRatio)
    }

    @Test
    fun `mbc threshold and ratio stay within the engine's safe ranges at every intensity`() {
        for (genre in BuiltInGenrePresets.all) {
            for (intensity in PresetIntensity.entries) {
                val resolved = PresetIntensityResolver.resolve(genre, intensity)
                assertTrue("${genre.id}/${intensity.id}", resolved.mbcThresholdDb in -30f..0f)
                assertTrue("${genre.id}/${intensity.id}", resolved.mbcRatio in 1f..6f)
            }
        }
    }

    @Test
    fun `flat ignores intensity entirely`() {
        val resolvedForEachIntensity =
            PresetIntensity.entries.map { PresetIntensityResolver.resolve(BuiltInGenrePresets.Flat, it) }

        resolvedForEachIntensity.forEach { resolved ->
            assertEquals(BuiltInGenrePresets.Flat.base.macroBassDb, resolved.macroBassDb)
            assertEquals(BuiltInGenrePresets.Flat.base.mbcThresholdDb, resolved.mbcThresholdDb)
            assertEquals(BuiltInGenrePresets.Flat.base.mbcRatio, resolved.mbcRatio)
        }
    }

    @Test
    fun `resolved id round-trips back to the same genre and intensity via parse`() {
        for (genre in BuiltInGenrePresets.all) {
            for (intensity in PresetIntensity.entries) {
                val resolved = PresetIntensityResolver.resolve(genre, intensity)
                val (parsedGenre, parsedIntensity) = requireNotNull(PresetIntensityResolver.parse(resolved.id, BuiltInGenrePresets.all))
                assertEquals(genre.id, parsedGenre.id)
                assertEquals(intensity, parsedIntensity)
            }
        }
    }

    @Test
    fun `parse returns null for an id that isn't a resolved genre-intensity combination`() {
        assertNull(PresetIntensityResolver.parse("builtin_clean_punch", BuiltInGenrePresets.all))
        assertNull(PresetIntensityResolver.parse("genre_uptempo__not_a_real_intensity", BuiltInGenrePresets.all))
        assertNotNull(
            PresetIntensityResolver.parse(
                PresetIntensityResolver.resolvedId("genre_uptempo", PresetIntensity.AGGRESSIVE),
                BuiltInGenrePresets.all,
            ),
        )
    }
}
