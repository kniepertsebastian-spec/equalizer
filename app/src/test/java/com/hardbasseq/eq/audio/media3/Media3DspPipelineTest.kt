package com.hardbasseq.eq.audio.media3

import com.hardbasseq.eq.audio.ProcessingSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class Media3DspPipelineTest {
    @Test
    fun processAudioFramePcm16_bypassReturnsOriginalSamples() {
        val pipeline = Media3DspPipeline()
        val original = shortArrayOf(1000, 2000, -1000, -2000)
        val settings = ProcessingSettings(masterEnabled = true, bypass = true)

        val result = pipeline.processAudioFramePcm16(original, settings)

        for (i in original.indices) {
            assertEquals(original[i], result[i])
        }
    }

    @Test
    fun processAudioFramePcm16_appliesInputGain() {
        val pipeline = Media3DspPipeline()
        val original = shortArrayOf(1000, 2000)
        // +6 dB input gain corresponds to factor ~2.0
        val settings = ProcessingSettings(masterEnabled = true, bypass = false, inputGainDb = 6.02f)

        val result = pipeline.processAudioFramePcm16(original, settings)

        assertEquals(2000.0, result[0].toDouble(), 20.0)
        assertEquals(4000.0, result[1].toDouble(), 40.0)
    }
}
