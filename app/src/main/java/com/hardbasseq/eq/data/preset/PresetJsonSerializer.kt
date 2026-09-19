package com.hardbasseq.eq.data.preset

import com.hardbasseq.eq.preset.Preset
import kotlinx.serialization.json.Json

object PresetJsonSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private const val MAX_JSON_SIZE_BYTES = 100 * 1024 // 100 KB limit

    fun exportToJson(preset: Preset): String {
        return json.encodeToString(Preset.serializer(), preset)
    }

    fun importFromJson(jsonString: String): Result<Preset> {
        return runCatching {
            if (jsonString.toByteArray(Charsets.UTF_8).size > MAX_JSON_SIZE_BYTES) {
                throw IllegalArgumentException("Preset JSON exceeds maximum size limit (100 KB)")
            }

            val preset = json.decodeFromString(Preset.serializer(), jsonString)

            if (preset.schemaVersion <= 0) {
                throw IllegalArgumentException("Invalid schemaVersion: ${preset.schemaVersion}")
            }

            if (preset.name.isBlank()) {
                throw IllegalArgumentException("Preset name cannot be blank")
            }

            if (preset.targetCurve.isEmpty()) {
                throw IllegalArgumentException("Preset targetCurve cannot be empty")
            }

            for (point in preset.targetCurve) {
                if (point.frequencyHz.isNaN() || point.frequencyHz.isInfinite() || point.frequencyHz <= 0f) {
                    throw IllegalArgumentException("Invalid frequencyHz: ${point.frequencyHz}")
                }
                if (point.gainDb.isNaN() || point.gainDb.isInfinite() || point.gainDb < -24f || point.gainDb > 24f) {
                    throw IllegalArgumentException("Gain out of bounds [-24, +24 dB]: ${point.gainDb}")
                }
            }

            preset
        }
    }
}
