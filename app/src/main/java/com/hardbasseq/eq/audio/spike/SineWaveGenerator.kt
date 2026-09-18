package com.hardbasseq.eq.audio.spike

import kotlin.math.PI
import kotlin.math.sin

/**
 * Pure PCM16 sine-wave generation for the session-attach spike's test tone.
 * Kept free of any Android API so it is testable as a plain JVM unit test.
 */
object SineWaveGenerator {

    fun generateMonoPcm16(
        sampleRateHz: Int,
        frequencyHz: Double,
        durationSeconds: Double,
        amplitude: Double = 0.2,
    ): ShortArray {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(frequencyHz > 0) { "frequencyHz must be positive" }
        require(amplitude in 0.0..1.0) { "amplitude must be in [0,1]" }

        val frameCount = (sampleRateHz * durationSeconds).toInt()
        val angularStep = 2.0 * PI * frequencyHz / sampleRateHz
        return ShortArray(frameCount) { i ->
            (amplitude * Short.MAX_VALUE * sin(angularStep * i)).toInt().toShort()
        }
    }

    /** Duplicates a mono buffer into an interleaved stereo (L,R,L,R,...) buffer. */
    fun interleaveStereo(mono: ShortArray): ShortArray =
        ShortArray(mono.size * 2) { i -> mono[i / 2] }
}
