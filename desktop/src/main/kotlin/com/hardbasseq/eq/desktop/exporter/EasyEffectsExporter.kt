package com.hardbasseq.eq.desktop.exporter

import com.hardbasseq.eq.correction.BuiltInCorrectionProfiles
import com.hardbasseq.eq.desktop.VirtualBands
import com.hardbasseq.eq.dsp.CurveComposer
import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.dsp.HeadroomCalculator
import com.hardbasseq.eq.preset.PortableSoundProfile
import com.hardbasseq.eq.preset.Preset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.sqrt

/**
 * Exports an EasyEffects 7.1.6 output chain. The plugin keys follow the
 * preset readers in EasyEffects' equalizer, multiband compressor and limiter.
 * The dynamics engines differ, so this is an approximation, not identical DSP.
 */
object EasyEffectsExporter {
    private val json = Json { prettyPrint = true }
    private const val MBC_BANDS = 8

    fun generatePresetJson(
        preset: Preset,
        macroBassDb: Float = preset.macroBassDb,
        macroPunchDb: Float = preset.macroPunchDb,
        macroHaerteDb: Float = preset.macroHaerteDb,
    ): String {
        val headroom =
            HeadroomCalculator.fromCombinedCurve(
                preset.targetCurve,
                macroBassDb,
                macroPunchDb,
                macroHaerteDb,
            )
        val peak = headroom.maxPositiveGainDb
        val gain = if (preset.limiter.enabled) -0.3f * peak else -peak
        return generatePresetJson(
            PortableSoundProfile(
                preset = preset,
                correction = BuiltInCorrectionProfiles.None,
                macroBassDb = macroBassDb,
                macroPunchDb = macroPunchDb,
                macroHaerteDb = macroHaerteDb,
                inputGainDb = gain.coerceIn(-15f, 0f),
                mbcEnabled = preset.mbcEnabled,
                mbcThresholdDb = preset.mbcThresholdDb,
                mbcRatio = preset.mbcRatio,
                limiter = preset.limiter,
                processingEnabled = true,
            ),
        )
    }

