package com.hardbasseq.eq.dsp

// roadmap-2026.md M6 Phase 1: "Filtermodell für Peak, Low Shelf, High Shelf und
// optional High-/Low-Pass definieren." LOW_PASS/HIGH_PASS ignore gainDb (the RBJ
// cookbook forms below have no gain parameter for them) - kept in one flat shape
// rather than a type hierarchy since M6 Phase 2 needs a uniform list of filters
// to support (per its own tasks) copy/paste, per-filter bypass and reordering.
enum class ParametricFilterType {
    PEAK,
    LOW_SHELF,
    HIGH_SHELF,
    LOW_PASS,
    HIGH_PASS,
}

data class ParametricFilter(
    val type: ParametricFilterType,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float,
    val bypassed: Boolean = false,
)

// M6 Phase 1: "Parametergrenzen für Frequenz, Gain und Q festlegen."
object ParametricFilterBounds {
    const val MIN_FREQUENCY_HZ = 20f
    const val MAX_FREQUENCY_HZ = 20000f
    const val MIN_GAIN_DB = -24f
    const val MAX_GAIN_DB = 24f

    // The RBJ cookbook's alpha term (sin(w0)/(2*Q)) blows up towards instability
    // well before Q=0, and a Q much above ~10 produces a resonance narrow enough
    // to be more a self-oscillation risk than a useful EQ move - both ends are
    // conservative, not hard DSP limits.
    const val MIN_Q = 0.1f
    const val MAX_Q = 10f

    fun clamp(filter: ParametricFilter): ParametricFilter =
        filter.copy(
            frequencyHz = filter.frequencyHz.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ),
            gainDb = filter.gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB),
            q = filter.q.coerceIn(MIN_Q, MAX_Q),
        )
}
