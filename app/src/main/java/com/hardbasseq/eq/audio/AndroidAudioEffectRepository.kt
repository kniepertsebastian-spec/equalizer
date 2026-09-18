package com.hardbasseq.eq.audio

import android.media.audiofx.AudioEffect

/**
 * Android-backed [AudioEffectRepository]. Wraps the static,
 * session-independent `AudioEffect.queryEffects()` call, which only reports
 * *which* effect engines exist on this device/OS build – it says nothing
 * about band counts or gain ranges, and it does not attach to any session.
 */
class AndroidAudioEffectRepository : AudioEffectRepository {

    override val knownEffectTypeIds: KnownEffectTypeIds = KnownEffectTypeIds(
        equalizer = AudioEffect.EFFECT_TYPE_EQUALIZER,
        dynamicsProcessing = AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING,
        bassBoost = AudioEffect.EFFECT_TYPE_BASS_BOOST,
        loudnessEnhancer = AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER,
    )

    override fun queryAvailableEffects(): List<AudioEffectDescriptor> =
        AudioEffect.queryEffects().map { descriptor ->
            AudioEffectDescriptor(
                typeUuid = descriptor.type,
                effectUuid = descriptor.uuid,
                name = descriptor.name,
                implementor = descriptor.implementor,
                connectMode = when (descriptor.connectMode) {
                    AudioEffect.EFFECT_INSERT -> EffectConnectMode.INSERT
                    AudioEffect.EFFECT_AUXILIARY -> EffectConnectMode.AUXILIARY
                    else -> EffectConnectMode.UNKNOWN
                },
            )
        }
}
