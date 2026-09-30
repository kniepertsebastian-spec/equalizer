package com.hardbasseq.eq.preset

import com.hardbasseq.eq.context.SoundContext
import com.hardbasseq.eq.data.preset.PresetJsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInContextPresetsTest {
    @Test
    fun `every context has a default preset and both are built in`() {
        assertEquals(BuiltInContextPresets.Car, BuiltInContextPresets.defaultFor(SoundContext.CAR))
        assertEquals(BuiltInContextPresets.BluetoothSpeaker, BuiltInContextPresets.defaultFor(SoundContext.BLUETOOTH_SPEAKER))
        BuiltInContextPresets.all.forEach { preset ->
            assertTrue(preset.metadata.builtIn)
            assertTrue("${preset.id} missing from BuiltInPresets.all", BuiltInPresets.all.any { it.id == preset.id })
        }
    }

    @Test
    fun `preset ids stay unique across all built-ins`() {
        val ids = BuiltInPresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `context presets switch on loudness, subsonic and virtual bass`() {
        BuiltInContextPresets.all.forEach { preset ->
            assertTrue("${preset.id} loudness", preset.loudnessMaxBoostDb > 0f)
            assertTrue("${preset.id} subsonic", preset.subsonicCutoffHz > 0f)
            assertTrue("${preset.id} virtual bass", preset.virtualBassMix in 0.01f..1f)
        }
    }

    @Test
    fun `curves are sorted and within the importable gain range`() {
        BuiltInContextPresets.all.forEach { preset ->
            val freqs = preset.targetCurve.map { it.frequencyHz }
            assertEquals(freqs.sorted(), freqs)
            preset.targetCurve.forEach { assertTrue(it.gainDb in -24f..24f) }
        }
    }

    @Test
    fun `bluetooth speaker protects the driver harder than the car preset`() {
        assertTrue(BuiltInContextPresets.BluetoothSpeaker.subsonicCutoffHz > BuiltInContextPresets.Car.subsonicCutoffHz)
        assertTrue(BuiltInContextPresets.BluetoothSpeaker.limiter.thresholdDb < BuiltInContextPresets.Car.limiter.thresholdDb)
    }

    @Test
    fun `context fields survive a json round trip`() {
        val json = PresetJsonSerializer.exportToJson(BuiltInContextPresets.Car)
        val restored = PresetJsonSerializer.importFromJson(json).getOrThrow()
        assertEquals(BuiltInContextPresets.Car, restored)
    }

    @Test
    fun `presets saved before the context fields existed decode with them switched off`() {
        val legacyJson =
            """{"id":"old","name":"Old","targetCurve":[{"frequencyHz":60.0,"gainDb":1.0}]}"""
        val restored = PresetJsonSerializer.importFromJson(legacyJson).getOrThrow()
        assertEquals(0f, restored.loudnessMaxBoostDb, 0f)
        assertEquals(0f, restored.subsonicCutoffHz, 0f)
        assertEquals(0f, restored.virtualBassMix, 0f)
    }
}
