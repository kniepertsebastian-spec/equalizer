package com.hardbasseq.eq.audio

/**
 * Encapsulates `AudioEffect.queryEffects()` so callers never touch the
 * Android audio-effects framework directly (see roadmap section 7,
 * "Zentrale Schnittstellen").
 */
interface AudioEffectRepository {
    /** The effect type UUIDs this repository can recognize on this platform. */
    val knownEffectTypeIds: KnownEffectTypeIds

    /** Queries the system for all globally registered audio effect descriptors. */
    fun queryAvailableEffects(): List<AudioEffectDescriptor>
}
