package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint

// The PCM16 adapter around PlayerDspChain: interleaved 16-bit audio in, the chain's stages,
// 16-bit audio out again. The fallback for an output that takes 16-bit only (roadmap 09,
// P9-1); the player's normal path stays float (PlayerDspChain.processFloat /
// processPcm16ToFloat) and never rounds back to 16 bits.
// The player's whole audio chain on interleaved PCM16, in one float pass:
// 1. target curve and subsonic high-pass (CurveEqualizer), whose pre-gain makes room
//    for a share of the strongest boost (all of it while the limiter is off),
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
// The result goes back to PCM16 once, with TPDF dither, so quiet passages after a large
// pre-gain cut keep their detail instead of turning into rounding distortion. With every
// stage off the buffer is left untouched (bit for bit, no dither).
class PlayerDspPcm16(
    val chain: PlayerDspChain = PlayerDspChain(),
) {
    private val dither = TpdfDither()

    fun updateSettings(newSettings: PlayerDspSettings) = chain.updateSettings(newSettings)
class PlayerDspPcm16 {
    private var settings = PlayerDspSettings()
    private val monoSummer = BassMonoSummer()
    private var limiter = LookaheadLimiter()
    private var channels = 0
    private val curveEqualizer = CurveEqualizer()
    private var exciters: List<BassExciter> = emptyList()
    private var exciterSettings = BassExciterSettings()

    // The curve as last handed in; set again on the equalizer when the limiter is switched,
    // because the headroom it may leave out depends on it.
    private var curve: List<TargetPoint> = emptyList()
    private var subsonicCutoffHz = 0f
    private var headroomSafetyRatio = CurveEqualizer.DEFAULT_HEADROOM_SAFETY_RATIO

    // One array per channel, BLOCK_FRAMES long: the audio is taken apart into these, runs
    // through the stages there and is put back together. Allocated once per channel layout.
    private var block: Array<DoubleArray> = emptyArray()
    private var ditherState = 0x2545F491

    fun updateSettings(newSettings: PlayerDspSettings) {
        // A limiter that was off holds stale samples: start it fresh.
        if (newSettings.limiterEnabled && !settings.limiterEnabled) limiter.reset()
        val limiterSwitched = newSettings.limiterEnabled != settings.limiterEnabled
        settings = newSettings
        if (limiterSwitched) applyCurve()
        monoSummer.updateSettings(
            BassMonoSummerSettings(enabled = newSettings.monoBassEnabled, cutoffHz = newSettings.monoBassCutoffHz),
        )
    }

    /**
     * The exact target curve (without the subsonic cut) and the subsonic cutoff; empty and 0 = off.
     * [headroomSafetyRatio] is the share of the strongest boost the curve's pre-gain takes back
     * (see CurveEqualizer); the limiter catches the rest. With the limiter off nothing would, so
     * then the whole boost is taken back and the curve can never clip.
     */
    fun updateCurve(
        curve: List<TargetPoint>,
        subsonicCutoffHz: Float,
    ) = chain.updateCurve(curve, subsonicCutoffHz)
        headroomSafetyRatio: Float = CurveEqualizer.DEFAULT_HEADROOM_SAFETY_RATIO,
    ) {
        this.curve = curve
        this.subsonicCutoffHz = subsonicCutoffHz
        this.headroomSafetyRatio = headroomSafetyRatio
        applyCurve()
    }

    private fun applyCurve() {
        val ratio = if (settings.limiterEnabled) headroomSafetyRatio else 1f
        curveEqualizer.setCurve(curve, subsonicCutoffHz, ratio)
    }

    /** Virtual bass amount, 0 = off, 1 = full. */
    fun updateVirtualBass(mix: Float) = chain.updateVirtualBass(mix)

    // Call whenever the channel layout changes.
    fun configure(channelCount: Int) = chain.configure(channelCount)

    // A seek or track change: drop the filters' and limiter's memory of the old audio.
    fun reset() = chain.reset()

    fun process(
        samples: ShortArray,
        offset: Int,
        sampleCount: Int,
        sampleRateHz: Int,
    ) {
        if (!chain.beginBuffer(sampleRateHz)) return
        val channels = chain.channelCount
        val block = chain.block
        var position = offset
        var framesLeft = sampleCount / channels
        while (framesLeft > 0) {
            val frames = minOf(framesLeft, PlayerDspChain.BLOCK_FRAMES)
            for (c in 0 until channels) {
                val channel = block[c]
                var i = position + c
                for (n in 0 until frames) {
                    channel[n] = (samples[i] / PlayerDspChain.PCM16_SCALE).toDouble()
                    i += channels
                }
            }
            chain.processBlock(frames)
            // Frame by frame, channel by channel: the order the dither noise is drawn in.
            var i = position
            for (n in 0 until frames) {
                for (c in 0 until channels) samples[i + c] = dither.toPcm16(block[c][n].toFloat())
                i += channels
            }
            position += frames * channels
            framesLeft -= frames
        }
    }
}

// Triangular (TPDF) dither of +-1 LSB, then rounding: the rounding error becomes a constant,
// signal-independent noise floor at -96 dBFS instead of distortion. Allocation-free; one
// instance per stream (it carries the noise generator's state).
class TpdfDither {
    private var state = 0x2545F491

    /** -1..1 (full scale) to a 16-bit sample, dithered and clipped to the 16-bit range. */
    fun toPcm16(value: Float): Short {
        val dithered = value * PlayerDspChain.PCM16_SCALE + (nextUniform() - nextUniform())
        return Math.round(dithered).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    // xorshift32: cheap, allocation-free and good enough for dither noise. 0..1.
    private fun nextUniform(): Float {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return (x ushr 8) * INV_2_POW_24
    }

    private companion object {
        const val INV_2_POW_24 = 1f / 16_777_216f
    }
}
