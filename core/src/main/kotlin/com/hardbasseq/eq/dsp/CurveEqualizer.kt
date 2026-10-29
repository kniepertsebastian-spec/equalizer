package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.pow

/**
 * The player's exact target curve as a chain of biquads, plus a real high-pass for the
 * subsonic cut. Android-free; the Media3 processor that feeds it lives in :player.
 *
 * The curve is realised by peaking filters at fixed centres whose gains are solved so the
 * whole chain follows the curve between the centres too. The subsonic cut is two cascaded
 * 2nd-order high-passes (24 dB/octave) and is *not* part of the curve passed in.
 *
 * The curve is set from any thread; the coefficients are (re)designed lazily on the audio
 * thread at the start of the next buffer, so they are never recomputed per buffer.
 *
 * A new curve while music plays (dragging a point) must not click: the filters keep their
 * memory across a redesign, and the headroom pre-gain glides to its new value over
 * [GAIN_RAMP_MS] instead of jumping.
 */
class CurveEqualizer {
    private class Request(
        val curve: List<TargetPoint>,
        val subsonicCutoffHz: Float,
    )

    private class Design(
        val request: Request,
        val sampleRateHz: Float,
        val highPass: BiquadCoefficients?,
        val peaks: List<BiquadCoefficients>,
        val preGain: Float,
    )

    // One channel's filter memory: the two high-pass stages and one state per peak.
    private class ChannelState(
        peakCount: Int,
    ) {
        val highPass = listOf(BiquadFilterState(), BiquadFilterState())
        val peaks = List(peakCount) { BiquadFilterState() }
    }

    @Volatile private var request: Request? = null
    private var design: Design? = null
    private var channelCount = 0
    private var states: List<ChannelState> = emptyList()

    // The pre-gain actually applied, gliding towards design.preGain one step per frame.
    private var appliedPreGain = Float.NaN
    private var preGainStep = 0f

    /** Empty curve and a cutoff of 0 switch the equalizer off (the audio is left untouched). */
    fun setCurve(
        curve: List<TargetPoint>,
        subsonicCutoffHz: Float,
    ) {
        val flat = curve.all { it.gainDb == 0f }
        request = if (flat && subsonicCutoffHz <= 0f) null else Request(curve.sortedBy { it.frequencyHz }, subsonicCutoffHz)
    }

    /** Call whenever the channel layout changes. */
    fun configure(channels: Int) {
        channelCount = channels.coerceAtLeast(1)
        design = null
        states = emptyList()
        appliedPreGain = Float.NaN
    }

    /** Linear gain that makes room for the curve's strongest boost; valid after [beginBuffer]. */
    val preGain: Float get() = design?.preGain ?: 1f

    /** The pre-gain the next frame gets; differs from [preGain] only while it glides. */
    internal val appliedGain: Float get() = if (appliedPreGain.isNaN()) preGain else appliedPreGain

    /** Call once per buffer, on the audio thread. Returns whether the equalizer is running. */
    fun beginBuffer(sampleRateHz: Float): Boolean {
        val wanted = request
        if (wanted == null) {
            design = null
            appliedPreGain = Float.NaN
            return false
        }
        val current = design
        if (current == null || current.request !== wanted || current.sampleRateHz != sampleRateHz) {
            val fresh = design(wanted, sampleRateHz)
            design = fresh
            // Same filter layout: keep the memory, so the audio runs on without a click.
            // Only a new layout (first start, other sample rate, other channel count) starts clean.
            if (states.size != channelCount || states.firstOrNull()?.peaks?.size != fresh.peaks.size) {
                states = List(channelCount) { ChannelState(fresh.peaks.size) }
            }
            if (current == null || current.sampleRateHz != sampleRateHz || appliedPreGain.isNaN()) {
                appliedPreGain = fresh.preGain
                preGainStep = 0f
            } else {
                val rampFrames = (GAIN_RAMP_MS / 1000f * sampleRateHz).coerceAtLeast(1f)
                preGainStep = (fresh.preGain - appliedPreGain) / rampFrames
            }
            if (fresh.highPass == null) states.forEach { channel -> channel.highPass.forEach { it.reset() } }
        }
        return true
    }

    /** Call once per frame, after all channels of the frame went through [process]. */
    fun endFrame() {
        val target = design?.preGain ?: return
        if (preGainStep == 0f) return
        appliedPreGain += preGainStep
        if ((preGainStep > 0f && appliedPreGain >= target) || (preGainStep < 0f && appliedPreGain <= target)) {
            appliedPreGain = target
            preGainStep = 0f
        }
    }

