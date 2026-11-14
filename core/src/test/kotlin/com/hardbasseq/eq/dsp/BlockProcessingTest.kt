package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// The player's audio thread runs every stage over a block at a time (processBlock) instead of
// sample by sample - several times cheaper, which is what keeps the music from stuttering on the
// slow cores a phone uses once its screen is locked. These tests pin down that the block forms
// are only faster: they have to produce the signal the per-sample forms produce.
class BlockProcessingTest {
    private val rate = 44_100f

    // Bass, mids and some noise, loud enough to reach the limiter; left and right differ.
    private fun signal(
        frames: Int,
        seed: Long = 1,
    ): Array<DoubleArray> {
        val random = Random(seed)
        val left = DoubleArray(frames)
        val right = DoubleArray(frames)
        for (n in 0 until frames) {
            val t = n / rate.toDouble()
            val bass = 0.55 * sin(2 * PI * 48.0 * t)
            val mid = 0.3 * sin(2 * PI * 620.0 * t)
            // Quiet in the middle third, so gates and releases are exercised too.
            val level = if (n in frames / 3 until frames / 2) 0.02 else 1.0
            left[n] = level * (bass + mid + 0.1 * (random.nextDouble() - 0.5))
            right[n] = level * (0.8 * bass - mid + 0.1 * (random.nextDouble() - 0.5))
        }
        return arrayOf(left, right)
    }

    // The same samples as what a Float-based stage is fed.
    private fun DoubleArray.rounded() = DoubleArray(size) { this[it].toFloat().toDouble() }

    private fun assertClose(
        expected: DoubleArray,
        actual: DoubleArray,
        tolerance: Double,
    ) {
        assertEquals(expected.size, actual.size)
        var worst = 0.0
        var worstAt = 0
        for (n in expected.indices) {
            val difference = abs(expected[n] - actual[n])
            if (difference > worst) {
                worst = difference
                worstAt = n
            }
        }
        assertTrue("largest difference $worst at sample $worstAt", worst <= tolerance)
    }

    // Runs [block] over the array in pieces of changing length, like buffers arrive from a player.
    private fun inPieces(
        frames: Int,
        block: (from: Int, count: Int) -> Unit,
    ) {
        val sizes = intArrayOf(1, 7, 512, 300, 1024, 3)
        var from = 0
        var i = 0
        while (from < frames) {
            val count = minOf(sizes[i % sizes.size], frames - from)
            block(from, count)
            from += count
            i++
        }
    }

    private val lowShelfLike =
        BiquadFilterDesigner.design(ParametricFilter(ParametricFilterType.PEAK, 45f, 9f, 1.4f), rate)

    @Test
    fun `a biquad block equals the biquad sample by sample`() {
        val input = signal(6_000)[0].rounded()
        val perSample = BiquadFilterState()
        val expected = DoubleArray(input.size) { perSample.process(input[it].toFloat(), lowShelfLike).toDouble() }

        val state = BiquadFilterState()
        val actual = input.copyOf()
        inPieces(input.size) { from, count ->
            val piece = actual.copyOfRange(from, from + count)
            state.processBlock(piece, count, lowShelfLike)
            piece.copyInto(actual, from)
        }

        // The per-sample form hands each result on as a Float.
        assertClose(expected, actual, 1e-6)
    }

    @Test
    fun `a pair of biquad blocks equals each channel on its own`() {
        val input = signal(6_000)
        val alone = Array(2) { input[it].copyOf() }
        BiquadFilterState().processBlock(alone[0], alone[0].size, lowShelfLike)
        BiquadFilterState().processBlock(alone[1], alone[1].size, lowShelfLike)

        val paired = Array(2) { input[it].copyOf() }
        BiquadFilterState.processBlockPair(BiquadFilterState(), paired[0], BiquadFilterState(), paired[1], paired[0].size, lowShelfLike)

        assertArrayEquals(alone[0], paired[0], 0.0)
        assertArrayEquals(alone[1], paired[1], 0.0)
    }

    private val curve =
        listOf(TargetPoint(30f, 8f), TargetPoint(60f, 9f), TargetPoint(120f, 5f), TargetPoint(1000f, 0f), TargetPoint(8000f, 3f))
    private val otherCurve = listOf(TargetPoint(40f, -4f), TargetPoint(200f, 2f), TargetPoint(4000f, 6f))

