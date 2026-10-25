package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint

/**
 * An EQ approximation of Virtual Bass for other apps' audio. Real harmonic synthesis is only
 * possible inside the app's own player; an equalizer can merely emphasise harmonics that are
 * already there. With strongly distorted kicks (uptempo, hardcore) those sit around 90-250 Hz,
 * so the sub-bass the small driver cannot reproduce anyway is eased off and that region lifted.
 */
object VirtualBassApproxCurve {
    const val SUB_BASS_DB = -6f
    const val LOW_BASS_DB = -3f
    const val HARMONIC_LOW_DB = 3f
    const val HARMONIC_HIGH_DB = 2.5f

    fun forMix(mix: Float): List<TargetPoint> {
        val m = mix.coerceIn(0f, 1f)
        if (m <= 0f) return emptyList()
        return listOf(
            TargetPoint(30f, SUB_BASS_DB * m),
            TargetPoint(45f, LOW_BASS_DB * m),
            TargetPoint(70f, 0f),
            TargetPoint(110f, HARMONIC_LOW_DB * m),
            TargetPoint(160f, HARMONIC_HIGH_DB * m),
            TargetPoint(250f, 0f),
        )
    }
}
