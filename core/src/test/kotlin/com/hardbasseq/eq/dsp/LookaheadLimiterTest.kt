package com.hardbasseq.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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

    // The limiter keeps its window minimum with a monotonic queue; this checks it
    // against the plain "scan the whole window" definition on noisy input.
    @Test
    fun `matches a straightforward reference implementation`() {
        val rate = 44_100f
        val settings = LookaheadLimiterSettings(enabled = true, thresholdDb = -6f, lookaheadMs = 3f)
        val limiter = LookaheadLimiter(settings)
        val lookahead = ((settings.lookaheadMs / 1000f) * rate).roundToInt()
        val threshold = 10f.pow(settings.thresholdDb / 20f)
        val release = exp(-1f / ((50f / 1000f) * rate))
        val random = java.util.Random(7)
        val delay = ArrayDeque<Float>()
        val window = ArrayDeque<Float>()
        var gain = 1f
        var previous = 0f
        repeat(5_000) { n ->
            val input = (random.nextGaussian().toFloat() * 0.6f).coerceIn(-1f, 1f) * if (n % 700 < 40) 1.6f else 1f
            val clamped = input.coerceIn(-1f, 1f)
            val peak = maxOf(abs(clamped), abs((previous + clamped) / 2f))
            previous = clamped
            val required = if (peak > threshold) threshold / peak else 1f
            delay.addLast(clamped)
            window.addLast(required)
            val expected =
                if (delay.size <= lookahead) {
                    0f
                } else {
                    val minGain = window.min()
                    gain = if (minGain < gain) minGain else release * gain + (1f - release) * minGain
                    val out = (delay.removeFirst() * gain).coerceIn(-1f, 1f)
                    window.removeFirst()
                    out
                }
            assertEquals("sample $n", expected, limiter.process(clamped, rate), 1e-6f)
        }
    }
}
