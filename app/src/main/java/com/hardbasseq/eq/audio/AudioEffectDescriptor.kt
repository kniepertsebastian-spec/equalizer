package com.hardbasseq.eq.audio

import java.util.UUID

/**
 * Platform-agnostic representation of one entry returned by
 * `android.media.audiofx.AudioEffect.queryEffects()`.
 */
data class AudioEffectDescriptor(
    val typeUuid: UUID,
    val effectUuid: UUID,
    val name: String,
    val implementor: String,
    val connectMode: EffectConnectMode,
)

enum class EffectConnectMode {
    INSERT,
    AUXILIARY,
    UNKNOWN,
}

/**
 * The well-known effect type UUIDs this app cares about, supplied by the
 * Android-specific repository implementation so the pure mapping logic in
 * [AudioCapabilitiesMapper] never has to duplicate framework constants.
 */
data class KnownEffectTypeIds(
    val equalizer: UUID,
    val dynamicsProcessing: UUID,
    val bassBoost: UUID,
    val loudnessEnhancer: UUID,
)
