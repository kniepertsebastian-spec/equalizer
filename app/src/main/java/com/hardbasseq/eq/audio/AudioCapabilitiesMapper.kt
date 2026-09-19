package com.hardbasseq.eq.audio

/**
 * Pure mapping from raw effect descriptors to [AudioCapabilities]. Kept free
 * of any Android framework dependency so it is testable as a plain JVM unit
 * test.
 */
object AudioCapabilitiesMapper {
    fun map(
        descriptors: List<AudioEffectDescriptor>,
        knownTypes: KnownEffectTypeIds,
    ): AudioCapabilities {
        val availableTypes = descriptors.map { it.typeUuid }.toSet()
        return AudioCapabilities(
            hasEqualizer = knownTypes.equalizer in availableTypes,
            hasDynamicsProcessing = knownTypes.dynamicsProcessing in availableTypes,
            hasBassBoost = knownTypes.bassBoost in availableTypes,
            hasLoudnessEnhancer = knownTypes.loudnessEnhancer in availableTypes,
            totalEffectCount = descriptors.size,
        )
    }
}
