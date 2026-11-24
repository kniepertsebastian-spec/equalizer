package com.auracurve.app.data.preset

import com.auracurve.app.preset.BuiltInPlayerPresets
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.PresetSourceScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetJsonSerializerTest {
    @Test
    fun exportAndImport_roundTripPreservesPreset() {
        val original = BuiltInPresets.CleanPunch
        val json = PresetJsonSerializer.exportToJson(original)

        val result = PresetJsonSerializer.importFromJson(json)

        assertTrue(result.isSuccess)
        val imported = result.getOrThrow()
        assertEquals(original.name, imported.name)
        assertEquals(original.targetCurve.size, imported.targetCurve.size)
    }

    @Test
    fun importFromJson_rejectsInvalidGain() {
        val invalidJson =
            """
            {
              "schemaVersion": 1,
              "name": "Extreme Gain Test",
              "targetCurve": [
                { "frequencyHz": 55.0, "gainDb": 100.0 }
              ]
            }
            """.trimIndent()

        val result = PresetJsonSerializer.importFromJson(invalidJson)

        assertTrue(result.isFailure)
    }

    @Test
    fun importFromJson_rejectsNegativeFrequency() {
        val invalidJson =
            """
            {
              "schemaVersion": 1,
              "name": "Negative Freq Test",
              "targetCurve": [
                { "frequencyHz": -50.0, "gainDb": 3.0 }
              ]
            }
            """.trimIndent()

        val result = PresetJsonSerializer.importFromJson(invalidJson)

        assertTrue(result.isFailure)
    }

    // Roadmap 15: PresetSourceScope.

    @Test
    fun importFromJson_fileFromBeforeSourceScope_decodesAsAll() {
        val withMetadata =
            """
            {
              "id": "old",
              "schemaVersion": 1,
              "name": "Saved in 2025",
              "targetCurve": [ { "frequencyHz": 55.0, "gainDb": 3.0 } ],
              "metadata": { "genre": "uptempo-hardcore", "builtIn": false }
            }
            """.trimIndent()
        val withoutMetadata =
            """
            { "id": "older", "name": "No metadata", "targetCurve": [ { "frequencyHz": 100.0, "gainDb": 1.0 } ] }
            """.trimIndent()

        listOf(withMetadata, withoutMetadata).forEach { json ->
            val imported = PresetJsonSerializer.importFromJson(json).getOrThrow()
            assertEquals(PresetSourceScope.ALL, imported.metadata.sourceScope)
        }
    }

    @Test
    fun exportAndImport_roundTripKeepsEverySourceScope() {
        val player = BuiltInPlayerPresets.Uptempo.base
        val external = BuiltInPresets.CleanPunch
        val all = BuiltInPresets.Flat
        assertEquals(PresetSourceScope.OWN_PLAYER, player.metadata.sourceScope)
        assertEquals(PresetSourceScope.EXTERNAL_APPS, external.metadata.sourceScope)
        assertEquals(PresetSourceScope.ALL, all.metadata.sourceScope)

        listOf(player, external, all).forEach { preset ->
            val imported = PresetJsonSerializer.importFromJson(PresetJsonSerializer.exportToJson(preset)).getOrThrow()
            assertEquals(preset.id, preset, imported)
        }
    }

    @Test
    fun export_writesTheScopeByName() {
        val json = PresetJsonSerializer.exportToJson(BuiltInPlayerPresets.Uptempo.base)

        assertTrue(json, json.contains("\"sourceScope\": \"OWN_PLAYER\""))
        assertFalse(PresetJsonSerializer.exportToJson(BuiltInPresets.Flat).contains("sourceScope"))
    }

    @Test
    fun importFromJson_unknownScopeFromANewerVersion_fallsBackToAll() {
        val json =
            """
            {
              "id": "future",
              "name": "From a newer app",
              "targetCurve": [ { "frequencyHz": 55.0, "gainDb": 3.0 } ],
              "metadata": { "genre": "techno", "builtIn": false, "sourceScope": "SOME_FUTURE_SOURCE" }
            }
            """.trimIndent()

        val imported = PresetJsonSerializer.importFromJson(json).getOrThrow()

        assertEquals(PresetSourceScope.ALL, imported.metadata.sourceScope)
        assertEquals("techno", imported.metadata.genre)
    }
}
