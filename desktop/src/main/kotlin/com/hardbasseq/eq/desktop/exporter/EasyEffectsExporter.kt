package com.hardbasseq.eq.desktop.exporter

import com.hardbasseq.eq.desktop.DESKTOP_FILTER_Q
import com.hardbasseq.eq.desktop.VirtualBands
import com.hardbasseq.eq.desktop.automaticPreampDb
import com.hardbasseq.eq.desktop.resolveBandGains
import com.hardbasseq.eq.preset.Preset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Generates an EasyEffects (Linux/PipeWire) "Equalizer" preset JSON file.
 * Schema reverse-engineered from real presets shared by the EasyEffects
 * community (e.g. github.com/wwmm/easyeffects preset discussions) - there is
 * no official schema doc, so this mirrors the field names/types observed in
 * working presets: an "output" root, an "equalizer#0" plugin instance with
 * per-channel band0..bandN objects, and a plugins_order array.
 *
 * We only write the file into EasyEffects' own preset folder; the user
 * still has to pick it from the EasyEffects UI (or `easyeffects -l <name>`)
 * since there's no stable, documented way to force-reload it live from
 * outside the app.
 */
object EasyEffectsExporter {
    private val json = Json { prettyPrint = true }

    fun generatePresetJson(
        preset: Preset,
        macroBassDb: Float = preset.macroBassDb,
        macroPunchDb: Float = preset.macroPunchDb,
        macroHaerteDb: Float = preset.macroHaerteDb,
    ): String {
        val bandGains = resolveBandGains(preset, macroBassDb, macroPunchDb, macroHaerteDb)
        val preampDb = automaticPreampDb(bandGains)

        val root =
            buildJsonObject {
                put(
                    "output",
                    buildJsonObject {
                        put("blocklist", JsonArray(emptyList()))
                        put(
                            "equalizer#0",
                            buildJsonObject {
                                put("balance", 0.0)
                                put("bypass", false)
                                put("input-gain", 0.0)
                                put("output-gain", preampDb.toDouble())
                                put("mode", "IIR")
                                put("num-bands", VirtualBands.bands.size)
                                put("pitch-left", 0.0)
                                put("pitch-right", 0.0)
                                put("split-channels", false)
                                put("left", channelBands(bandGains))
                                put("right", channelBands(bandGains))
                            },
                        )
                        put("plugins_order", JsonArray(listOf(JsonPrimitive("equalizer#0"))))
                    },
                )
            }
        return json.encodeToString(JsonObject.serializer(), root)
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
                        put("q", DESKTOP_FILTER_Q.toDouble())
                        put("slope", "x1")
                        put("solo", false)
                        put("type", "Bell")
                        put("width", 4.0)
                    },
                )
            }
        }

    /** Default EasyEffects output-preset directory for the current user. */
    fun defaultPresetDir(): File = File(System.getProperty("user.home"), ".config/easyeffects/output")

    fun installTo(
        presetDir: File,
        preset: Preset,
        macroBassDb: Float = preset.macroBassDb,
        macroPunchDb: Float = preset.macroPunchDb,
        macroHaerteDb: Float = preset.macroHaerteDb,
    ): File {
        presetDir.mkdirs()
        val file = File(presetDir, "${sanitizeFileName(preset.name)}.json")
        file.writeText(generatePresetJson(preset, macroBassDb, macroPunchDb, macroHaerteDb))
        return file
    }

    private fun sanitizeFileName(name: String): String = name.replace(Regex("[/\\\\:*?\"<>|]"), "-").trim()
}
