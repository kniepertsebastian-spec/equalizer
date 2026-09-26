package com.hardbasseq.eq.desktop.exporter

import com.hardbasseq.eq.correction.BuiltInCorrectionProfiles
import com.hardbasseq.eq.correction.CorrectionProfile
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.LimiterConfig
import com.hardbasseq.eq.preset.PortableSoundProfile
import com.hardbasseq.eq.preset.TargetPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class EasyEffectsExporterTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `generated preset has 15 bell bands on both channels`() {
        val root = json.parseToJsonElement(EasyEffectsExporter.generatePresetJson(BuiltInPresets.CleanPunch)).jsonObject
        val equalizer = root["output"]!!.jsonObject["equalizer#0"]!!.jsonObject

        assertEquals(15, equalizer["num-bands"]!!.jsonPrimitive.int)
        val left = equalizer["left"]!!.jsonObject
        val right = equalizer["right"]!!.jsonObject
        assertEquals(15, left.size)
        assertEquals(15, right.size)
        assertEquals("Bell", left["band0"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val bassQ = left["band3"]!!.jsonObject["q"]!!.jsonPrimitive.double
        val midQ = left["band11"]!!.jsonObject["q"]!!.jsonPrimitive.double
        assertTrue(bassQ > midQ)
    }

    @Test
    fun `enabled dynamics follow the equalizer in processing order`() {
        val root = json.parseToJsonElement(EasyEffectsExporter.generatePresetJson(BuiltInPresets.CleanPunch)).jsonObject
        val pluginsOrder = root["output"]!!.jsonObject["plugins_order"]!!.jsonArray

        assertEquals(
            listOf("equalizer#0", "multiband_compressor#0", "limiter#0"),
            pluginsOrder.map { it.jsonPrimitive.content },
        )
        val output = root["output"]!!.jsonObject
        val compressor = output["multiband_compressor#0"]!!.jsonObject
        assertEquals(8, compressor.keys.count { it.startsWith("band") })
        val lowSplit = compressor["band1"]!!.jsonObject["split-frequency"]!!.jsonPrimitive.double
        val highSplit = compressor["band2"]!!.jsonObject["split-frequency"]!!.jsonPrimitive.double
        assertEquals(120.0, lowSplit, 0.0001)
        assertEquals(1500.0, highSplit, 0.0001)
        val disabledBand = compressor["band3"]!!.jsonObject
        val limiter = output["limiter#0"]!!.jsonObject
        assertFalse(disabledBand["enable-band"]!!.jsonPrimitive.content.toBoolean())
        assertFalse(limiter["gain-boost"]!!.jsonPrimitive.content.toBoolean())
        // LimiterConfig's default threshold (Preset.kt) is -0.3dB as of Session 24,
        // raised from -1.0dB - BuiltInPresets.CleanPunch doesn't override it.
        assertEquals(-0.3, limiter["threshold"]!!.jsonPrimitive.double, 0.0001)
    }

    @Test
    fun `flat preset has zero output gain and zero band gains`() {
        val root = json.parseToJsonElement(EasyEffectsExporter.generatePresetJson(BuiltInPresets.Flat)).jsonObject
        val output = root["output"]!!.jsonObject
        val equalizer = output["equalizer#0"]!!.jsonObject

        assertEquals(listOf("equalizer#0"), output["plugins_order"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(0.0, equalizer["output-gain"]!!.jsonPrimitive.double, 0.0001)
        val left = equalizer["left"]!!.jsonObject
        left.values.forEach { band ->
            assertEquals(0.0, band.jsonObject["gain"]!!.jsonPrimitive.double, 0.0001)
        }
    }

    @Test
    fun `phone snapshot carries correction live bands and dynamics settings`() {
        val profile =
            PortableSoundProfile(
                preset = BuiltInPresets.CleanPunch,
                correction = CorrectionProfile("headphone", "My headphones", listOf(TargetPoint(100f, -2f))),
                appliedEqCurve = listOf(TargetPoint(100f, 5f), TargetPoint(1000f, 0f)),
                macroBassDb = 1.5f,
                macroPunchDb = 2f,
                macroHaerteDb = 0.5f,
                inputGainDb = -1.8f,
                mbcEnabled = true,
                mbcThresholdDb = -9f,
                mbcRatio = 3f,
                limiter = LimiterConfig(true, -2f),
                processingEnabled = true,
            )
        val output = json.parseToJsonElement(EasyEffectsExporter.generatePresetJson(profile)).jsonObject["output"]!!.jsonObject
        val eq = output["equalizer#0"]!!.jsonObject
        val mbc = output["multiband_compressor#0"]!!.jsonObject

        assertEquals(-1.8, eq["output-gain"]!!.jsonPrimitive.double, 0.0001)
        val left = eq["left"]!!.jsonObject
        val band3 = left["band3"]!!.jsonObject
        val liveBandGain = band3["gain"]!!.jsonPrimitive.double
        assertEquals(5.0, liveBandGain, 0.0001)
        assertEquals(3.0, mbc["band0"]!!.jsonObject["ratio"]!!.jsonPrimitive.double, 0.0001)
        assertEquals(-9.0, mbc["band0"]!!.jsonObject["attack-threshold"]!!.jsonPrimitive.double, 0.0001)
        assertEquals(-2.0, output["limiter#0"]!!.jsonObject["threshold"]!!.jsonPrimitive.double, 0.0001)

        val edited = json.parseToJsonElement(EasyEffectsExporter.generatePresetJson(profile, macroBassDb = 2.5f)).jsonObject
        val editedEq = edited["output"]!!.jsonObject["equalizer#0"]!!.jsonObject
        val editedLeft = editedEq["left"]!!.jsonObject
        val editedBand = editedLeft["band3"]!!.jsonObject
        assertEquals(6.0, editedBand["gain"]!!.jsonPrimitive.double, 0.0001)
    }

    @Test
    fun `disabled phone processing produces an empty chain`() {
        val profile =
            PortableSoundProfile(
                preset = BuiltInPresets.Flat,
                correction = BuiltInCorrectionProfiles.None,
                macroBassDb = 0f,
                macroPunchDb = 0f,
                macroHaerteDb = 0f,
                inputGainDb = 0f,
                mbcEnabled = false,
                mbcThresholdDb = -6f,
                mbcRatio = 2f,
                limiter = LimiterConfig(false, 0f),
                processingEnabled = false,
            )
        val output = json.parseToJsonElement(EasyEffectsExporter.generatePresetJson(profile)).jsonObject["output"]!!.jsonObject
        assertTrue(output["plugins_order"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `installTo writes a file named after the sanitized preset name`() {
        val dir = Files.createTempDirectory("easyeffects-test").toFile()

        val file = EasyEffectsExporter.installTo(dir, BuiltInPresets.KickAttack)

        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".json"))
        assertEquals(dir, file.parentFile)
        val parsed: JsonObject = json.parseToJsonElement(file.readText()).jsonObject
        assertTrue(parsed.containsKey("output"))
    }
}
