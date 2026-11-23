package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * The player's exact target curve as a chain of biquads, plus a real high-pass for the
 * subsonic cut. Android-free; the Media3 processor that feeds it lives in :player.
 *
 * The curve is realised by peaking filters on the 29 ISO third-octave centres from 25 Hz to
 * 16 kHz, plus a low shelf at the lowest and a high shelf at the highest centre that carry the
 * level beyond them (roadmap 10, P10-1). Their gains are solved by least squares on a grid
 * three times as dense as the bands, so the whole chain follows the curve between the centres
 * too: the built-in presets within 0.5 dB from 25 Hz to 16 kHz (the 14 peaks before: 1.3 dB).
 * Every filter is a minimum-phase biquad: no block transform, so no pre-echo before a kick
 * and no latency, unlike an FFT equalizer with the same resolution.
 *
 * Free parametric filters (peak, shelves) can be handed in on their own: they are then run
 * exactly as set (roadmap 10, P10-2) instead of being smeared onto the third-octave grid.
 * The curve passed in must then not contain them a second time.
 *
 * The subsonic cut is two cascaded 2nd-order high-passes (24 dB/octave) and is *not* part
 * of the curve passed in.
 *
 * The curve is set from any thread; the coefficients are (re)designed lazily on the audio
 * thread at the start of the next buffer, so they are never recomputed per buffer.
 * Bands whose solved gain is below [SKIP_GAIN_DB] are not computed at all, only their
 * filter memory follows the audio, so a mostly flat curve costs almost nothing.
 *
 * A new curve while music plays (dragging a point) must not click: the filters keep their
 * memory across a redesign, and the headroom pre-gain glides to its new value over
 * [GAIN_RAMP_MS] instead of jumping.
 *
 * The pre-gain takes back only a share of the strongest boost (the headroom safety ratio),
 * the same share the system path gives up for other apps (EqController's input gain): taking
 * back all of it left the own player 6-9 dB quieter than Spotify with the same bass preset,
 * and starved the dynamics behind it. What the remaining boost pushes over full scale is
 * caught by the player's lookahead limiter (PlayerDspPcm16).
 */
class CurveEqualizer {
    private class Request(
        val curve: List<TargetPoint>,
        val subsonicCutoffHz: Float,
        val headroomSafetyRatio: Float,
        val parametric: List<ParametricFilter>,
    )

    private class Design(
        val request: Request,
        val sampleRateHz: Float,
        val highPass: BiquadCoefficients?,
        // One entry per third-octave centre below Nyquist; null = skipped (about 0 dB).
        val bands: Array<BiquadCoefficients?>,
        // One entry per parametric filter of the request; null = skipped.
        val direct: Array<BiquadCoefficients?>,
        val preGain: Float,
    ) {
        fun activeFilters(): List<BiquadCoefficients> = bands.filterNotNull() + direct.filterNotNull()
    }

    // One channel's filter memory: the two high-pass stages, one state per band and one per
    // parametric filter.
    private class ChannelState(
        bandCount: Int,
        directCount: Int,
    ) {
        val highPass = listOf(BiquadFilterState(), BiquadFilterState())
        val bands = List(bandCount) { BiquadFilterState() }
        var direct = List(directCount) { BiquadFilterState() }

        // Follows the chain's output, so a parametric filter added during playback starts from
        // the audio running past instead of from silence (which would be a small click).
        val output = BiquadFilterState()
    }

    @Volatile private var request: Request? = null
    private var design: Design? = null
    private var channelCount = 0
    private var states: List<ChannelState> = emptyList()

    // The pre-gain actually applied, gliding towards design.preGain one step per frame.
    private var appliedPreGain = Float.NaN
    private var preGainStep = 0f

    /**
     * Empty curve and a cutoff of 0 switch the equalizer off (the audio is left untouched).
     * [headroomSafetyRatio] (0..1) is the share of the strongest boost the pre-gain takes back:
     * 1 = all of it (never louder than the input), 0 = none.
     * [parametricFilters] run exactly as given, after the curve; bypassed ones are ignored.
     */
    fun setCurve(
        curve: List<TargetPoint>,
        subsonicCutoffHz: Float,
        headroomSafetyRatio: Float = DEFAULT_HEADROOM_SAFETY_RATIO,
        parametricFilters: List<ParametricFilter> = emptyList(),
    ) {
        val flat = curve.all { it.gainDb == 0f }
        val parametric = parametricFilters.filter { !it.bypassed }.map(ParametricFilterBounds::clamp)
        val parametricFlat = parametric.all { isGainType(it.type) && abs(it.gainDb) < SKIP_GAIN_DB }
        request =
            if (flat && parametricFlat && subsonicCutoffHz <= 0f) {
                null
            } else {
                Request(curve.sortedBy { it.frequencyHz }, subsonicCutoffHz, headroomSafetyRatio.coerceIn(0f, 1f), parametric)
            }
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

    /** How many biquads the current design really computes per sample and channel; for tests. */
    internal val activeFilterCount: Int
        get() {
            val current = design ?: return 0
            val highPass = if (current.highPass != null) 2 else 0
            return highPass + current.bands.count { it != null } + current.direct.count { it != null }
        }

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
            if (states.size != channelCount || states.firstOrNull()?.bands?.size != fresh.bands.size) {
                states = List(channelCount) { ChannelState(fresh.bands.size, fresh.direct.size) }
            } else if (states.first().direct.size != fresh.direct.size) {
                // A parametric filter added or removed: the others keep their memory.
                states.forEach { channel ->
                    val old = channel.direct
                    channel.direct = List(fresh.direct.size) { old.getOrNull(it) ?: channel.output.copy() }
                }
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
        for (i in current.bands.indices) {
            val coefficients = current.bands[i]
            if (coefficients != null) value = chain.bands[i].process(value, coefficients) else chain.bands[i].follow(value)
        }
        for (i in current.direct.indices) {
            val coefficients = current.direct[i]
            if (coefficients != null) value = chain.direct[i].process(value, coefficients) else chain.direct[i].follow(value)
        }
        chain.output.follow(value)
        return value
    }

    /**
     * The first [frames] samples of every channel through the chain, in place: the block form of
     * [process] followed by [endFrame] for each frame, with the same result. One array per channel.
     * Only valid after [beginBuffer] returned true.
     */
    fun processBlock(
        channels: Array<DoubleArray>,
        frames: Int,
    ) {
        val current = design ?: return
        val target = current.preGain
        val startGain = appliedPreGain
        val startStep = preGainStep
        var endGain = startGain
        var endStep = startStep
        val highPass = current.highPass
        val bands = current.bands
        val direct = current.direct
        for (c in channels.indices) {
            if (c >= states.size) break
            val samples = channels[c]
            // The pre-gain, gliding exactly as endFrame() moves it; every channel gets the same ramp.
            var gain = startGain
            var step = startStep
            for (n in 0 until frames) {
                samples[n] = samples[n] * gain
                if (step != 0f) {
                    gain += step
                    if ((step > 0f && gain >= target) || (step < 0f && gain <= target)) {
                        gain = target
                        step = 0f
                    }
                }
            }
            endGain = gain
            endStep = step
        }
        // Stereo, the normal case: both channels through each filter together.
        var c = 0
        while (c + 1 < channels.size && c + 1 < states.size) {
            val left = states[c]
            val right = states[c + 1]
            val leftSamples = channels[c]
            val rightSamples = channels[c + 1]
            if (highPass != null) {
                BiquadFilterState.processBlockPair(left.highPass[0], leftSamples, right.highPass[0], rightSamples, frames, highPass)
                BiquadFilterState.processBlockPair(left.highPass[1], leftSamples, right.highPass[1], rightSamples, frames, highPass)
            }
            for (i in bands.indices) {
                processPair(bands[i], left.bands[i], leftSamples, right.bands[i], rightSamples, frames)
            }
            for (i in direct.indices) {
                processPair(direct[i], left.direct[i], leftSamples, right.direct[i], rightSamples, frames)
            }
            left.output.followBlock(leftSamples, frames)
            right.output.followBlock(rightSamples, frames)
            c += 2
        }
        // Mono, or the last channel of an odd layout.
        if (c < channels.size && c < states.size) {
            val chain = states[c]
            val samples = channels[c]
            if (highPass != null) {
                chain.highPass[0].processBlock(samples, frames, highPass)
                chain.highPass[1].processBlock(samples, frames, highPass)
            }
            for (i in bands.indices) processSingle(bands[i], chain.bands[i], samples, frames)
            for (i in direct.indices) processSingle(direct[i], chain.direct[i], samples, frames)
            chain.output.followBlock(samples, frames)
        }
        appliedPreGain = endGain
        preGainStep = endStep
    }

    private fun processPair(
        coefficients: BiquadCoefficients?,
        left: BiquadFilterState,
        leftSamples: DoubleArray,
        right: BiquadFilterState,
        rightSamples: DoubleArray,
        frames: Int,
    ) {
        if (coefficients != null) {
            BiquadFilterState.processBlockPair(left, leftSamples, right, rightSamples, frames, coefficients)
        } else {
            left.followBlock(leftSamples, frames)
            right.followBlock(rightSamples, frames)
        }
    }

    private fun processSingle(
        coefficients: BiquadCoefficients?,
        state: BiquadFilterState,
        samples: DoubleArray,
        frames: Int,
    ) {
        if (coefficients != null) state.processBlock(samples, frames, coefficients) else state.followBlock(samples, frames)
    }

    /** The designed chain's response (without the headroom pre-gain) in dB; for tests. */
    internal fun chainResponseDb(freqHz: Float): Float {
        val current = design ?: return 0f
        val chain = current.activeFilters() + listOfNotNull(current.highPass, current.highPass)
        val grid = ResponseGrid(doubleArrayOf(freqHz.toDouble()), current.sampleRateHz)
        return chain.sumOf { grid.responseDb(it, 0) }.toFloat()
    }

    /** A seek or track change: drop the filters' memory of the old audio. */
    fun reset() {
        states.forEach { channel ->
            channel.highPass.forEach { it.reset() }
            channel.bands.forEach { it.reset() }
            channel.direct.forEach { it.reset() }
            channel.output.reset()
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
        // Filters at or above Nyquist cannot be designed; they only matter at low rates.
        val limitHz = sampleRateHz / 2f * NYQUIST_MARGIN
        val centres = CENTERS_HZ.filter { it < limitHz }
        // Low shelf, a peak per centre, high shelf: one fixed layout per sample rate, so the
        // filter memory survives every new curve. The shelves carry the curve's level beyond the
        // outermost centres (a bass shelf, a treble tilt).
        val bands =
            if (centres.isEmpty()) {
                emptyList()
            } else {
                listOf(Band(ParametricFilterType.LOW_SHELF, centres.first())) +
                    centres.map { Band(ParametricFilterType.PEAK, it) } +
                    Band(ParametricFilterType.HIGH_SHELF, centres.last())
            }
        val gains = solveGains(request.curve, bands, sampleRateHz)
        val layout =
            Array(bands.size) { i ->
                val idle = abs(gains[i]) < SKIP_GAIN_DB
                if (idle) null else BiquadFilterDesigner.design(bands[i].filter(gains[i], sampleRateHz), sampleRateHz)
            }
        val direct =
            Array(request.parametric.size) { i ->
                val filter = request.parametric[i]
                val idle = filter.frequencyHz >= limitHz || (isGainType(filter.type) && abs(filter.gainDb) < SKIP_GAIN_DB)
                if (idle) null else BiquadFilterDesigner.design(filter, sampleRateHz)
            }
        val active = layout.filterNotNull() + direct.filterNotNull()
        val maxBoostDb = peakBoostDb(active, request.parametric.map { it.frequencyHz }, sampleRateHz)
        val preGain = 10f.pow(preGainDb(maxBoostDb, request.headroomSafetyRatio) / 20f)
        return Design(request, sampleRateHz, highPass, layout, direct, preGain)
    }

    // Weighted least squares over 20 Hz to 20 kHz, three points per band spacing: the gains g
    // minimise sum_k w_k (sum_j B[k][j] g_j - target_k)^2, where column j is band j's response in
    // dB divided by its gain. A peak's dB response is not exactly proportional to its gain (it gets wider
    // as it grows), so B is rebuilt from the gains just found and the system solved again; two
    // or three rounds settle within a hundredth of a dB. Gains that would leave +-MAX_GAIN_DB
    // are pinned there and the others solved around them. A tiny ridge keeps neighbours from
    // trading huge opposite gains on targets narrower than a third of an octave.
    private fun solveGains(
        curve: List<TargetPoint>,
        bands: List<Band>,
        sampleRateHz: Float,
    ): FloatArray {
        val bandCount = bands.size
        if (bandCount == 0 || curve.isEmpty()) return FloatArray(bandCount)
        val lowestHz = bands.first().frequencyHz
        val highestHz = bands.last().frequencyHz
        val grid = ResponseGrid(solveGridHz(lowestHz, highestHz, sampleRateHz), sampleRateHz)
        val points = grid.size
        val targets = DoubleArray(points) { k -> targetAt(grid.freqHz[k].toFloat(), curve).toDouble() }
        if (targets.all { abs(it) < SKIP_GAIN_DB }) return FloatArray(bandCount)
        // Full weight between the outermost centres, less beyond: there it only has to keep the
        // chain from running away, not to cost accuracy where the bands are.
        val weights =
            DoubleArray(points) { k ->
                val f = grid.freqHz[k]
                if (f < lowestHz || f > highestHz) OUTSIDE_WEIGHT else 1.0
            }
        var gains = DoubleArray(bandCount) { j -> targets[nearest(grid.freqHz, bands[j].frequencyHz)] / 2.0 }
        val basis = Array(bandCount) { DoubleArray(points) }
        repeat(SOLVE_ITERATIONS) {
            for (j in 0 until bandCount) {
                // The column is the response per dB at the gain just found; near 0 dB it tends to
                // the slope at 0.
                val size = abs(gains[j]).coerceAtLeast(MIN_PROTOTYPE_DB)
                val prototype = if (gains[j] < 0.0) -size else size
                val coefficients = BiquadFilterDesigner.design(bands[j].filter(prototype.toFloat(), sampleRateHz), sampleRateHz)
                val column = basis[j]
                for (k in 0 until points) column[k] = grid.responseDb(coefficients, k) / prototype
            }
            val next = boundedLeastSquares(basis, targets, weights)
            var change = 0.0
            for (j in 0 until bandCount) change = maxOf(change, abs(next[j] - gains[j]))
            gains = next
            if (change < SOLVE_TOLERANCE_DB) return FloatArray(bandCount) { gains[it].toFloat() }
        }
        return FloatArray(bandCount) { gains[it].toFloat() }
    }

    private fun boundedLeastSquares(
        basis: Array<DoubleArray>,
        targets: DoubleArray,
        weights: DoubleArray,
    ): DoubleArray {
        val bandCount = basis.size
        val points = targets.size
        val gains = DoubleArray(bandCount)
        val pinned = BooleanArray(bandCount)
        repeat(bandCount) {
            val free = (0 until bandCount).filter { !pinned[it] }
            if (free.isEmpty()) return gains
            val residual = targets.copyOf()
            for (j in 0 until bandCount) {
                if (!pinned[j]) continue
                val column = basis[j]
                for (k in 0 until points) residual[k] -= column[k] * gains[j]
            }
            val size = free.size
            val normal = Array(size) { DoubleArray(size) }
            val rhs = DoubleArray(size)
            for (a in 0 until size) {
                val columnA = basis[free[a]]
                for (b in a until size) {
                    val columnB = basis[free[b]]
                    var sum = 0.0
                    for (k in 0 until points) sum += weights[k] * columnA[k] * columnB[k]
                    normal[a][b] = sum
                    normal[b][a] = sum
                }
                normal[a][a] += RIDGE
                var sum = 0.0
                for (k in 0 until points) sum += weights[k] * columnA[k] * residual[k]
                rhs[a] = sum
            }
            val solved = solveSymmetric(normal, rhs)
            var newlyPinned = false
            for (a in 0 until size) {
                val j = free[a]
                gains[j] = solved[a]
                if (abs(solved[a]) > MAX_GAIN_DB) {
                    gains[j] = solved[a].coerceIn(-MAX_GAIN_DB.toDouble(), MAX_GAIN_DB.toDouble())
                    pinned[j] = true
                    newlyPinned = true
                }
            }
            if (!newlyPinned) return gains
        }
        return gains
    }

    private fun peakBoostDb(
        chain: List<BiquadCoefficients>,
        extraFreqsHz: List<Float>,
        sampleRateHz: Float,
    ): Float {
        if (chain.isEmpty()) return 0f
        val freqs = ArrayList<Double>()
        var f = 20.0
        while (f <= 20_000.0 && f < sampleRateHz / 2.0) {
            freqs += f
            f *= GRID_STEP
        }
        // A narrow parametric peak can sit between two grid points: look at its centre too.
        extraFreqsHz.filter { it < sampleRateHz / 2f }.forEach { freqs += it.toDouble() }
        val grid = ResponseGrid(freqs.toDoubleArray(), sampleRateHz)
        var max = 0.0
        for (k in 0 until grid.size) {
            var db = 0.0
            for (coefficients in chain) db += grid.responseDb(coefficients, k)
            if (db > max) max = db
        }
        return max.toFloat()
    }

    // One filter of the curve's chain whose gain the solver sets.
    private class Band(
        val type: ParametricFilterType,
        val frequencyHz: Float,
    ) {
        fun filter(
            gainDb: Float,
            sampleRateHz: Float,
        ): ParametricFilter {
            val q = if (type == ParametricFilterType.PEAK) warpedQ(frequencyHz, sampleRateHz) else SHELF_Q
            return ParametricFilter(type, frequencyHz, gainDb, q)
        }
    }

    // |H| of a biquad at fixed frequencies, in double precision: in single precision the
    // denominator near z = 1 (the 25 Hz band) cancels down to noise.
    private class ResponseGrid(
        val freqHz: DoubleArray,
        sampleRateHz: Float,
    ) {
        val size = freqHz.size
        private val cos1 = DoubleArray(size)
        private val sin1 = DoubleArray(size)
        private val cos2 = DoubleArray(size)
        private val sin2 = DoubleArray(size)

        init {
            for (k in 0 until size) {
                val w = 2.0 * PI * freqHz[k] / sampleRateHz
                cos1[k] = cos(w)
                sin1[k] = sin(w)
                cos2[k] = cos(2.0 * w)
                sin2[k] = sin(2.0 * w)
            }
        }

        fun responseDb(
            coefficients: BiquadCoefficients,
            k: Int,
        ): Double {
            val b0 = coefficients.b0.toDouble()
            val b1 = coefficients.b1.toDouble()
            val b2 = coefficients.b2.toDouble()
            val a1 = coefficients.a1.toDouble()
            val a2 = coefficients.a2.toDouble()
            val numeratorReal = b0 + b1 * cos1[k] + b2 * cos2[k]
            val numeratorImag = -(b1 * sin1[k] + b2 * sin2[k])
            val denominatorReal = 1.0 + a1 * cos1[k] + a2 * cos2[k]
            val denominatorImag = -(a1 * sin1[k] + a2 * sin2[k])
            val numerator = numeratorReal * numeratorReal + numeratorImag * numeratorImag
            val denominator = denominatorReal * denominatorReal + denominatorImag * denominatorImag
            if (denominator <= 0.0) return 0.0
            return 10.0 * log10((numerator / denominator).coerceAtLeast(1e-24))
        }
    }

    companion object {
        /** The system path's share for headphones and most outputs (EqController's INPUT_GAIN_SAFETY_RATIO). */
        const val DEFAULT_HEADROOM_SAFETY_RATIO = 0.5f

        /** The headroom pre-gain in dB for a chain whose strongest boost is [maxBoostDb]. */
        fun preGainDb(
            maxBoostDb: Float,
            headroomSafetyRatio: Float,
        ): Float {
            val db = -(maxBoostDb.coerceAtLeast(0f) * headroomSafetyRatio.coerceIn(0f, 1f))
            // Never -0.0f: boxed Floats (assertEquals, data classes) tell it apart from 0.0f.
            return if (db == 0f) 0f else db
        }

        /** ISO 266 third-octave centres, 25 Hz to 16 kHz: 29 bands. */
        val CENTERS_HZ: List<Float> =
            listOf(
                25f,
                31.5f,
                40f,
                50f,
                63f,
                80f,
                100f,
                125f,
                160f,
                200f,
                250f,
                315f,
                400f,
                500f,
                630f,
                800f,
                1000f,
                1250f,
                1600f,
                2000f,
                2500f,
                3150f,
                4000f,
                5000f,
                6300f,
                8000f,
                10000f,
                12500f,
                16000f,
            )

        // Wider than a strict third octave (Q 4.3): neighbours overlap by about half, so a
        // smooth curve needs no ripple between the centres, and the least squares fit still
        // follows features down to a third of an octave.
        const val BAND_Q = 2.5f
        const val MAX_GAIN_DB = 15f
        private const val SHELF_Q = 0.7071f

        /** Bands and parametric filters with less than this are not computed (the fast path). */
        const val SKIP_GAIN_DB = 0.05f

        private const val BUTTERWORTH_Q = 0.7071f
        private const val SOLVE_ITERATIONS = 4
        private const val SOLVE_TOLERANCE_DB = 0.01
        private const val SOLVE_POINTS_PER_BAND = 3
        private const val SOLVE_GRID_MIN_HZ = 20.0
        private const val SOLVE_GRID_MAX_HZ = 20_000.0
        private const val OUTSIDE_WEIGHT = 0.25
        private const val MIN_PROTOTYPE_DB = 1.0
        private const val RIDGE = 1e-3
        private const val NYQUIST_MARGIN = 0.9f
        private const val GRID_STEP = 1.05
        private const val GAIN_RAMP_MS = 30f

        // The bilinear transform squeezes a peak towards Nyquist: at 16 kHz and 48 kHz a Q of 2.5
        // comes out about a third narrower than at 1 kHz, and the top bands stop overlapping. The
        // cookbook's bandwidth form undoes that (alpha = sin w0 * sinh(ln2 / 2 * BW * w0 / sin w0)),
        // so every band keeps the same width in octaves; in the bass it is the plain Q.
        private fun warpedQ(
            centreHz: Float,
            sampleRateHz: Float,
        ): Float {
            val w0 = 2.0 * PI * centreHz / sampleRateHz
            val octaves = 2.0 / ln(2.0) * asinh(1.0 / (2.0 * BAND_Q))
            return (1.0 / (2.0 * sinh(ln(2.0) / 2.0 * octaves * w0 / sin(w0)))).toFloat()
        }

        private fun targetAt(
            freqHz: Float,
            curve: List<TargetPoint>,
        ): Float = if (curve.isEmpty()) 0f else EqualizerInterpolator.interpolateFrequency(freqHz, curve)

        private fun nearest(
            freqsHz: DoubleArray,
            freqHz: Float,
        ): Int = freqsHz.indices.minBy { abs(freqsHz[it] - freqHz) }

        private fun isGainType(type: ParametricFilterType) =
            type == ParametricFilterType.PEAK || type == ParametricFilterType.LOW_SHELF || type == ParametricFilterType.HIGH_SHELF

        // The audible range, 20 Hz to 20 kHz (or as far as the sample rate allows), three points
        // per band spacing: also above the top band, where nothing else would keep the chain
        // from overshooting the curve.
        private fun solveGridHz(
            lowestHz: Float,
            highestHz: Float,
            sampleRateHz: Float,
        ): DoubleArray {
            val first = minOf(SOLVE_GRID_MIN_HZ, lowestHz.toDouble())
            val last = maxOf(minOf(SOLVE_GRID_MAX_HZ, sampleRateHz / 2.0 * NYQUIST_MARGIN), highestHz.toDouble())
            val step = 2.0.pow(1.0 / (3.0 * SOLVE_POINTS_PER_BAND))
            val freqs = ArrayList<Double>()
            var f = first
            while (f <= last * 1.0001) {
                freqs += f
                f *= step
            }
            return freqs.toDoubleArray()
        }

        // Cholesky for the symmetric positive definite normal equations (the ridge makes sure).
        private fun solveSymmetric(
            matrix: Array<DoubleArray>,
            rhs: DoubleArray,
        ): DoubleArray {
            val n = rhs.size
            val lower = Array(n) { DoubleArray(n) }
            for (i in 0 until n) {
                for (j in 0..i) {
                    var sum = matrix[i][j]
                    for (k in 0 until j) sum -= lower[i][k] * lower[j][k]
                    lower[i][j] = if (i == j) sqrt(sum.coerceAtLeast(1e-12)) else sum / lower[j][j]
                }
            }
            val y = DoubleArray(n)
            for (i in 0 until n) {
                var sum = rhs[i]
                for (k in 0 until i) sum -= lower[i][k] * y[k]
                y[i] = sum / lower[i][i]
            }
            val x = DoubleArray(n)
            for (i in n - 1 downTo 0) {
                var sum = y[i]
                for (k in i + 1 until n) sum -= lower[k][i] * x[k]
                x[i] = sum / lower[i][i]
            }
            return x
        }
    }
}
