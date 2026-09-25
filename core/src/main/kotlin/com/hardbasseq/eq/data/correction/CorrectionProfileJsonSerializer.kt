package com.hardbasseq.eq.data.correction

import com.hardbasseq.eq.correction.CorrectionProfile
import kotlinx.serialization.json.Json

// Mirrors PresetJsonSerializer exactly (see there for why: one validated JSON blob
// per row, reused for the M2-style "corrupted data can't block startup" handling
// in the Room repository, and no per-field migrations for a new CorrectionProfile
// field - only a change to its *shape* needs one).
object CorrectionProfileJsonSerializer {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    private const val MAX_JSON_SIZE_BYTES = 100 * 1024 // 100 KB limit

    fun exportToJson(profile: CorrectionProfile): String = json.encodeToString(CorrectionProfile.serializer(), profile)

    fun importFromJson(jsonString: String): Result<CorrectionProfile> =
        runCatching {
            if (jsonString.toByteArray(Charsets.UTF_8).size > MAX_JSON_SIZE_BYTES) {
                throw IllegalArgumentException("CorrectionProfile JSON exceeds maximum size limit (100 KB)")
            }

            val profile = json.decodeFromString(CorrectionProfile.serializer(), jsonString)

            if (profile.name.isBlank()) {
                throw IllegalArgumentException("CorrectionProfile name cannot be blank")
            }

            if (profile.curve.isEmpty()) {
                throw IllegalArgumentException("CorrectionProfile curve cannot be empty")
            }

            for (point in profile.curve) {
                if (point.frequencyHz.isNaN() || point.frequencyHz.isInfinite() || point.frequencyHz <= 0f) {
                    throw IllegalArgumentException("Invalid frequencyHz: ${point.frequencyHz}")
                }
                if (point.gainDb.isNaN() || point.gainDb.isInfinite() || point.gainDb < -24f || point.gainDb > 24f) {
                    throw IllegalArgumentException("Gain out of bounds [-24, +24 dB]: ${point.gainDb}")
                }
            }

            profile
        }
}
