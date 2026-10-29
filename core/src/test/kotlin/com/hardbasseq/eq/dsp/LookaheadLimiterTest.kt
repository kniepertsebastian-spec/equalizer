package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

class LookaheadLimiterTest {
    private val sampleRateHz = 48000f

    @Test
    fun disabled_isBypass() {
        val limiter = LookaheadLimiter(LookaheadLimiterSettings(enabled = false))

        val output = limiter.process(0.95f, sampleRateHz)

        assertEquals(0.95f, output, 0.0001f)
    }

    @Test
    fun quietSignal_passesThroughAtUnityGainAfterTheLookaheadDelay() {
        val limiter = LookaheadLimiter(LookaheadLimiterSettings(enabled = true, thresholdDb = -1f, lookaheadMs = 5f))
        val lookaheadSamples = lookaheadSamplesFor(5f)
        val level = 0.1f

        val outputs = FloatArray(lookaheadSamples + 100) { limiter.process(level, sampleRateHz) }

        for (i in 0 until lookaheadSamples) {
            assertEquals("expected silence during the startup latency window", 0f, outputs[i], 0.0001f)
        }
        for (i in lookaheadSamples until outputs.size) {
            assertEquals(level, outputs[i], 0.001f)
        }
    }

    @Test
    fun lookahead_reducesGainBeforeThePeakArrivesAtOutput() {
        val thresholdDb = -1f
        val lookaheadMs = 5f
        val limiter = LookaheadLimiter(LookaheadLimiterSettings(enabled = true, thresholdDb = thresholdDb, lookaheadMs = lookaheadMs))
        val lookaheadSamples = lookaheadSamplesFor(lookaheadMs)
        val thresholdLinear = 10f.pow(-thresholdDb / 20f)

        val quietLevel = 0.3f
        val spikeIndex = 500
        val totalSamples = spikeIndex + lookaheadSamples + 200

        val outputs =
            FloatArray(totalSamples) { i ->
                val input = if (i == spikeIndex) 1f else quietLevel
                limiter.process(input, sampleRateHz)
            }

        // The spike's own (gain-reduced) sample surfaces at this delayed
        // output index - never above the threshold.
        val delayedSpikeOutputIndex = spikeIndex + lookaheadSamples
        assertTrue(abs(outputs[delayedSpikeOutputIndex]) <= thresholdLinear + 0.01f)

        // A plain reactive limiter (no lookahead) could only start reducing
        // gain once it has actually seen the spike - i.e. no earlier than
        // delayedSpikeOutputIndex itself. Because this one buffers
        // lookaheadSamples ahead, gain must already be reduced somewhere in
        // the window immediately before that index too.
        val preSpikeWindowStart = delayedSpikeOutputIndex - lookaheadSamples
        val minPreSpikeOutput = (preSpikeWindowStart until delayedSpikeOutputIndex).minOf { outputs[it] }
        assertTrue(
            "expected gain reduction visible before the peak reaches output, minPreSpikeOutput=$minPreSpikeOutput",
            minPreSpikeOutput < quietLevel - 0.001f,
        )
    }

    @Test
    fun output_neverExceedsTheConfiguredThreshold() {
        val thresholdDb = -3f
        val limiter = LookaheadLimiter(LookaheadLimiterSettings(enabled = true, thresholdDb = thresholdDb, lookaheadMs = 5f))
        val thresholdLinear = 10f.pow(-thresholdDb / 20f)
        val lookaheadSamples = lookaheadSamplesFor(5f)

        val levels = floatArrayOf(0.9f, 1f, 0.95f, 1f, 0.99f)
        val outputs = mutableListOf<Float>()
        repeat(lookaheadSamples + levels.size * 300) { i ->
            val level = levels[(i / 300) % levels.size]
            outputs.add(limiter.process(level, sampleRateHz))
        }

        // Skip the startup latency window - it's all zeros by design, not a
        // meaningful peak measurement.
        val settled = outputs.drop(lookaheadSamples)
        assertTrue(settled.all { abs(it) <= thresholdLinear + 0.01f })
    }

    @Test
    fun settings_clampThresholdAndLookaheadToSafeRanges() {
        val settings = LookaheadLimiterSettings(thresholdDb = 10f, lookaheadMs = 999f).clamped()
        assertEquals(LookaheadLimiterSettings.MAX_THRESHOLD_DB, settings.thresholdDb, 0.001f)
        assertEquals(LookaheadLimiterSettings.MAX_LOOKAHEAD_MS, settings.lookaheadMs, 0.001f)

        val tooLow = LookaheadLimiterSettings(thresholdDb = -999f, lookaheadMs = 0f).clamped()
        assertEquals(LookaheadLimiterSettings.MIN_THRESHOLD_DB, tooLow.thresholdDb, 0.001f)
        assertEquals(LookaheadLimiterSettings.MIN_LOOKAHEAD_MS, tooLow.lookaheadMs, 0.001f)
    }

