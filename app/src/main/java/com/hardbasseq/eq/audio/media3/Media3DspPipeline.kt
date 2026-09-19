package com.hardbasseq.eq.audio.media3

import com.hardbasseq.eq.audio.ProcessingSettings
import kotlin.math.pow

class Media3DspPipeline {

    fun processAudioFramePcm16(pcmSamples: ShortArray, settings: ProcessingSettings): ShortArray {
        if (!settings.masterEnabled || settings.bypass) {
            return pcmSamples
        }

        val gainFactor = 10.0.pow((settings.inputGainDb / 20.0)).toFloat()
        val processed = ShortArray(pcmSamples.size)

        for (i in pcmSamples.indices) {
            val sampleFloat = pcmSamples[i] * gainFactor
            val clampedSample = sampleFloat.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            processed[i] = clampedSample.toInt().toShort()
        }

        return processed
    }
}