    fun generatePresetJson(
        profile: PortableSoundProfile,
        macroBassDb: Float = profile.macroBassDb,
        macroPunchDb: Float = profile.macroPunchDb,
        macroHaerteDb: Float = profile.macroHaerteDb,
    ): String {
        val usingAppliedCurve = profile.appliedEqCurve.isNotEmpty()
        val curve =
            if (usingAppliedCurve) {
                profile.appliedEqCurve
            } else {
                CurveComposer.combine(profile.correction.curve, profile.preset.targetCurve)
            }
        // Live Android bands already include the original macros. Apply only
        // changes made after import so the same bass boost is not added twice.
        val bass = if (usingAppliedCurve) macroBassDb - profile.macroBassDb else macroBassDb
        val punch = if (usingAppliedCurve) macroPunchDb - profile.macroPunchDb else macroPunchDb
        val haerte = if (usingAppliedCurve) macroHaerteDb - profile.macroHaerteDb else macroHaerteDb
        val bandGains = EqualizerInterpolator.interpolateCurveToBands(curve, VirtualBands.bands, bass, punch, haerte)
        val inputGain =
            if (macroBassDb == profile.macroBassDb &&
                macroPunchDb == profile.macroPunchDb &&
                macroHaerteDb == profile.macroHaerteDb
            ) {
                profile.inputGainDb
            } else {
                val peak = HeadroomCalculator.fromCombinedCurve(curve, bass, punch, haerte).maxPositiveGainDb
                if (profile.limiter.enabled) (-0.3f * peak).coerceIn(-15f, 0f) else (-peak).coerceIn(-15f, 0f)
            }

        val plugins =
            buildList {
                if (profile.processingEnabled) {
                    add("equalizer#0")
                    if (profile.mbcEnabled) add("multiband_compressor#0")
                    if (profile.limiter.enabled) add("limiter#0")
                }
            }
        val root =
            buildJsonObject {
                put(
                    "output",
                    buildJsonObject {
                        put("blocklist", JsonArray(emptyList()))
                        put("equalizer#0", equalizer(bandGains, inputGain))
                        if (profile.mbcEnabled) put("multiband_compressor#0", multibandCompressor(profile))
                        if (profile.limiter.enabled) put("limiter#0", limiter(profile))
                        put("plugins_order", JsonArray(plugins.map { JsonPrimitive(it) }))
                    },
                )
            }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun equalizer(
        bandGains: Map<Int, Float>,
        inputGainDb: Float,
    ): JsonObject =
        buildJsonObject {
            put("balance", 0.0)
            put("bypass", false)
            put("input-gain", 0.0)
            // Android applies DynamicsProcessing input gain after the EQ.
            put("output-gain", inputGainDb.toDouble())
            put("mode", "IIR")
            put("num-bands", VirtualBands.bands.size)
            put("pitch-left", 0.0)
            put("pitch-right", 0.0)
            put("split-channels", false)
            put("left", channelBands(bandGains))
            put("right", channelBands(bandGains))
        }

    private fun channelBands(bandGainsDb: Map<Int, Float>): JsonObject =
        buildJsonObject {
            VirtualBands.bands.forEach { band ->
                put(
                    "band${band.index}",
                    buildJsonObject {
                        put("frequency", band.centerFreqHz.toDouble())
                        put("gain", (bandGainsDb[band.index] ?: 0f).toDouble())
                        put("mode", "RLC (BT)")
                        put("mute", false)
                        put("q", bandQ(band.index))
                        put("slope", "x1")
                        put("solo", false)
                        put("type", "Bell")
                        put("width", 4.0)
                    },
                )
            }
        }

    /** Keep adjacent bass bells from stacking far above the requested curve. */
    private fun bandQ(index: Int): Double {
        val centers = VirtualBands.bands.map { it.centerFreqHz.toDouble() }
        val center = centers[index]
        val lower = if (index == 0) center * center / centers[1] else centers[index - 1]
        val upper = if (index == centers.lastIndex) center * center / centers[index - 1] else centers[index + 1]
        val lowerEdge = sqrt(lower * center)
        val upperEdge = sqrt(center * upper)
        return (center / (upperEdge - lowerEdge)).coerceIn(0.5, 8.0)
    }

    private fun multibandCompressor(profile: PortableSoundProfile): JsonObject =
        buildJsonObject {
            put("bypass", false)
            put("input-gain", 0.0)
            put("output-gain", 0.0)
            put("dry", -100.0)
            put("wet", 0.0)
            put("compressor-mode", "Modern")
            put("envelope-boost", "None")
            put("stereo-split", false)
            repeat(MBC_BANDS) { index -> put("band$index", compressorBand(index, profile)) }
        }

    private fun compressorBand(
        index: Int,
        profile: PortableSoundProfile,
    ): JsonObject {
        val active = index < 3
        val ratio = profile.mbcRatio.coerceIn(1f, 6f)
        val threshold = profile.mbcThresholdDb.coerceIn(-30f, 0f)
        val makeup = ((-threshold) * (1f - 1f / ratio) * 0.5f).coerceIn(0f, 4f)
        val splits = listOf(10.0, 120.0, 1500.0, 2000.0, 4000.0, 8000.0, 12000.0, 16000.0)
        val attack = listOf(15.0, 8.0, 3.0)
        val release = listOf(180.0, 120.0, 80.0)
        return buildJsonObject {
            if (index > 0) {
                put("enable-band", active)
                put("split-frequency", splits[index])
            }
            put("compressor-enable", active)
            put("solo", false)
            put("mute", false)
            put("attack-threshold", if (active) threshold.toDouble() else -12.0)
            put("attack-time", if (active) attack[index] else 20.0)
            put("release-threshold", -100.0)
            put("release-time", if (active) release[index] else 100.0)
            put("ratio", if (active) ratio.toDouble() else 1.0)
            put("knee", -6.0)
            put("makeup", if (active) makeup.toDouble() else 0.0)
            put("compression-mode", "Downward")
            put("external-sidechain", false)
            put("sidechain-mode", "RMS")
            put("sidechain-source", "Middle")
            put("stereo-split-source", "Left/Right")
            put("sidechain-lookahead", 0.0)
            put("sidechain-reactivity", 10.0)
            put("sidechain-preamp", 0.0)
            put("sidechain-custom-lowcut-filter", false)
            put("sidechain-custom-highcut-filter", false)
            put("sidechain-lowcut-frequency", splits[index])
            put("sidechain-highcut-frequency", if (index < 7) splits[index + 1] else 20000.0)
            put("boost-threshold", -72.0)
            put("boost-amount", 6.0)
        }
    }

    private fun limiter(profile: PortableSoundProfile): JsonObject =
        buildJsonObject {
            put("mode", "Herm Thin")
            put("oversampling", "None")
            put("dithering", "None")
            put("bypass", false)
            put("input-gain", 0.0)
            put("output-gain", 0.0)
            put("lookahead", 5.0)
            put("attack", 1.0)
            // EasyEffects 7.1.6 caps release at 20 ms; Android requests 50 ms.
            put("release", 20.0)
            put(
                "threshold",
                profile.limiter.thresholdDb
                    .coerceIn(-48f, 0f)
                    .toDouble(),
            )
            put("sidechain-preamp", 0.0)
            put("stereo-link", 100.0)
            put("alr-attack", 5.0)
            put("alr-release", 50.0)
            put("alr-knee", 0.0)
            put("alr", false)
            // EasyEffects defaults to boosting, unlike Android's zero-post-gain limiter.
            put("gain-boost", false)
            put("external-sidechain", false)
        }

    fun defaultPresetDir(): File = File(System.getProperty("user.home"), ".config/easyeffects/output")

    fun installTo(
        presetDir: File,
        preset: Preset,
        macroBassDb: Float = preset.macroBassDb,
        macroPunchDb: Float = preset.macroPunchDb,
        macroHaerteDb: Float = preset.macroHaerteDb,
    ): File = writePreset(presetDir, preset.name, generatePresetJson(preset, macroBassDb, macroPunchDb, macroHaerteDb))

    fun installTo(
        presetDir: File,
        profile: PortableSoundProfile,
        macroBassDb: Float = profile.macroBassDb,
        macroPunchDb: Float = profile.macroPunchDb,
        macroHaerteDb: Float = profile.macroHaerteDb,
    ): File = writePreset(presetDir, profile.preset.name, generatePresetJson(profile, macroBassDb, macroPunchDb, macroHaerteDb))

    private fun writePreset(
        presetDir: File,
        name: String,
        content: String,
    ): File {
        presetDir.mkdirs()
        return File(presetDir, "${sanitizeFileName(name)}.json").also { it.writeText(content) }
    }

    private fun sanitizeFileName(name: String): String = name.replace(Regex("[/\\\\:*?\"<>|]"), "-").trim()
}
