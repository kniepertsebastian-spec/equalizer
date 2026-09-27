package com.hardbasseq.eq.preset

import org.junit.Assert.assertEquals
import org.junit.Test

class PresetDesignTest {
    @Test
    fun everyLegacyBuiltInPresetSelectsItsRequestedDesign() {
        val expected =
            mapOf(
                "builtin_clean_punch" to PresetDesign.UPTEMPO_HARDCORE,
                "builtin_deep_rumble" to PresetDesign.UPTEMPO_HARDCORE,
                "builtin_kick_attack" to PresetDesign.UPTEMPO_HARDCORE,
                "builtin_final_smash" to PresetDesign.UPTEMPO_HARDCORE,
                "builtin_maximum_distortion" to PresetDesign.TERRORCORE,
                "builtin_raw_power" to PresetDesign.GABBER,
                "builtin_fast_attack" to PresetDesign.GABBER,
                "builtin_balanced" to PresetDesign.HARD_DANCE,
                "builtin_flat" to PresetDesign.FLAT,
            )

        val actual = BuiltInPresets.all.filter { it.id in expected.keys }.associate { it.id to PresetDesign.forPreset(it) }
        assertEquals(expected, actual)
    }

    // Genre x intensity redesign: design is a genre-level property, PresetIntensity
    // deltas never touch id/name/metadata.genre, so every intensity of a genre must
    // resolve to the same design as its MODERATE (base) variant.
    @Test
    fun everyGenrePresetSelectsItsExpectedDesignAtEveryIntensity() {
        val expectedByGenreId =
            mapOf(
                "genre_terror" to PresetDesign.TERRORCORE,
                "genre_uptempo" to PresetDesign.UPTEMPO_HARDCORE,
                "genre_gabber" to PresetDesign.GABBER,
                "genre_early" to PresetDesign.GABBER,
                "genre_frenchcore" to PresetDesign.GABBER,
                "genre_dance" to PresetDesign.HARD_DANCE,
                "genre_pop" to PresetDesign.FLAT,
                "genre_flat" to PresetDesign.FLAT,
            )

        for (genre in BuiltInGenrePresets.all) {
            val expected = requireNotNull(expectedByGenreId[genre.id]) { "no expectation for ${genre.id}" }
            val intensities = if (genre.allowsIntensity) PresetIntensity.entries else listOf(PresetIntensity.MODERATE)
            for (intensity in intensities) {
                val resolved = PresetIntensityResolver.resolve(genre, intensity)
                assertEquals("${genre.id}/${intensity.id}", expected, PresetDesign.forPreset(resolved))
            }
        }
    }

    @Test
    fun duplicatedPresetKeepsItsDesignAfterRename() {
        val copy =
            BuiltInPresets.MaximumDistortion.copy(
                id = "custom_terror",
                name = "My profile",
                metadata = BuiltInPresets.MaximumDistortion.metadata.copy(builtIn = false),
            )

        assertEquals(PresetDesign.TERRORCORE, PresetDesign.forPreset(copy))
    }
}
