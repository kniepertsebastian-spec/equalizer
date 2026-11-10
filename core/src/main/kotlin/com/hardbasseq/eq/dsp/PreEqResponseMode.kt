package com.hardbasseq.eq.dsp

/**
 * Controls how aggressively the high-resolution pre-EQ responds to curve changes.
 *
 * SMOOTH: Uses Simpson integration (3-point sampling) for gradual, anti-aliased curves.
 *         Best for dense curves (AutoEQ profiles) and gentle tonal shaping.
 *         May sound softer/less aggressive.
 *
 * BALANCED: Uses Simpson integration for the base curve, but tighter manual deltas
 *           (1 octave reach instead of 2). Good compromise between precision and smoothness.
 *
 * PRECISE: Uses geometric midpoint sampling (single point per band) for sharper response.
 *          Manual deltas have minimal spread (0.5 octave reach).
 *          More aggressive, punchier, better for bass emphasis and targeted cuts.
 *          May cause small aliasing artifacts on very steep curves.
 *
 * The mode affects:
 * - Integration method (Simpson vs midpoint)
 * - Manual delta reach distance
 * - How bass boosts translate to the actual bands
 */
enum class PreEqResponseMode {
    SMOOTH,
    BALANCED,
    PRECISE,
    ;

    /** How many octaves a manual band adjustment's influence reaches to each side. */
    val manualDeltaReachOctaves: Float
        get() =
            when (this) {
                SMOOTH -> 2.0f // Original: spreads far, very gradual
                BALANCED -> 1.0f // Tighter: still smooth but more localized
                PRECISE -> 0.5f // Minimal spread: direct control
            }

    /** Whether to use Simpson integration (true) or geometric midpoint (false). */
    val useSimpsonIntegration: Boolean
        get() =
            when (this) {
                SMOOTH, BALANCED -> true
                PRECISE -> false
            }

    companion object {
        /** Default mode: balanced compromise between smoothness and responsiveness. */
        val DEFAULT = BALANCED
    }
}
