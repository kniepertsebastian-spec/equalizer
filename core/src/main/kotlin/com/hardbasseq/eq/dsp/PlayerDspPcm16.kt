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
// PCM16 out: the chain was built when Media3's speed processor (Sonic, behind this one)
// accepted 16-bit only (Media3 1.3.1). That is outdated: Media3 1.11.1's SonicAudioProcessor
// takes PCM16 and float (checked in its configure()), so a float output is possible. Whether
// it is worth the change is open (roadmap item P4-7); until then the result goes back to PCM16.
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

    // One array per channel, BLOCK_FRAMES long: the audio is taken apart into these, runs
    // through the stages there and is put back together. Allocated once per channel layout.
    private var block: Array<DoubleArray> = emptyArray()
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
        block = Array(channels) { DoubleArray(BLOCK_FRAMES) }
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
        val block = block
        var position = offset
        var framesLeft = sampleCount / channels
        // Block by block and, inside a block, stage by stage: every filter runs over a short
        // array with its state in local variables. Going through all stages sample by sample
        // instead cost several times as much processor time and created garbage for every
        // frame - enough to make the music stutter once the screen was locked and the phone
        // had moved the player to its slow cores.
        while (framesLeft > 0) {
            val frames = minOf(framesLeft, BLOCK_FRAMES)
            for (c in 0 until channels) {
                val channel = block[c]
                var i = position + c
                for (n in 0 until frames) {
                    channel[n] = (samples[i] / PCM16_SCALE).toDouble()
                    i += channels
                }
            }
            if (curveActive) curveEqualizer.processBlock(block, frames)
            if (exciterActive) {
                for (c in 0 until channels) exciters[c].processBlock(block[c], frames, rate)
            }
            if (monoActive) monoSummer.processBlock(block[0], block[1], frames, rate)
            if (current.limiterEnabled) limiter.processBlock(block, frames, rate)
            // Frame by frame, channel by channel: the order the dither noise is drawn in.
            var i = position
            for (n in 0 until frames) {
                for (c in 0 until channels) samples[i + c] = toPcm16(block[c][n].toFloat())
                i += channels
            }
            position += frames * channels
            framesLeft -= frames
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
        // Short enough that a block and the stages' working arrays stay in the processor's
        // first-level cache, long enough that the per-block overhead does not matter.
        const val BLOCK_FRAMES = 512
        const val PCM16_SCALE = 32768f
        const val INV_2_POW_24 = 1f / 16_777_216f
    }
}