    @Test
    fun `the curve equalizer block equals the curve equalizer sample by sample`() {
        for (channelCount in 1..3) {
            val frames = 12_000
            val input = Array(channelCount) { signal(frames, seed = it + 1L)[it % 2].rounded() }

            // Per sample, with a new curve half way: the pre-gain glides there.
            val reference = CurveEqualizer().also { it.configure(channelCount) }
            reference.setCurve(curve, 28f)
            val expected = Array(channelCount) { DoubleArray(frames) }
            for (n in 0 until frames) {
                if (n == frames / 2) reference.setCurve(otherCurve, 0f)
                // One buffer per frame is allowed: beginBuffer only redesigns for a new curve.
                reference.beginBuffer(rate)
                for (c in 0 until channelCount) expected[c][n] = reference.process(c, input[c][n].toFloat()).toDouble()
                reference.endFrame()
            }

            val equalizer = CurveEqualizer().also { it.configure(channelCount) }
            equalizer.setCurve(curve, 28f)
            val actual = Array(channelCount) { input[it].copyOf() }
            var position = 0
            for (half in 0 until 2) {
                if (half == 1) equalizer.setCurve(otherCurve, 0f)
                val end = if (half == 0) frames / 2 else frames
                while (position < end) {
                    val count = minOf(512, end - position)
                    val pieces = Array(channelCount) { actual[it].copyOfRange(position, position + count) }
                    assertTrue(equalizer.beginBuffer(rate))
                    equalizer.processBlock(pieces, count)
                    for (c in 0 until channelCount) pieces[c].copyInto(actual[c], position)
                    position += count
                }
            }

            for (c in 0 until channelCount) assertClose(expected[c], actual[c], 2e-5)
        }
    }

    @Test
    fun `the bass exciter block equals the bass exciter sample by sample`() {
        val input = signal(20_000)[0].rounded()
        val settings = BassExciterSettings(enabled = true, mix = 0.7f)
        val reference = BassExciter(settings)
        val expected = DoubleArray(input.size) { reference.processUnclamped(input[it].toFloat(), rate).toDouble() }

        val exciter = BassExciter(settings)
        val actual = input.copyOf()
        inPieces(input.size) { from, count ->
            val piece = actual.copyOfRange(from, from + count)
            exciter.processBlock(piece, count, rate)
            piece.copyInto(actual, from)
        }

        assertClose(expected, actual, 2e-5)
        assertTrue("harmonics were added", (0 until input.size).any { abs(actual[it] - input[it]) > 1e-3 })
    }

    @Test
    fun `the mono bass block equals mono bass sample by sample`() {
        val input = signal(8_000).map { it.rounded() }
        val settings = BassMonoSummerSettings(enabled = true, cutoffHz = 120f)
        val reference = BassMonoSummer(settings)
        val expectedLeft = DoubleArray(8_000)
        val expectedRight = DoubleArray(8_000)
        for (n in 0 until 8_000) {
            val mixed = reference.process(input[0][n].toFloat(), input[1][n].toFloat(), rate)
            expectedLeft[n] = mixed.left.toDouble()
            expectedRight[n] = mixed.right.toDouble()
        }

        val summer = BassMonoSummer(settings)
        val left = input[0].copyOf()
        val right = input[1].copyOf()
        inPieces(8_000) { from, count ->
            val leftPiece = left.copyOfRange(from, from + count)
            val rightPiece = right.copyOfRange(from, from + count)
            summer.processBlock(leftPiece, rightPiece, count, rate)
            leftPiece.copyInto(left, from)
            rightPiece.copyInto(right, from)
        }

        assertClose(expectedLeft, left, 1e-6)
        assertClose(expectedRight, right, 1e-6)
    }

