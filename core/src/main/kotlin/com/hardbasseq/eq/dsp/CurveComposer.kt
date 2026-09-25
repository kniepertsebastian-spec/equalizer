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
    ): List<TargetPoint> {
        if (correctionCurve.isEmpty()) return voicingCurve.sortedBy { it.frequencyHz }
        if (voicingCurve.isEmpty()) return correctionCurve.sortedBy { it.frequencyHz }

        val sortedCorrection = correctionCurve.sortedBy { it.frequencyHz }
        val sortedVoicing = voicingCurve.sortedBy { it.frequencyHz }
        val frequencies =
            (sortedCorrection.map { it.frequencyHz } + sortedVoicing.map { it.frequencyHz }).toSortedSet()

        return frequencies.map { freqHz ->
            val correctionGain = EqualizerInterpolator.interpolateFrequency(freqHz, sortedCorrection)
            val voicingGain = EqualizerInterpolator.interpolateFrequency(freqHz, sortedVoicing)
            TargetPoint(frequencyHz = freqHz, gainDb = correctionGain + voicingGain)
        }
    }
}
