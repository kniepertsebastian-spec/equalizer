package com.hardbasseq.eq.desktop.exporter

import com.hardbasseq.eq.correction.BuiltInCorrectionProfiles
import com.hardbasseq.eq.desktop.DESKTOP_FILTER_Q
import com.hardbasseq.eq.desktop.VirtualBands
import com.hardbasseq.eq.desktop.automaticPreampDb
import com.hardbasseq.eq.desktop.resolveBandGains
import com.hardbasseq.eq.dsp.HeadroomCalculator
import com.hardbasseq.eq.preset.BuiltInGenrePresets
import com.hardbasseq.eq.preset.BuiltInPresets
import com.hardbasseq.eq.preset.PortableSoundProfile
import com.hardbasseq.eq.preset.Preset
import com.hardbasseq.eq.preset.PresetIntensity
import com.hardbasseq.eq.preset.PresetIntensityResolver
import com.hardbasseq.eq.preset.TargetPoint
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
    fun `preamp fully cancels the true cascaded-filter peak, not just the highest single band`() {
        val config = EqualizerApoExporter.generateConfig(BuiltInPresets.CleanPunch)

        val preampDb =
            config
                .lines()
                .first { it.startsWith("Preamp:") }
                .substringAfter("Preamp:")
                .substringBefore("dB")
                .trim()
                .toFloat()

        val bandGains = resolveBandGains(BuiltInPresets.CleanPunch)
        val expectedPreampDb =
            HeadroomCalculator.fromCascadedPeakingFilters(bandGains, VirtualBands.bands, DESKTOP_FILTER_Q).recommendedInputGainDb

        assertEquals(expectedPreampDb, preampDb, 0.01f)
    }

    // Chat feature ("Presets für Aggressive/Very Aggressive nicht übersteuern
    // lassen"): proves the bug this fixes - several closely-spaced, heavily
    // boosted bass bands combine (cascaded filters add in dB) to a REAL peak
    // above any single band's own gain, which the old naive max-of-bands
    // preamp calculation missed.
    @Test
    fun `very aggressive intensity's true combined peak exceeds its highest single band gain`() {
        val preset = PresetIntensityResolver.resolve(BuiltInGenrePresets.Uptempo, PresetIntensity.VERY_AGGRESSIVE)
        val bandGains = resolveBandGains(preset)

        val naiveMaxDb = bandGains.values.max()
        val trueCombinedPeakDb =
            HeadroomCalculator.fromCascadedPeakingFilters(bandGains, VirtualBands.bands, DESKTOP_FILTER_Q).maxPositiveGainDb

        assertTrue(
            "expected the cascaded peak ($trueCombinedPeakDb dB) to exceed the naive per-band max ($naiveMaxDb dB)",
            trueCombinedPeakDb > naiveMaxDb,
        )
    }

    @Test
    fun `automaticPreampDb, applied as a flat broadband gain, brings the true combined peak down to exactly zero`() {
        listOf(PresetIntensity.AGGRESSIVE, PresetIntensity.VERY_AGGRESSIVE).forEach { intensity ->
            val preset = PresetIntensityResolver.resolve(BuiltInGenrePresets.Uptempo, intensity)
            val bandGains = resolveBandGains(preset)
            val preampDb = automaticPreampDb(bandGains)
            val trueCombinedPeakDb =
                HeadroomCalculator.fromCascadedPeakingFilters(bandGains, VirtualBands.bands, DESKTOP_FILTER_Q).maxPositiveGainDb

            // Preamp is a single flat gain applied ahead of every band filter, so it
            // shifts the whole combined response (and thus its peak) by the same
            // amount at every frequency - unlike a per-band gain change, which would
            // reshape each filter's own response.
            assertEquals(0f, trueCombinedPeakDb + preampDb, 0.01f)
        }
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

    private fun flatCurve(gainDb: Float) =
        VirtualBands.bands.map { band ->
            TargetPoint(band.centerFreqHz.toFloat(), gainDb)
        }

    private fun profile(
        preset: Preset,
        applied: List<TargetPoint> = emptyList(),
    ) = PortableSoundProfile(
        preset = preset,
        correction = BuiltInCorrectionProfiles.None,
        appliedEqCurve = applied,
        macroBassDb = preset.macroBassDb,
        macroPunchDb = preset.macroPunchDb,
        macroHaerteDb = preset.macroHaerteDb,
        inputGainDb = 0f,
        mbcEnabled = false,
        mbcThresholdDb = -8f,
        mbcRatio = 2.5f,
        limiter = preset.limiter,
        processingEnabled = true,
    )

    @Test
    fun `subsonic cutoff becomes a true high-pass filter line`() {
        val preset = BuiltInPresets.CleanPunch.copy(subsonicCutoffHz = 35f)

        val config = EqualizerApoExporter.generateConfig(preset)

        assertTrue(config.lines().any { it.matches(Regex("""Filter 16: ON HPQ Fc 35\.00 Hz Q 0\.71""")) })
    }

    @Test
    fun `no high-pass line when subsonic is off`() {
        val config = EqualizerApoExporter.generateConfig(BuiltInPresets.CleanPunch)

        assertTrue(config.lines().none { it.contains("HPQ") })
    }

    @Test
    fun `profile export uses the applied phone curve instead of the preset curve`() {
        val applied = flatCurve(3f)

        val config = EqualizerApoExporter.generateConfig(profile(BuiltInPresets.Flat, applied))

        val gains = config.lines().filter { it.startsWith("Filter") }
        assertEquals(15, gains.size)
        assertTrue(gains.all { it.contains("Gain 3.00 dB") })
    }

    @Test
    fun `profile export does not add a second high-pass on top of the applied curve`() {
        val preset = BuiltInPresets.Flat.copy(subsonicCutoffHz = 35f)
        val applied = flatCurve(0f)

        val withApplied = EqualizerApoExporter.generateConfig(profile(preset, applied))
        val withoutApplied = EqualizerApoExporter.generateConfig(profile(preset))

        assertTrue(withApplied.lines().none { it.contains("HPQ") })
        assertTrue(withoutApplied.lines().any { it.contains("HPQ") })
    }

    @Test
    fun `profile export of an unmodified preset matches the preset export`() {
        val preset = BuiltInPresets.CleanPunch

        assertEquals(
            EqualizerApoExporter.generateConfig(preset),
            EqualizerApoExporter.generateConfig(profile(preset)),
        )
    }
}