    // The limiter written down the slow, obvious way: every frame looks back over the whole
    // window. Independent of the ring buffers and the deque in LookaheadLimiter, so it catches a
    // mistake in those.
    private class ReferenceLimiter(
        private val channels: Int,
        sampleRateHz: Float,
        thresholdDb: Float = -1f,
        lookaheadMs: Float = 5f,
    ) {
        private val threshold = 10f.pow(thresholdDb / 20f)
        private val window =
            ((lookaheadMs / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(LookaheadLimiter.DETECTOR_DELAY + 1) -
                LookaheadLimiter.DETECTOR_DELAY + 1
        private val release = exp(-1f / ((LookaheadLimiter.RELEASE_MS / 1000f) * sampleRateHz))
        private val inputs = ArrayList<FloatArray>()
        private val required = ArrayList<Float>()
        private val held = ArrayList<Float>()
        private var previousIntervalPeak = 0f
        private var gain = 1f

        private fun input(
            index: Int,
            channel: Int,
        ) = if (index < 0) 0f else inputs[index][channel]

        fun processFrame(frame: FloatArray) {
            inputs.add(frame.copyOf())
            val newest = inputs.size - 1
            // The frame two behind the newest is the one detected now.
            val detected = newest - LookaheadLimiter.DETECTOR_DELAY
            var samplePeak = 0f
            var nextIntervalPeak = 0f
            for (c in 0 until channels) {
                samplePeak = maxOf(samplePeak, abs(input(detected, c)))
                nextIntervalPeak =
                    maxOf(
                        nextIntervalPeak,
                        LookaheadLimiter.interpolatedPeak(
                            input(detected - 1, c),
                            input(detected, c),
                            input(detected + 1, c),
                            input(detected + 2, c),
                        ),
                    )
            }
            val peak = maxOf(samplePeak, previousIntervalPeak, nextIntervalPeak)
            previousIntervalPeak = nextIntervalPeak
            required.add(if (peak > threshold) threshold / peak else 1f)

            val last = required.size - 1
            var minimum = 1f
            for (j in maxOf(0, last - window + 1)..last) minimum = minOf(minimum, required[j])
            held.add(minimum)
            var sum = 0.0
            for (j in last - window + 1..last) sum += if (j < 0) 1.0 else held[j].toDouble()
            val ramped = (sum / window).toFloat()
            gain = if (ramped < gain) ramped else release * gain + (1f - release) * ramped

            val leaving = detected - (window - 1)
            for (c in 0 until channels) frame[c] = (input(leaving, c) * gain).coerceIn(-1f, 1f)
        }
    }

    @Test
    fun `the limiter block equals the limiter written the obvious way`() {
        val frames = 30_000
        // Well above the threshold in the loud parts, so the limiter works and releases.
        val input = signal(frames).map { channel -> DoubleArray(frames) { (channel[it] * 1.6).toFloat().toDouble() } }

        val reference = ReferenceLimiter(channels = 2, sampleRateHz = rate)
        val expected = Array(2) { DoubleArray(frames) }
        val frame = FloatArray(2)
        for (n in 0 until frames) {
            frame[0] = input[0][n].toFloat()
            frame[1] = input[1][n].toFloat()
            reference.processFrame(frame)
            expected[0][n] = frame[0].toDouble()
            expected[1][n] = frame[1].toDouble()
        }

        val limiter = LookaheadLimiter(channelCount = 2)
        val actual = Array(2) { input[it].copyOf() }
        inPieces(frames) { from, count ->
            val pieces = Array(2) { actual[it].copyOfRange(from, from + count) }
            limiter.processBlock(pieces, count, rate)
            for (c in 0 until 2) pieces[c].copyInto(actual[c], from)
        }

        assertArrayEquals(expected[0], actual[0], 0.0)
        assertArrayEquals(expected[1], actual[1], 0.0)
        val threshold = 0.8913 // -1 dBFS
        assertTrue("the limiter held the peaks", actual.all { channel -> channel.all { abs(it) <= threshold + 1e-3 } })
        assertTrue("the input was above the threshold", input.any { channel -> channel.any { abs(it) > 1.0 } })
        assertTrue("the quiet part came back up", (frames * 2 / 5 until frames / 2).any { abs(actual[0][it]) > 0.005 })
    }

    @Test
    fun `the limiter frame by frame equals the limiter block`() {
        val frames = 5_000
        val input = signal(frames).map { channel -> DoubleArray(frames) { (channel[it] * 1.6).toFloat().toDouble() } }
        val block = LookaheadLimiter(channelCount = 2)
        val expected = Array(2) { input[it].copyOf() }
        block.processBlock(expected, frames, rate)

        val frameByFrame = LookaheadLimiter(channelCount = 2)
        val frame = FloatArray(2)
        for (n in 0 until frames) {
            frame[0] = input[0][n].toFloat()
            frame[1] = input[1][n].toFloat()
            frameByFrame.processFrame(frame, rate)
            assertEquals(expected[0][n], frame[0].toDouble(), 0.0)
            assertEquals(expected[1][n], frame[1].toDouble(), 0.0)
        }
    }

    // The player's chain as it ran before the block rewrite: every frame through all stages, one
    // sample at a time, with the reference limiter and the same dither generator as PlayerDspPcm16.
    private class PerSampleChain(
        private val channels: Int,
    ) {
        val curveEqualizer = CurveEqualizer().also { it.configure(channels) }
        val exciterSettings = BassExciterSettings(enabled = true, mix = 0.6f)
        private val exciters = List(channels) { BassExciter(exciterSettings) }
        private val monoSummer = BassMonoSummer(BassMonoSummerSettings(enabled = true, cutoffHz = 120f))
        private var limiter: ReferenceLimiter? = null
        private val frame = FloatArray(channels)
        private var ditherState = 0x2545F491

        fun process(
            samples: ShortArray,
            sampleRateHz: Int,
        ) {
            val rate = sampleRateHz.toFloat()
            val limiter = limiter ?: ReferenceLimiter(channels, rate).also { limiter = it }
            val curveActive = curveEqualizer.beginBuffer(rate)
            var i = 0
            while (i + channels <= samples.size) {
                for (c in 0 until channels) {
                    var sample = samples[i + c] / 32768f
                    if (curveActive) sample = curveEqualizer.process(c, sample)
                    sample = exciters[c].processUnclamped(sample, rate)
                    frame[c] = sample
                }
                if (curveActive) curveEqualizer.endFrame()
                if (channels == 2) {
                    val mixed = monoSummer.process(frame[0], frame[1], rate)
                    frame[0] = mixed.left
                    frame[1] = mixed.right
                }
                limiter.processFrame(frame)
                for (c in 0 until channels) samples[i + c] = toPcm16(frame[c])
                i += channels
            }
        }

        private fun toPcm16(value: Float): Short {
            val dithered = value * 32768f + (nextUniform() - nextUniform())
            return Math.round(dithered).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        private fun nextUniform(): Float {
            var x = ditherState
            x = x xor (x shl 13)
            x = x xor (x ushr 17)
            x = x xor (x shl 5)
            ditherState = x
            return (x ushr 8) * (1f / 16_777_216f)
        }
    }

    private fun pcm(
        frames: Int,
        channels: Int,
    ): ShortArray {
        val source = signal(frames)
        return ShortArray(frames * channels) { (source[it % channels][it / channels] * 30_000).toInt().coerceIn(-32768, 32767).toShort() }
    }

    @Test
    fun `the whole player chain sounds the same as sample by sample`() {
        for (channels in 1..2) {
            val frames = 40_000
            val original = pcm(frames, channels)

            val reference = PerSampleChain(channels)
            reference.curveEqualizer.setCurve(curve, 28f)
            val expected = original.copyOf()
            reference.process(expected, rate.toInt())

            val dsp = PlayerDspPcm16().also { it.configure(channels) }
            dsp.updateSettings(PlayerDspSettings(monoBassEnabled = true, monoBassCutoffHz = 120f, limiterEnabled = true))
            dsp.updateVirtualBass(0.6f)
            dsp.updateCurve(curve, 28f)
            val actual = original.copyOf()
            dsp.process(actual, 0, actual.size, rate.toInt())

            var worst = 0
            for (n in expected.indices) worst = maxOf(worst, abs(expected[n] - actual[n]))
            // One step of 16-bit audio: where a value sits right between two steps, the rounding
            // may fall the other way.
            assertTrue("$channels channel(s): largest difference $worst steps", worst <= 1)
            assertTrue("the chain changed the audio", (original.indices).any { abs(original[it] - actual[it]) > 500 })
        }
    }

    @Test
    fun `the player chain gives the same output however the audio is cut into buffers`() {
        val frames = 20_000
        val original = pcm(frames, 2)

        fun configured() =
            PlayerDspPcm16().also {
                it.configure(2)
                it.updateSettings(PlayerDspSettings(monoBassEnabled = true, limiterEnabled = true))
                it.updateVirtualBass(0.6f)
                it.updateCurve(curve, 28f)
            }

        val atOnce = original.copyOf()
        configured().process(atOnce, 0, atOnce.size, rate.toInt())

        val inBuffers = original.copyOf()
        val dsp = configured()
        inPieces(frames) { from, count -> dsp.process(inBuffers, from * 2, count * 2, rate.toInt()) }

        assertArrayEquals(atOnce, inBuffers)
    }
}
