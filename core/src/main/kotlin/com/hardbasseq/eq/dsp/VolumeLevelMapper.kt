package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.log10

// Bridges a system media-volume position (0..1, e.g. AudioManager index/max) to
// LoudnessCompensationCurve's relative-dB scale. The volume steps are treated as
// linear amplitude, i.e. 20*log10(fraction): half volume is about -6 dB, a tenth
// about -20 dB. Real devices map steps to gain differently, but the curve only
// needs a monotonic "how far below the reference listening level" measure (see
// LoudnessCompensationCurve), not absolute SPL.
object VolumeLevelMapper {
    // Below this the log mapping would run off to -infinity.
    private const val MIN_FRACTION = 0.01f

    // At or above this volume (about -6 dB, half scale) no compensation is added:
    // that is where the tuned curves are assumed to sound balanced.
    const val REFERENCE_FRACTION = 0.5f

    fun fractionToRelativeDb(fraction: Float): Float = 20f * log10(fraction.coerceIn(MIN_FRACTION, 1f))

    fun compensationCurve(
        volumeFraction: Float,
        maxBoostDb: Float,
    ): List<TargetPoint> =
        LoudnessCompensationCurve.forLevel(
            currentLevelDb = fractionToRelativeDb(volumeFraction),
            referenceLevelDb = fractionToRelativeDb(REFERENCE_FRACTION),
            maxBoostDb = maxBoostDb,
        )
}
