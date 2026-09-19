package com.hardbasseq.eq.data.preset

import com.hardbasseq.eq.preset.BuiltInPresets
import org.junit.Assert.assertEquals
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
}
