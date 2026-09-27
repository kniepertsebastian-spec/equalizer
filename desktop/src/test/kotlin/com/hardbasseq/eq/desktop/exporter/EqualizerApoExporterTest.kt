package com.hardbasseq.eq.desktop.exporter

import com.hardbasseq.eq.preset.BuiltInPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

private fun db(value: Float): String = String.format(Locale.ROOT, "%.2f", value)

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
            config
                .lines()
                .first { it.startsWith("Preamp:") }
                .substringAfter("Preamp:")
                .substringBefore("dB")
                .trim()
                .toFloat()
        val maxGainDb =
            config
                .lines()
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

    // --- Chat feature: experimental Windows dynamics (ReaComp reference) ---

    @Test
    fun `dynamics config carries the preset's own mbc and limiter values`() {
        val config = EqualizerApoExporter.generateDynamicsConfig(BuiltInPresets.CleanPunch)

        assertTrue(config.contains("Threshold: ${db(BuiltInPresets.CleanPunch.mbcThresholdDb)} dB"))
        assertTrue(config.contains("Ratio: ${db(BuiltInPresets.CleanPunch.mbcRatio)}:1"))
        assertTrue(config.contains("Threshold: ${db(BuiltInPresets.CleanPunch.limiter.thresholdDb)} dB"))
    }

    @Test
    fun `every VST automation line in the dynamics config is commented out`() {
        val config = EqualizerApoExporter.generateDynamicsConfig(BuiltInPresets.CleanPunch)

        val automationLines = config.lines().filter { it.contains("VST:") || it.contains("VSTPlugin:") }
        assertTrue(automationLines.isNotEmpty())
        assertTrue(automationLines.all { it.trim().startsWith("#") })
    }

    @Test
    fun `flat preset (mbc and limiter disabled) produces no automation lines at all`() {
        val config = EqualizerApoExporter.generateDynamicsConfig(BuiltInPresets.Flat)

        assertTrue(config.lines().none { it.contains("VST:") || it.contains("VSTPlugin:") })
    }

    @Test
    fun `installDynamicsTo writes a separate include file from the eq curve export`() {
        val dir = Files.createTempDirectory("eqapo-dynamics-test").toFile()

        EqualizerApoExporter.installTo(dir, BuiltInPresets.CleanPunch)
        EqualizerApoExporter.installDynamicsTo(dir, BuiltInPresets.CleanPunch)

        assertTrue(File(dir, EqualizerApoExporter.INCLUDE_FILE_NAME).exists())
        assertTrue(File(dir, EqualizerApoExporter.INCLUDE_FILE_NAME_DYNAMICS).exists())

        val configTxt = File(dir, "config.txt").readText()
        assertTrue(configTxt.contains("Include: ${EqualizerApoExporter.INCLUDE_FILE_NAME}"))
        assertTrue(configTxt.contains("Include: ${EqualizerApoExporter.INCLUDE_FILE_NAME_DYNAMICS}"))
    }

    @Test
    fun `installDynamicsTo wires its include line exactly once across repeated calls`() {
        val dir = Files.createTempDirectory("eqapo-dynamics-test-repeat").toFile()

        EqualizerApoExporter.installDynamicsTo(dir, BuiltInPresets.CleanPunch)
        EqualizerApoExporter.installDynamicsTo(dir, BuiltInPresets.DeepRumble)

        val expectedLine = "Include: ${EqualizerApoExporter.INCLUDE_FILE_NAME_DYNAMICS}"
        val includeLines = File(dir, "config.txt").readLines().filter { it.trim() == expectedLine }
        assertEquals(1, includeLines.size)
    }
}
