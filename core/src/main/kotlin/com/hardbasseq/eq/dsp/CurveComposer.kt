package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint

// roadmap-2026.md M3/§5: combines a headphone CorrectionProfile's curve with a
// VoicingPreset's (== Preset) curve into one resulting target curve, before that
// combined curve gets mapped onto whatever bands the device/session actually
// offers (EqualizerInterpolator.interpolateCurveToBands) or sampled for headroom
// (HeadroomCalculator). Kept as a free function over List<TargetPoint> rather than
// tied to CorrectionProfile/Preset directly so it composes with CustomOverrides
// (macro deltas, manual per-band edits) the same way.
object CurveComposer {
    // Combines two curves by summing their independently-interpolated gain at the
    // union of both curves' own frequency points - exact wherever either input
    // curve actually defines a point, not just at frequencies they happen to
    // share. An empty curve interpolates to 0 dB everywhere, so combining with
    // BuiltInCorrectionProfiles.None is a true no-op.
    fun combine(
        correctionCurve: List<TargetPoint>,
        voicingCurve: List<TargetPoint>,
    ): List<TargetPoint> = combine(listOf(correctionCurve, voicingCurve))

    // N-ary variant, added alongside dsp/LoudnessCompensationCurve (a third
    // curve source on top of correction + voicing). Same summing-at-the-union
    // approach as the two-curve overload above, generalized to any number of
    // curves rather than replacing it, so existing call sites keep working
    // unchanged.
    fun combine(curves: List<List<TargetPoint>>): List<TargetPoint> {
        val nonEmptyCurves = curves.filter { it.isNotEmpty() }
        if (nonEmptyCurves.isEmpty()) return emptyList()
        if (nonEmptyCurves.size == 1) return nonEmptyCurves.first().sortedBy { it.frequencyHz }

        val sortedCurves = nonEmptyCurves.map { curve -> curve.sortedBy { it.frequencyHz } }
        val frequencies = sortedCurves.flatMap { curve -> curve.map { it.frequencyHz } }.toSortedSet()

        return frequencies.map { freqHz ->
            val totalGainDb =
                sortedCurves
                    .sumOf { curve ->
                        EqualizerInterpolator.interpolateFrequency(freqHz, curve).toDouble()
                    }.toFloat()
            TargetPoint(frequencyHz = freqHz, gainDb = totalGainDb)
        }
    }
}