    private fun lookaheadSamplesFor(lookaheadMs: Float): Int = ((lookaheadMs / 1000f) * sampleRateHz).roundToInt()

    // The limiter keeps its window minimum and the ramp in ring buffers; this checks
    // them against the plain definitions (scan the window, average the window) on
    // noisy input.
    @Test
    fun `matches a straightforward reference implementation`() {
        val rate = 44_100f
        val settings = LookaheadLimiterSettings(enabled = true, thresholdDb = -6f, lookaheadMs = 3f)
        val limiter = LookaheadLimiter(settings)
        val lookahead = ((settings.lookaheadMs / 1000f) * rate).roundToInt()
        val window = lookahead - LookaheadLimiter.DETECTOR_DELAY + 1
        val threshold = 10f.pow(settings.thresholdDb / 20f)
        val release = exp(-1f / ((LookaheadLimiter.RELEASE_MS / 1000f) * rate))
        val random = java.util.Random(7)
        val count = 5_000
        val x =
            FloatArray(count) { n ->
                ((random.nextGaussian().toFloat() * 0.6f) * if (n % 700 < 40) 1.6f else 1f).coerceIn(-1f, 1f)
            }

        fun at(i: Int) = if (i < 0) 0f else x[i]

        // Core frame p carries input sample p - 2, detected with two neighbours each side.
        val required =
            FloatArray(count) { p ->
                val k = p - LookaheadLimiter.DETECTOR_DELAY
                val peak =
                    maxOf(
                        abs(at(k)),
                        LookaheadLimiter.interpolatedPeak(at(k - 2), at(k - 1), at(k), at(k + 1)),
                        LookaheadLimiter.interpolatedPeak(at(k - 1), at(k), at(k + 1), at(k + 2)),
                    )
                if (peak > threshold) threshold / peak else 1f
            }
        val held = FloatArray(count) { p -> (maxOf(0, p - window + 1)..p).minOf { required[it] } }
        var gain = 1f
        for (p in 0 until count) {
            val ramped = ((p - window + 1)..p).sumOf { if (it < 0) 1.0 else held[it].toDouble() } / window
            gain = if (ramped < gain) ramped.toFloat() else release * gain + (1f - release) * ramped.toFloat()
            val expected = (at(p - LookaheadLimiter.DETECTOR_DELAY - window + 1) * gain).coerceIn(-1f, 1f)
            assertEquals("sample $p", expected, limiter.process(x[p], rate), 1e-5f)
        }
    }

    @Test
    fun `gain ramps down over the lookahead instead of jumping`() {
        val lookaheadSamples = lookaheadSamplesFor(5f)
        val limiter = LookaheadLimiter(LookaheadLimiterSettings(enabled = true, thresholdDb = -6f, lookaheadMs = 5f))
        val quiet = 0.3f
        val outputs = FloatArray(2_000) { i -> limiter.process(if (i == 1_000) 1f else quiet, sampleRateHz) }

        // Gain of every output that carries a quiet input sample.
        val gains = (lookaheadSamples + 10 until outputs.size).filter { it != 1_000 + lookaheadSamples }.map { outputs[it] / quiet }
        val largestDrop = gains.zipWithNext { a, b -> a - b }.max()
        assertTrue("gain dropped by $largestDrop in one sample", largestDrop < 0.01f)
    }

    @Test
    fun `peaks between samples are caught`() {
        // A quarter-sample-rate sine with a 45 degree phase: every sample sits at +-0.75,
        // while the reconstructed waveform peaks at about 1.06 between them.
        val limiter = LookaheadLimiter(LookaheadLimiterSettings(enabled = true, thresholdDb = -1f, lookaheadMs = 5f))
        val outputs =
            FloatArray(4_000) { n ->
                limiter.process((1.06 * kotlin.math.sin(PI / 2 * n + PI / 4)).toFloat(), sampleRateHz)
            }
        val settledPeak = (2_000 until outputs.size).maxOf { abs(outputs[it]) }
        assertTrue("sample peak stayed at $settledPeak", settledPeak < 0.74f)
    }

    @Test
    fun `linked channels share one gain`() {
        val limiter = LookaheadLimiter(LookaheadLimiterSettings(enabled = true, thresholdDb = -6f, lookaheadMs = 5f), channelCount = 2)
        val frame = FloatArray(2)
        var minRight = 1f
        repeat(2_000) { n ->
            // Bursts on the left only; the right channel stays quiet throughout.
            frame[0] = if (n % 200 < 20) 1f else 0.2f
            frame[1] = 0.2f
            limiter.processFrame(frame, sampleRateHz)
            if (n > 500) minRight = minOf(minRight, frame[1])
        }
        // Unlinked, the right channel would stay at 0.2 and the image would swing left.
        assertTrue("right channel never ducked: $minRight", minRight < 0.15f)
    }
}
