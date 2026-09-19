package com.hardbasseq.eq.audio.spike

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

object LogarithmicSweepGenerator {

    fun generateLogSweepPcm16(
        sampleRateHz: Int = 44100,
        startFreqHz: Double = 20.0,
        endFreqHz: Double = 20000.0,
        durationSeconds: Double = 2.0,
        amplitude: Double = 0.2,
    ): ShortArray {
        val totalSamples = (sampleRateHz * durationSeconds).toInt()
        val pcm = ShortArray(totalSamples)
        val k = (startFreqHz * durationSeconds) / ln(endFreqHz / startFreqHz)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRateHz
            val phase = 2.0 * PI * k * (Math.exp((t / durationSeconds) * ln(endFreqHz / startFreqHz)) - 1.0)
            val sample = (amplitude * Short.MAX_VALUE * sin(phase)).toInt().toShort()
            pcm[i] = sample
        }
        return pcm
    }
}
