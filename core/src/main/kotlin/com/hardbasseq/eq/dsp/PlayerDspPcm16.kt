package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint

// The player's output stage on interleaved PCM16: the target curve and subsonic
// high-pass (CurveEqualizer) first, then mono bass (stereo only), then a lookahead
// limiter per channel so the bass boost and virtual bass cannot clip the DAC. All
// stages run on floats and the result is converted back to PCM16 once, so an EQ boost
// cannot clip between stages. Each part can be switched off on its own; with both off the buffer
// is left untouched. Android-free like BassExciterPcm16 - the Media3 processor that
// feeds it lives in :player.
//
// The limiter runs per channel (not linked): when only one channel peaks, only that
// one is pulled down, which can nudge the stereo image for a few milliseconds.
data class PlayerDspSettings(
    val monoBassEnabled: Boolean = true,
    val monoBassCutoffHz: Float = DEFAULT_MONO_BASS_CUTOFF_HZ,
    val limiterEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_MONO_BASS_CUTOFF_HZ = 120f
    }
}

class PlayerDspPcm16 {
    private var settings = PlayerDspSettings()
    private val monoSummer = BassMonoSummer()
    private var limiters: List<LookaheadLimiter> = emptyList()
    private val curveEqualizer = CurveEqualizer()

    fun updateSettings(newSettings: PlayerDspSettings) {
        // A limiter that was off holds stale samples: start it fresh.
        if (newSettings.limiterEnabled && !settings.limiterEnabled) limiters.forEach { it.reset() }
        settings = newSettings
        monoSummer.updateSettings(
            BassMonoSummerSettings(enabled = newSettings.monoBassEnabled, cutoffHz = newSettings.monoBassCutoffHz),
        )
    }

    /** The exact target curve (without the subsonic cut) and the subsonic cutoff; empty and 0 = off. */
    fun updateCurve(
        curve: List<TargetPoint>,
        subsonicCutoffHz: Float,
    ) {
        curveEqualizer.setCurve(curve, subsonicCutoffHz)
    }

    // Call whenever the channel layout changes.
    fun configure(channelCount: Int) {
        limiters = List(channelCount.coerceAtLeast(1)) { LookaheadLimiter() }
        monoSummer.reset()
        curveEqualizer.configure(channelCount)
    }

    // A seek or track change: drop the filters' and limiters' memory of the old audio.
    fun reset() {
        monoSummer.reset()
        limiters.forEach { it.reset() }
        curveEqualizer.reset()
    }

    fun process(
        samples: ShortArray,
        offset: Int,
        sampleCount: Int,
        sampleRateHz: Int,
    ) {
        val current = settings
        if (limiters.isEmpty()) return
        val rate = sampleRateHz.toFloat()
        val curveActive = curveEqualizer.beginBuffer(rate)
        if (!(curveActive || current.monoBassEnabled || current.limiterEnabled)) return
        val channels = limiters.size
        val monoActive = current.monoBassEnabled && channels == 2
        val frame = FloatArray(channels)
        var i = offset
        val end = offset + sampleCount
        while (i + channels <= end) {
            for (c in 0 until channels) {
                val sample = samples[i + c] / PCM16_SCALE
                frame[c] = if (curveActive) curveEqualizer.process(c, sample) else sample
            }
            if (monoActive) {
                val mixed = monoSummer.process(frame[0], frame[1], rate)
                frame[0] = mixed.left
                frame[1] = mixed.right
            }
            for (c in 0 until channels) {
                val out = if (current.limiterEnabled) limiters[c].process(frame[c], rate) else frame[c]
                samples[i + c] = toPcm16(out)
            }
            i += channels
        }
    }

    private fun toPcm16(value: Float): Short =
        (value * PCM16_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private companion object {
        const val PCM16_SCALE = 32768f
    }
}
