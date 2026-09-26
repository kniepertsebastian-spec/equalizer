package com.hardbasseq.eq.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInGenrePresetsTest {
    @Test
    fun `has exactly the eight requested genres`() {
        assertEquals(8, BuiltInGenrePresets.all.size)
        assertEquals(
            setOf("Terror", "Uptempo", "Gabber", "Early", "Frenchcore", "Dance", "Pop", "Flat"),
            BuiltInGenrePresets.all.map { it.displayName }.toSet(),
        )
    }

    @Test
    fun `only flat opts out of the intensity axis`() {
        val genresWithoutIntensity = BuiltInGenrePresets.all.filterNot { it.allowsIntensity }
        assertEquals(listOf("Flat"), genresWithoutIntensity.map { it.displayName })
    }

    @Test
    fun `resolved presets are 5 per intensity-enabled genre plus 1 for flat, all with unique ids`() {
        val expectedCount = 7 * PresetIntensity.entries.size + 1
        assertEquals(expectedCount, BuiltInGenrePresets.allResolvedPresets.size)

        val ids = BuiltInGenrePresets.allResolvedPresets.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `builtInPresets exposes every resolved genre-intensity combination by id`() {
        val allIds = BuiltInPresets.all.map { it.id }.toSet()
        BuiltInGenrePresets.allResolvedPresets.forEach { resolved ->
            assertTrue("missing ${resolved.id}", allIds.contains(resolved.id))
        }
    }

    @Test
    fun `no id collides between the legacy hand-authored presets and the new genre combinations`() {
        val ids = BuiltInPresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
