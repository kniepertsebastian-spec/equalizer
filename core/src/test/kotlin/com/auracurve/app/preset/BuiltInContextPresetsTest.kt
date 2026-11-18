package com.auracurve.app.preset

import com.auracurve.app.context.SoundContext
import com.auracurve.app.data.preset.PresetJsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInContextPresetsTest {
    @Test
    fun `every context has a default preset and all are built in`() {
        assertEquals(BuiltInContextPresets.Car, BuiltInContextPresets.defaultFor(SoundContext.CAR))
        assertEquals(BuiltInContextPresets.BluetoothSpeaker, BuiltInContextPresets.defaultFor(SoundContext.BLUETOOTH_SPEAKER))
        assertEquals(BuiltInContextPresets.Soundbar, BuiltInContextPresets.defaultFor(SoundContext.SOUNDBAR))
        assertEquals(BuiltInContextPresets.InEar, BuiltInContextPresets.defaultFor(SoundContext.IN_EAR))
        assertEquals(SoundContext.entries.size, BuiltInContextPresets.all.size)
        BuiltInContextPresets.all.forEach { preset ->
            assertTrue(preset.metadata.builtIn)
            assertTrue("${preset.id} missing from BuiltInPresets.all", BuiltInPresets.all.any { it.id == preset.id })
        }
    }

    @Test
    fun `contextOf maps a context preset back to its mode and other presets to none`() {
        SoundContext.entries.forEach { context ->
            assertEquals(context, BuiltInContextPresets.contextOf(BuiltInContextPresets.defaultFor(context)))
        }
        assertNull(BuiltInContextPresets.contextOf(BuiltInPresets.CleanPunch))
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
    fun `small speaker puts its bass where the driver can play it`() {
        val speaker = BuiltInContextPresets.BluetoothSpeaker
        val peak = speaker.targetCurve.maxBy { it.gainDb }
        // The kick lift sits above the subsonic corner, never below it.
        assertTrue(peak.frequencyHz > speaker.subsonicCutoffHz)
        assertTrue(speaker.targetCurve.none { it.frequencyHz < speaker.subsonicCutoffHz && it.gainDb > 0f })
        // Synthesized harmonics carry the bass the driver cannot.
        assertTrue(speaker.virtualBassMix > BuiltInContextPresets.Soundbar.virtualBassMix)
    }

    @Test
    fun `in-ears and soundbars push real low end, small speakers do not`() {
        fun lowGain(preset: Preset) = preset.targetCurve.filter { it.frequencyHz <= 60f }.maxOfOrNull { it.gainDb } ?: 0f
        assertTrue(lowGain(BuiltInContextPresets.InEar) >= 3f)
        assertTrue(lowGain(BuiltInContextPresets.Soundbar) >= 3f)
        assertEquals(0f, lowGain(BuiltInContextPresets.BluetoothSpeaker), 0f)
        assertTrue(BuiltInContextPresets.InEar.subsonicCutoffHz < BuiltInContextPresets.Soundbar.subsonicCutoffHz)
    }

    @Test
    fun `in-ear tames sibilance`() {
        val sibilance = BuiltInContextPresets.InEar.targetCurve.filter { it.frequencyHz in 5000f..8000f }
        assertTrue(sibilance.isNotEmpty() && sibilance.all { it.gainDb < 0f })
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
