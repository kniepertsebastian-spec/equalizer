package com.hardbasseq.eq.dsp

// roadmap-2026.md M4: "Warnstufen definieren: ausreichend Headroom; Limiter
// arbeitet gelegentlich; dauerhaft starke Begrenzung/zu aggressive Einstellung."
// Thresholds are an analytical estimate derived from the combined curve's peak
// boost (HeadroomInfo), not a measurement of the Limiter/DynamicsProcessing's
// actual real-time gain reduction - Android exposes no public API for that. This
// can only ever describe "how hard the Limiter is expected to have to work",
// never a verified reading - callers must never present it as more than that
// (M4 acceptance criterion: "Die Oberfläche bezeichnet keine Schätzung als echte
// Messung").
enum class HeadroomWarningLevel {
    SUFFICIENT,
    OCCASIONAL_LIMITING,
    HEAVY_LIMITING,
}

object HeadroomWarningLevelCalculator {
    // Below this, an occasional transient might touch the Limiter's threshold;
    // above it, boosts this large are expected to be limited continuously, not
    // just on peaks. Picked from the existing Limiter defaults (threshold around
    // -1 dB, hard 10:1 ratio) - a rough, undocumented-as-precise estimate, not a
    // calibrated figure.
    private const val OCCASIONAL_THRESHOLD_DB = 6f

    fun fromHeadroom(headroom: HeadroomInfo): HeadroomWarningLevel =
        when {
            !headroom.isClippingRisk -> HeadroomWarningLevel.SUFFICIENT
            headroom.maxPositiveGainDb <= OCCASIONAL_THRESHOLD_DB -> HeadroomWarningLevel.OCCASIONAL_LIMITING
            else -> HeadroomWarningLevel.HEAVY_LIMITING
        }
}
