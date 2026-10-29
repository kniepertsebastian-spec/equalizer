package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint

// The player's whole audio chain on interleaved PCM16, in one float pass:
// 1. target curve and subsonic high-pass (CurveEqualizer), whose pre-gain makes room
//    for the strongest boost,
// 2. virtual bass (BassExciter) - after the pre-gain, so its harmonics fit in,
// 3. mono bass (stereo only),
// 4. one lookahead limiter linked across the channels, so neither the boost nor the
//    harmonics can clip the DAC.
// Nothing is clipped or rounded between the stages; the result goes back to PCM16 once,
// with TPDF dither, so quiet passages after a large pre-gain cut keep their detail
// instead of turning into rounding distortion. Each part can be switched off on its own;
// with everything off the buffer is left untouched. Android-free like the rest of :core -
// the Media3 processor that feeds it lives in :player.
//
// PCM16 rather than float out: Media3 1.3.1 runs its speed processor (Sonic) behind
// this one, and that accepts 16-bit only.
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
    private var limiter = LookaheadLimiter()
    private var channels = 0
    private val curveEqualizer = CurveEqualizer()
    private var exciters: List<BassExciter> = emptyList()
    private var exciterSettings = BassExciterSettings()
    private var frame = FloatArray(0)
    private var ditherState = 0x2545F491

    fun updateSettings(newSettings: PlayerDspSettings) {
        // A limiter that was off holds stale samples: start it fresh.
        if (newSettings.limiterEnabled && !settings.limiterEnabled) limiter.reset()
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

    /** Virtual bass amount, 0 = off, 1 = full. */
    fun updateVirtualBass(mix: Float) {
        val next = BassExciterSettings(enabled = mix > 0f, mix = mix)
        if (next == exciterSettings) return
        exciterSettings = next
        exciters.forEach { it.updateSettings(next) }
    }

    // Call whenever the channel layout changes.
    fun configure(channelCount: Int) {
        channels = channelCount.coerceAtLeast(1)
        limiter = LookaheadLimiter(channelCount = channels)
        exciters = List(channels) { BassExciter(exciterSettings) }
        frame = FloatArray(channels)
        monoSummer.reset()
        curveEqualizer.configure(channels)
    }

    // A seek or track change: drop the filters' and limiter's memory of the old audio.
    fun reset() {
        monoSummer.reset()
        limiter.reset()
        exciters.forEach { it.reset() }
        curveEqualizer.reset()
    }

    fun process(
        samples: ShortArray,
        offset: Int,
        sampleCount: Int,
        sampleRateHz: Int,
    ) {
        val current = settings
        if (channels == 0) return
        val rate = sampleRateHz.toFloat()
        val curveActive = curveEqualizer.beginBuffer(rate)
        val exciterActive = exciterSettings.enabled
        if (!(curveActive || exciterActive || current.monoBassEnabled || current.limiterEnabled)) return
        val monoActive = current.monoBassEnabled && channels == 2
        val frame = frame
        var i = offset
        val end = offset + sampleCount
        while (i + channels <= end) {
            for (c in 0 until channels) {
                var sample = samples[i + c] / PCM16_SCALE
                if (curveActive) sample = curveEqualizer.process(c, sample)
                if (exciterActive) sample = exciters[c].processUnclamped(sample, rate)
                frame[c] = sample
            }
            if (curveActive) curveEqualizer.endFrame()
            if (monoActive) {
                val mixed = monoSummer.process(frame[0], frame[1], rate)
                frame[0] = mixed.left
                frame[1] = mixed.right
            }
            if (current.limiterEnabled) limiter.processFrame(frame, rate)
            for (c in 0 until channels) samples[i + c] = toPcm16(frame[c])
            i += channels
        }
    }

    // Triangular (TPDF) dither of +-1 LSB, then rounding: the rounding error becomes a
    // constant, signal-independent noise floor at -96 dBFS instead of distortion.
    private fun toPcm16(value: Float): Short {
        val dithered = value * PCM16_SCALE + (nextUniform() - nextUniform())
        return Math.round(dithered).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    // xorshift32: cheap, allocation-free and good enough for dither noise. 0..1.
    private fun nextUniform(): Float {
        var x = ditherState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        ditherState = x
        return (x ushr 8) * INV_2_POW_24
    }

    private companion object {
        const val PCM16_SCALE = 32768f
        const val INV_2_POW_24 = 1f / 16_777_216f
    }
}
