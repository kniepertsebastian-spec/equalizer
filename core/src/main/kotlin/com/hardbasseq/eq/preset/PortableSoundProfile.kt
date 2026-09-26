package com.hardbasseq.eq.preset

import com.hardbasseq.eq.correction.CorrectionProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A snapshot of the sound currently configured on Android, independent of its audio APIs. */
@Serializable
data class PortableSoundProfile(
    val version: Int = 1,
    val preset: Preset,
    val correction: CorrectionProfile,
    val appliedEqCurve: List<TargetPoint> = emptyList(),
    val macroBassDb: Float,
    val macroPunchDb: Float,
    val macroHaerteDb: Float,
    val inputGainDb: Float,
    val mbcEnabled: Boolean,
    val mbcThresholdDb: Float,
    val mbcRatio: Float,
    val limiter: LimiterConfig,
    val processingEnabled: Boolean,
)

/** File format shared by the Android export and the desktop import. */
object PortableSoundProfileJson {
    private const val MAX_BYTES = 100 * 1024
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    fun export(profile: PortableSoundProfile): String {
        validate(profile)
        return json.encodeToString(PortableSoundProfile.serializer(), profile)
    }

    fun import(text: String): PortableSoundProfile {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Profil ist größer als 100 KB" }
        return json.decodeFromString(PortableSoundProfile.serializer(), text).also(::validate)
    }

    private fun validate(profile: PortableSoundProfile) {
        require(profile.version == 1) { "Unbekannte Profilversion: ${profile.version}" }
        require(profile.preset.name.isNotBlank()) { "Preset-Name fehlt" }
        require(profile.preset.targetCurve.isNotEmpty()) { "Preset-Kurve fehlt" }
        require(profile.macroBassDb.isFinite() && profile.macroBassDb in -6f..6f)
        require(profile.macroPunchDb.isFinite() && profile.macroPunchDb in -6f..6f)
        require(profile.macroHaerteDb.isFinite() && profile.macroHaerteDb in -2f..2f)
        require(profile.inputGainDb.isFinite() && profile.inputGainDb in -36f..0f)
        require(profile.mbcThresholdDb.isFinite() && profile.mbcThresholdDb in -30f..0f)
        require(profile.mbcRatio.isFinite() && profile.mbcRatio in 1f..6f)
        require(profile.limiter.thresholdDb.isFinite() && profile.limiter.thresholdDb in -48f..0f)
        require(profile.appliedEqCurve.size <= 64 && profile.correction.curve.size <= 512)
        listOf(profile.preset.targetCurve, profile.correction.curve, profile.appliedEqCurve).forEach { curve ->
            require(curve.size <= 512)
            curve.forEach { point ->
                require(point.frequencyHz.isFinite() && point.frequencyHz in 1f..20000f)
                require(point.gainDb.isFinite() && point.gainDb in -24f..24f)
            }
        }
    }
}
