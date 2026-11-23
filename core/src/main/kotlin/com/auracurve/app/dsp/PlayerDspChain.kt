package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint
import kotlin.math.pow

// The player's whole audio chain, independent of the sample format, in one floating-point pass:
// 1. target curve and subsonic high-pass (CurveEqualizer), whose pre-gain makes room
//    for the strongest boost,
// 2. virtual bass (BassExciter) - after the pre-gain, so its harmonics fit in,
// 3. mono bass (stereo only),
// 4. output drive (0 to +4 dB make-up gain, roadmap 09 P9-3),
// 5. soft clipper (SoftClipper): linear below -2.5 dBFS, a tanh bend toward 0.98 above,
// 6. a lookahead limiter linked across the channels as the safety ceiling (-0.2 dBFS,
//    2 ms lookahead), so neither the boosts nor the drive can clip the DAC.
// Nothing is clipped or rounded between the stages. Each part can be switched off on its
// own; with everything off the audio is left untouched.
//
// Who limits what (roadmap 09, P9-2, way A): the system-wide DynamicsProcessing that
// AuraCurve attaches to the player's session (multiband compressor + its 2 ms limiter at the
// preset's limiterThresholdDb) does the loudness work. This chain only rounds the peaks off
// (soft clipper) and keeps a fixed ceiling just under full scale; it used to run a second
// brickwall limiter at -1 dBFS with 5 ms lookahead in front of the system one, which pulled
// every kick down 5 ms before it arrived.
//
// Formats: processFloat() works on interleaved 32-bit floats in place, the player's normal
// path since roadmap 09 (P9-1) - the result stays float all the way to the AudioTrack, with
// no dither and no 16-bit clipping before DynamicsProcessing. processPcm16ToFloat() is the
// same with 16-bit input. PlayerDspPcm16 is the adapter for 16-bit output (with TPDF dither).
// Android-free like the rest of :core - the Media3 processor that feeds it lives in :player.
data class PlayerDspSettings(
    val monoBassEnabled: Boolean = true,
    val monoBassCutoffHz: Float = DEFAULT_MONO_BASS_CUTOFF_HZ,
    val limiterEnabled: Boolean = true,
    val softClipEnabled: Boolean = true,
    val outputDriveDb: Float = 0f,
) {
    companion object {
        const val DEFAULT_MONO_BASS_CUTOFF_HZ = 120f
        const val MIN_OUTPUT_DRIVE_DB = 0f
        const val MAX_OUTPUT_DRIVE_DB = 4f

        /** The safety ceiling: just under full scale, short lookahead so kicks stay crisp. */
        val SAFETY_CEILING = LookaheadLimiterSettings(enabled = true, thresholdDb = -0.2f, lookaheadMs = 2f)
    }
}

class PlayerDspChain {
    private var settings = PlayerDspSettings()
    private val monoSummer = BassMonoSummer()
    private var limiter = LookaheadLimiter(PlayerDspSettings.SAFETY_CEILING)
    private val softClipper = SoftClipper()
    private var driveGain = 1.0
    private var channels = 0
    private val curveEqualizer = CurveEqualizer()
    private var exciters: List<BassExciter> = emptyList()
    private var exciterSettings = BassExciterSettings()

    // Which stages run for the current buffer, decided once per buffer in beginBuffer().
    private var rate = 0f
    private var curveActive = false
    private var exciterActive = false
    private var monoActive = false
    private var driveActive = false
    private var softClipActive = false
    private var limiterActive = false

    /**
     * One array per channel, [BLOCK_FRAMES] long: the audio is taken apart into these, runs
     * through the stages there ([processBlock]) and is put back together. Allocated once per
     * channel layout.
     */
    internal var block: Array<DoubleArray> = emptyArray()
        private set

    val channelCount: Int get() = channels

