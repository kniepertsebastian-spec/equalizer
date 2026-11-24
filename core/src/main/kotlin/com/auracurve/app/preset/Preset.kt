package com.auracurve.app.preset

import kotlinx.serialization.Serializable

@Serializable
data class TargetPoint(
    val frequencyHz: Float,
    val gainDb: Float,
)

@Serializable
data class LimiterConfig(
    val enabled: Boolean = true,
    // Session 24: raised -1.0 -> -0.3dB at the user's explicit request for more
    // headroom/punch, after already loosening the input-gain and MBC makeup-gain
    // stages ahead of this in AndroidAudioEngine. -0.3dBFS is a standard mastering
    // true-peak ceiling, not an arbitrary number - the actual clipping guarantee
    // comes from the Limiter's ratio (10:1, unchanged, in AndroidAudioEngine), not
    // this threshold, so this alone doesn't add real clipping risk.
    val thresholdDb: Float = -0.3f,
)

/**
 * Which playback source a preset is tuned for (roadmap 15).
 *
 * The built-in player runs its own DSP chain (pre-EQ, bass mono, soft clipper, MBC and limiter) with
 * no loudness normalization in front of it, so a curve tuned for Spotify through the system EQ can
 * move a speaker cone much further there. Presets are therefore tuned per source:
 * - [ALL]: fits every source (Flat, listening modes, and every custom or imported preset).
 * - [OWN_PLAYER]: tuned for the built-in player (speaker-safe: subsonic filter, defensive limiter).
 * - [EXTERNAL_APPS]: tuned for Spotify and other apps through the system EQ.
 */
@Serializable
enum class PresetSourceScope {
    ALL,
    OWN_PLAYER,
    EXTERNAL_APPS,
    ;

    /** True when a preset with this scope fits playback from [source]; [ALL] on either side always fits. */
    fun fits(source: PresetSourceScope): Boolean = this == ALL || source == ALL || this == source
}

@Serializable
data class PresetMetadata(
    val genre: String = "uptempo-hardcore",
    val builtIn: Boolean = false,
    // Defaults to ALL, so every preset saved, exported or shared before roadmap 15 decodes unchanged.
    val sourceScope: PresetSourceScope = PresetSourceScope.ALL,
)

@Serializable
data class Preset(
    val id: String,
    val schemaVersion: Int = 1,
    val name: String,
    val targetCurve: List<TargetPoint>,
    val macroBassDb: Float = 0f,
    val macroPunchDb: Float = 0f,
    val macroHaerteDb: Float = 0f,
    val requestedHeadroomDb: Float = 4.0f,
    val mbcEnabled: Boolean = true,
    val mbcThresholdDb: Float = -8.0f,
    val mbcRatio: Float = 2.5f,
    val limiter: LimiterConfig = LimiterConfig(),
    val metadata: PresetMetadata = PresetMetadata(),
    // Context-mode features (car / Bluetooth speaker). All default to "off", so
    // every preset and every JSON saved before these existed decodes unchanged.
    // Max boost (dB) of the volume-dependent loudness compensation, 0 = off.
    val loudnessMaxBoostDb: Float = 0f,
    // Subsonic high-pass corner in Hz, 0 = off (see SubsonicFilterCurve).
    val subsonicCutoffHz: Float = 0f,
    // 0..1 amount of synthesized bass harmonics, 0 = off (see BassExciter).
    val virtualBassMix: Float = 0f,
)
