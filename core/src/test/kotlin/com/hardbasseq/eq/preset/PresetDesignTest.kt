package com.hardbasseq.eq.preset

import org.junit.Assert.assertEquals
import org.junit.Test

class PresetDesignTest {
    @Test
    fun everyBuiltInPresetSelectsItsRequestedDesign() {
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

        assertEquals(expected, BuiltInPresets.all.associate { it.id to PresetDesign.forPreset(it) })
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
