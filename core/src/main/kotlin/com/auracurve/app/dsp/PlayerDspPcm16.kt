package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint

// The PCM16 adapter around PlayerDspChain: interleaved 16-bit audio in, the chain's stages,
// 16-bit audio out again. The fallback for an output that takes 16-bit only (roadmap 09,
// P9-1); the player's normal path stays float (PlayerDspChain.processFloat /
// processPcm16ToFloat) and never rounds back to 16 bits.
//
// The result goes back to PCM16 once, with TPDF dither, so quiet passages after a large
// pre-gain cut keep their detail instead of turning into rounding distortion. With every
// stage off the buffer is left untouched (bit for bit, no dither).
class PlayerDspPcm16(
    val chain: PlayerDspChain = PlayerDspChain(),
) {
    private val dither = TpdfDither()

    fun updateSettings(newSettings: PlayerDspSettings) = chain.updateSettings(newSettings)

    /** The exact target curve (without the subsonic cut) and the subsonic cutoff; empty and 0 = off. */
    fun updateCurve(
        curve: List<TargetPoint>,
        subsonicCutoffHz: Float,
    ) = chain.updateCurve(curve, subsonicCutoffHz)

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
