package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint

// Turns the system media volume level (relative dB on the 20*log10(volume
// fraction) scale SystemVolumeRepository reports: half volume is about -6 dB, a
// tenth about -20 dB) into a loudness-compensation curve for a context preset.
// Unlike headphone mode's own compensation (reference = full volume), context
// sounds are tuned to sound balanced at about half volume, so nothing is added
// at or above that point and the boost only grows below it.
object VolumeLevelMapper {
    // -6.02 dB = half scale.
    const val REFERENCE_LEVEL_DB = -6.02f

    fun compensationCurve(
        currentLevelDb: Float,
        maxBoostDb: Float,
    ): List<TargetPoint> =
        LoudnessCompensationCurve.forLevel(
            currentLevelDb = currentLevelDb,
            referenceLevelDb = REFERENCE_LEVEL_DB,
            maxBoostDb = maxBoostDb,
        )
}