    fun updateSettings(newSettings: PlayerDspSettings) {
        // A limiter that was off holds stale samples: start it fresh.
        if (newSettings.limiterEnabled && !settings.limiterEnabled) limiter.reset()
        settings = newSettings
        driveGain =
            dbToGain(newSettings.outputDriveDb.coerceIn(PlayerDspSettings.MIN_OUTPUT_DRIVE_DB, PlayerDspSettings.MAX_OUTPUT_DRIVE_DB))
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
        limiter = LookaheadLimiter(PlayerDspSettings.SAFETY_CEILING, channelCount = channels)
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

    /**
     * Starts a buffer at [sampleRateHz]: picks up a new curve and decides which stages run.
     * False = nothing would touch the audio; then [processBlock] must not be called for it.
     */
    fun beginBuffer(sampleRateHz: Int): Boolean {
        if (channels == 0) return false
        val current = settings
        rate = sampleRateHz.toFloat()
        curveActive = curveEqualizer.beginBuffer(rate)
        exciterActive = exciterSettings.enabled
        monoActive = current.monoBassEnabled && channels == 2
        driveActive = driveGain != 1.0
        softClipActive = current.softClipEnabled
        limiterActive = current.limiterEnabled
        return curveActive || exciterActive || monoActive || driveActive || softClipActive || limiterActive
    }

    /** The first [frames] frames of [block] through every active stage, in place. */
    fun processBlock(frames: Int) {
        val block = block
        // Block by block and, inside a block, stage by stage: every filter runs over a short
        // array with its state in local variables. Going through all stages sample by sample
        // instead cost several times as much processor time and created garbage for every
        // frame - enough to make the music stutter once the screen was locked and the phone
        // had moved the player to its slow cores.
        if (curveActive) curveEqualizer.processBlock(block, frames)
        if (exciterActive) {
            for (c in 0 until channels) exciters[c].processBlock(block[c], frames, rate)
        }
        if (monoActive) monoSummer.processBlock(block[0], block[1], frames, rate)
        if (driveActive) {
            val gain = driveGain
            for (c in 0 until channels) {
                val channel = block[c]
                for (n in 0 until frames) channel[n] *= gain
            }
        }
        if (softClipActive) {
            for (c in 0 until channels) softClipper.processBlock(block[c], frames)
        }
        if (limiterActive) limiter.processBlock(block, frames, rate)
    }

    /** Interleaved 32-bit float audio, [sampleCount] samples from [offset], processed in place. */
    fun processFloat(
        samples: FloatArray,
        offset: Int,
        sampleCount: Int,
        sampleRateHz: Int,
    ) {
        if (!beginBuffer(sampleRateHz)) return
        val channels = channels
        val block = block
        var position = offset
        var framesLeft = sampleCount / channels
        while (framesLeft > 0) {
            val frames = minOf(framesLeft, BLOCK_FRAMES)
            for (c in 0 until channels) {
                val channel = block[c]
                var i = position + c
                for (n in 0 until frames) {
                    channel[n] = samples[i].toDouble()
                    i += channels
                }
            }
            processBlock(frames)
            for (c in 0 until channels) {
                val channel = block[c]
                var i = position + c
                for (n in 0 until frames) {
                    samples[i] = channel[n].toFloat()
                    i += channels
                }
            }
            position += frames * channels
            framesLeft -= frames
        }
    }

    /**
     * Interleaved PCM16 in, interleaved 32-bit float out (-1..1 = full scale): the 16-bit
     * source keeps every bit, and the result is neither rounded nor clipped to 16 bits again.
     */
    fun processPcm16ToFloat(
        input: ShortArray,
        inputOffset: Int,
        output: FloatArray,
        outputOffset: Int,
        sampleCount: Int,
        sampleRateHz: Int,
    ) {
        for (n in 0 until sampleCount) output[outputOffset + n] = input[inputOffset + n] / PCM16_SCALE
        processFloat(output, outputOffset, sampleCount, sampleRateHz)
    }

    companion object {
        // Short enough that a block and the stages' working arrays stay in the processor's
        // first-level cache, long enough that the per-block overhead does not matter.
        const val BLOCK_FRAMES = 512
        internal const val PCM16_SCALE = 32768f

        private fun dbToGain(db: Float): Double = 10.0.pow(db / 20.0)
    }
}