    /** One sample of [channel] through the chain. Only valid after [beginBuffer] returned true. */
    fun process(
        channel: Int,
        input: Float,
    ): Float {
        val current = design ?: return input
        val chain = states.getOrNull(channel) ?: return input
        var value = input * appliedPreGain
        current.highPass?.let { coefficients -> chain.highPass.forEach { value = it.process(value, coefficients) } }
        for (i in current.peaks.indices) {
            value = chain.peaks[i].process(value, current.peaks[i])
        }
        return value
    }

    /** The designed chain's response (without the headroom pre-gain) in dB; for tests. */
    internal fun chainResponseDb(freqHz: Float): Float {
        val current = design ?: return 0f
        val chain = current.peaks + listOfNotNull(current.highPass, current.highPass)
        return totalResponseDb(chain, freqHz, current.sampleRateHz)
    }

    /** A seek or track change: drop the filters' memory of the old audio. */
    fun reset() {
        states.forEach { channel ->
            channel.highPass.forEach { it.reset() }
            channel.peaks.forEach { it.reset() }
        }
    }

    private fun design(
        request: Request,
        sampleRateHz: Float,
    ): Design {
        val highPass =
            if (request.subsonicCutoffHz > 0f) {
                BiquadFilterDesigner.design(
                    ParametricFilter(ParametricFilterType.HIGH_PASS, request.subsonicCutoffHz, 0f, BUTTERWORTH_Q),
                    sampleRateHz,
                )
            } else {
                null
            }
        val gains = solveGains(request.curve, sampleRateHz)
        // Filters at or above Nyquist cannot be designed; they only matter at low rates.
        val peaks =
            CENTERS_HZ.indices
                .filter { CENTERS_HZ[it] < sampleRateHz / 2f * NYQUIST_MARGIN }
                .map { i ->
                    BiquadFilterDesigner.design(
                        ParametricFilter(ParametricFilterType.PEAK, CENTERS_HZ[i], gains[i], qFor(i)),
                        sampleRateHz,
                    )
                }
        val maxBoostDb = peakBoostDb(peaks, sampleRateHz)
        return Design(request, sampleRateHz, highPass, peaks, 10f.pow(-maxBoostDb / 20f))
    }

    // The chain's response is close to, but not exactly, the sum of the gains, so the gains
    // are nudged towards the target a few times. Plain "gain += error" overshoots between the
    // overlapping neighbours and diverges; a damped step converges.
    private fun solveGains(
        curve: List<TargetPoint>,
        sampleRateHz: Float,
    ): List<Float> {
        val targets = CENTERS_HZ.map { EqualizerInterpolator.interpolateFrequency(it, curve) }
        val gains = targets.map { it.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB) }.toMutableList()
        repeat(SOLVE_ITERATIONS) {
            val designed =
                CENTERS_HZ.indices.map { i ->
                    BiquadFilterDesigner.design(
                        ParametricFilter(ParametricFilterType.PEAK, CENTERS_HZ[i], gains[i], qFor(i)),
                        sampleRateHz,
                    )
                }
            for (i in CENTERS_HZ.indices) {
                val actual = totalResponseDb(designed, CENTERS_HZ[i], sampleRateHz)
                gains[i] = (gains[i] + SOLVE_DAMPING * (targets[i] - actual)).coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
            }
        }
        return gains
    }

    private fun peakBoostDb(
        peaks: List<BiquadCoefficients>,
        sampleRateHz: Float,
    ): Float {
        var max = 0f
        var f = 20f
        while (f <= 20_000f && f < sampleRateHz / 2f) {
            val db = totalResponseDb(peaks, f, sampleRateHz)
            if (db > max) max = db
            f *= GRID_STEP
        }
        return max
    }

    private fun totalResponseDb(
        chain: List<BiquadCoefficients>,
        freqHz: Float,
        sampleRateHz: Float,
    ): Float {
        var sum = 0f
        for (coefficients in chain) sum += BiquadFilterDesigner.magnitudeResponseDb(coefficients, freqHz, sampleRateHz)
        return sum
    }

    // Half-octave spacing in the bass (Q ~1.4), octave spacing above 500 Hz (Q ~1.0).
    private fun qFor(index: Int): Float = if (CENTERS_HZ[index] <= 500f) BASS_Q else UPPER_Q

    companion object {
        val CENTERS_HZ: List<Float> =
            listOf(31f, 45f, 63f, 90f, 125f, 180f, 250f, 355f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        const val MAX_GAIN_DB = 15f
        private const val BASS_Q = 1.4f
        private const val UPPER_Q = 1.0f
        private const val BUTTERWORTH_Q = 0.7071f
        private const val SOLVE_ITERATIONS = 8
        private const val SOLVE_DAMPING = 0.6f
        private const val NYQUIST_MARGIN = 0.9f
        private const val GRID_STEP = 1.05f
        private const val GAIN_RAMP_MS = 30f
    }
}
