package com.hardbasseq.eq.desktop.exporter

import com.hardbasseq.eq.preset.BuiltInPresets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun `plugins order references the equalizer instance`() {
        val root = json.parseToJsonElement(EasyEffectsExporter.generatePresetJson(BuiltInPresets.Flat)).jsonObject
        val pluginsOrder = root["output"]!!.jsonObject["plugins_order"]!!.jsonArray

        assertEquals(1, pluginsOrder.size)
        assertEquals("equalizer#0", pluginsOrder[0].jsonPrimitive.content)
    }

    @Test
    fun `flat preset has zero output gain and zero band gains`() {
        val root = json.parseToJsonElement(EasyEffectsExporter.generatePresetJson(BuiltInPresets.Flat)).jsonObject
        val equalizer = root["output"]!!.jsonObject["equalizer#0"]!!.jsonObject

        assertEquals(0.0, equalizer["output-gain"]!!.jsonPrimitive.double, 0.0001)
        val left = equalizer["left"]!!.jsonObject
        left.values.forEach { band ->
            assertEquals(0.0, band.jsonObject["gain"]!!.jsonPrimitive.double, 0.0001)
        }
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
