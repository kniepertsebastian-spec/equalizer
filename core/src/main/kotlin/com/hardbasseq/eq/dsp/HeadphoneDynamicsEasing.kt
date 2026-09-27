package com.hardbasseq.eq.dsp

// Chat feature ("quality changes for headphones" - user picked gentler
// dynamics over the other options offered: loudness compensation or a real
// crossfeed implementation). Headphones sit right at the ear canal, unlike a
// car system or speaker where ambient noise masks some of the multiband
// compressor's audible work - the same compression that goes unnoticed in a
// car can read as fatiguing/pumping over headphones. Only the MBC eases here;
// the Limiter (its fixed 10:1 ratio in AndroidAudioEngine, and its Session 24
// default threshold) stays exactly as-is regardless of route - it's the
// actual clipping backstop, not a musical/character choice, so headphone mode
// has no business loosening it.
object HeadphoneDynamicsEasing {
    private const val THRESHOLD_EASE_DB = 2.0f
    private const val RATIO_EASE_FACTOR = 0.75f

    // Raises the threshold (the MBC engages later) rather than leaving it
    // alone.
    fun easedThresholdDb(baseThresholdDb: Float): Float = (baseThresholdDb + THRESHOLD_EASE_DB).coerceAtMost(0f)

    // Pulls the ratio proportionally toward 1 (transparent) rather than
    // subtracting a flat amount - a flat subtraction would either go unstable
    // for an already-low ratio or barely touch a high one; scaling the
    // distance from 1 treats every genre/intensity's own ratio consistently.
    fun easedRatio(baseRatio: Float): Float = 1f + (baseRatio - 1f) * RATIO_EASE_FACTOR
}
