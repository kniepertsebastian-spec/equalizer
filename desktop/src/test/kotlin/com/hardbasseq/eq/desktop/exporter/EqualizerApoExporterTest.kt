package com.hardbasseq.eq.desktop.exporter

import com.hardbasseq.eq.preset.BuiltInPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EqualizerApoExporterTest {
    @Test
    fun `flat preset produces zero preamp and zero gain filters`() {
        val config = EqualizerApoExporter.generateConfig(BuiltInPresets.Flat)

        assertTrue(config.contains("Preamp: 0.00 dB"))
        val filterLines = config.lines().filter { it.startsWith("Filter") }
        assertEquals(15, filterLines.size)
        assertTrue(filterLines.all { it.contains("Gain 0.00 dB") })
    }

    @Test
    fun `filter lines use Equalizer APO peaking syntax`() {
        val config = EqualizerApoExporter.generateConfig(BuiltInPresets.CleanPunch)

        val firstFilter = config.lines().first { it.startsWith("Filter 1:") }
        assertTrue(firstFilter.matches(Regex("""Filter 1: ON PK Fc \d+ Hz Gain -?\d+\.\d\d dB Q \d+\.\d\d""")))
    }

    @Test
    fun `preamp fully cancels the peak positive band gain`() {
        val config = EqualizerApoExporter.generateConfig(BuiltInPresets.CleanPunch)

        val preampDb =
            config.lines()
                .first { it.startsWith("Preamp:") }
                .substringAfter("Preamp:")
                .substringBefore("dB")
                .trim()
                .toFloat()
        val maxGainDb =
            config.lines()
                .filter { it.startsWith("Filter") }
                .map { line -> line.substringAfter("Gain ").substringBefore(" dB").toFloat() }
                .max()

        assertEquals(-maxGainDb, preampDb, 0.01f)
    }

    @Test
    fun `installTo writes include file and wires it into config txt exactly once`() {
        val dir = Files.createTempDirectory("eqapo-test").toFile()

        EqualizerApoExporter.installTo(dir, BuiltInPresets.DeepRumble)
        EqualizerApoExporter.installTo(dir, BuiltInPresets.KickAttack)

        val includeFile = File(dir, EqualizerApoExporter.INCLUDE_FILE_NAME)
        assertTrue(includeFile.exists())
        assertTrue(includeFile.readText().contains("Kick Attack"))

        val configTxt = File(dir, "config.txt")
        val expectedLine = "Include: ${EqualizerApoExporter.INCLUDE_FILE_NAME}"
        val includeLines = configTxt.readLines().filter { it.trim() == expectedLine }
        assertEquals(1, includeLines.size)
    }

    @Test
    fun `installTo preserves existing config txt content`() {
        val dir = Files.createTempDirectory("eqapo-test-existing").toFile()
        val configTxt = File(dir, "config.txt")
        dir.mkdirs()
        configTxt.writeText("# my own settings\nFilter 1: ON HP Fc 20 Hz\n")

        EqualizerApoExporter.installTo(dir, BuiltInPresets.Flat)

        val content = configTxt.readText()
        assertTrue(content.contains("# my own settings"))
        assertTrue(content.contains("Filter 1: ON HP Fc 20 Hz"))
        assertTrue(content.contains("Include: ${EqualizerApoExporter.INCLUDE_FILE_NAME}"))
    }
}
