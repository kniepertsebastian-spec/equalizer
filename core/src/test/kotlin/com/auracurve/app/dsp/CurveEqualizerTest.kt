package com.auracurve.app.dsp

import com.auracurve.app.autoeq.BuiltInAutoEqCatalog
import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CurveEqualizerTest {
    private val rate = 48_000f

    private fun designed(
        curve: List<TargetPoint>,
        cutoffHz: Float = 0f,
        headroomSafetyRatio: Float = CurveEqualizer.DEFAULT_HEADROOM_SAFETY_RATIO,
    ) = CurveEqualizer().also {
        it.configure(2)
        it.setCurve(curve, cutoffHz, headroomSafetyRatio)
        assertTrue(it.beginBuffer(rate))
    }

    private fun Float.toDb() = 20f * log10(this)

    private val flat12Db = listOf(TargetPoint(20f, 12f), TargetPoint(20_000f, 12f))

    // The deviation of the designed chain from the curve, 25 Hz to 16 kHz at 1/48 octave.
    private class Deviation(
        val maxDb: Float,
        val atHz: Float,
        val rmsDb: Float,
    )

    private fun deviation(
        curve: List<TargetPoint>,
        sampleRateHz: Float = rate,
    ): Deviation {
        val equalizer = CurveEqualizer()
        equalizer.configure(2)
        equalizer.setCurve(curve, 0f)
        assertTrue(equalizer.beginBuffer(sampleRateHz))
        val sorted = curve.sortedBy { it.frequencyHz }
        var max = 0f
        var at = 0f
        var squares = 0.0
        var count = 0
        var hz = 25.0
        while (hz <= 16_000.0 * 1.0001) {
            val error = abs(equalizer.chainResponseDb(hz.toFloat()) - EqualizerInterpolator.interpolateFrequency(hz.toFloat(), sorted))
            if (error > max) {
                max = error
                at = hz.toFloat()
            }
            squares += error * error
            count++
            hz *= 2.0.pow(1.0 / 48.0)
        }
        return Deviation(max, at, sqrt(squares / count).toFloat())
    }

    @Test
    fun `the chain follows every built-in context curve within 0_5 dB from 25 to 16000 Hz`() {
        // The old 14 peaks were off by up to 1.3 dB here (in-ear at 12 kHz).
        BuiltInContextPresets.all.forEach { preset ->
            for (sampleRate in listOf(44_100f, 48_000f)) {
                val result = deviation(preset.targetCurve, sampleRate)
                assertTrue("${preset.id} at $sampleRate: ${result.maxDb} dB at ${result.atHz} Hz", result.maxDb < 0.5f)
            }
        }
    }

    @Test
    fun `steep curves stay within 0_5 dB from 25 to 16000 Hz`() {
        val steep =
            mapOf(
                "flat +12 dB" to flat12Db,
                // +15 dB bass shelf falling 15 dB in two octaves: the old design was 5 dB off at 25 Hz.
                "bass shelf +15 dB" to
                    listOf(
                        TargetPoint(20f, 15f),
                        TargetPoint(80f, 15f),
                        TargetPoint(300f, 0f),
                        TargetPoint(20_000f, 0f),
                    ),
                "treble tilt -9 dB" to
                    listOf(TargetPoint(20f, 0f), TargetPoint(2_000f, 0f), TargetPoint(16_000f, -9f), TargetPoint(20_000f, -9f)),
                // A -6 dB dip one octave wide (Q 1.4), as a headphone correction at the ear-canal resonance.
                "presence dip" to ParametricCurve.toCurve(listOf(ParametricFilter(ParametricFilterType.PEAK, 2_800f, -6f, 1.4f))),
            )
        steep.forEach { (name, curve) ->
            for (sampleRate in listOf(44_100f, 48_000f)) {
                val result = deviation(curve, sampleRate)
                assertTrue("$name at $sampleRate: ${result.maxDb} dB at ${result.atHz} Hz", result.maxDb < 0.5f)
            }
        }
    }

    @Test
    fun `the AutoEQ catalogue corrections are followed closely`() {
        // Raw AutoEQ GraphicEQ data, 127 points: it wiggles by 1-2 dB within a sixth of an octave
        // (e.g. Sony WH-1000XM4 4.0-4.8 kHz), finer than any third-octave filter can follow, so
        // single points miss by up to 1.8 dB. Over the range the chain is within 0.35 dB rms;
        // the old 14 peaks were 0.5-1.2 dB rms and up to 5.3 dB off.
        BuiltInAutoEqCatalog.entries.forEach { entry ->
            val result = deviation(entry.profile.curve)
            assertTrue("${entry.id}: ${result.rmsDb} dB rms", result.rmsDb < 0.35f)
            assertTrue("${entry.id}: ${result.maxDb} dB at ${result.atHz} Hz", result.maxDb < 1.8f)
        }
    }

    @Test
    fun `third-octave bands from 25 Hz to 16 kHz plus two edge shelves`() {
        assertEquals(29, CurveEqualizer.CENTERS_HZ.size)
        assertEquals(25f, CurveEqualizer.CENTERS_HZ.first())
        assertEquals(16_000f, CurveEqualizer.CENTERS_HZ.last())
        CurveEqualizer.CENTERS_HZ.zipWithNext().forEach { (low, high) ->
            // ISO nominal values: 1.25 to 1.28 instead of exactly 1.26.
            assertEquals("third octave from $low", 2.0.pow(1.0 / 3.0), (high / low).toDouble(), 0.03)
        }
        assertEquals(31, designed(flat12Db).activeFilterCount)
    }

    @Test
    fun `bands with about 0 dB are skipped`() {
        // Bass only: nothing above the low mids needs computing.
        val bassOnly = designed(listOf(TargetPoint(20f, 8f), TargetPoint(60f, 8f), TargetPoint(250f, 0f), TargetPoint(20_000f, 0f)))
        assertTrue("active ${bassOnly.activeFilterCount}", bassOnly.activeFilterCount <= 14)
        // A subsonic cut alone runs just the two high-pass stages.
        assertEquals(2, designed(listOf(TargetPoint(100f, 0f)), cutoffHz = 30f).activeFilterCount)
    }

    @Test
    fun `a skipped filter follows the audio like a pass-through one`() {
        val input = DoubleArray(512) { sin(it * 0.37) * 0.4 }
        val identity = BiquadCoefficients(1f, 0f, 0f, 0f, 0f)
        val boost = BiquadFilterDesigner.design(ParametricFilter(ParametricFilterType.PEAK, 1_000f, 6f, 2.5f), rate)

        val followed = BiquadFilterState()
        followed.followBlock(input, input.size)
        val computed = BiquadFilterState()
        computed.processBlock(input.copyOf(), input.size, identity)

        // From here both go on with a real boost: same state, same output.
        val next = DoubleArray(64) { sin((512 + it) * 0.37) * 0.4 }
        val a = next.copyOf().also { followed.processBlock(it, it.size, boost) }
        val b = next.copyOf().also { computed.processBlock(it, it.size, boost) }
        for (n in a.indices) assertEquals(b[n], a[n], 1e-12)

        val single = BiquadFilterState()
        input.forEach { single.follow(it.toFloat()) }
        val c = next.copyOf().also { single.processBlock(it, it.size, boost) }
        for (n in a.indices) assertEquals(b[n], c[n], 1e-6)
    }

    @Test
    fun `a skipped band coming back during playback does not click`() {
        val equalizer = CurveEqualizer()
        equalizer.configure(2)
        // Bass only: the bands around 5 kHz are skipped.
        equalizer.setCurve(listOf(TargetPoint(20f, 6f), TargetPoint(100f, 6f), TargetPoint(400f, 0f), TargetPoint(20_000f, 0f)), 0f)
        val left = DoubleArray(48_000) { (0.3 * sin(2 * PI * 5_000 * it / rate) + 0.3 * sin(2 * PI * 60 * it / rate)) }
        val right = left.copyOf()
        val originalLeft = left.copyOf()
        val block = Array(2) { DoubleArray(480) }
        for (b in 0 until 100) {
            // Halfway, a treble boost brings the 5 kHz bands back.
            if (b == 50) {
                equalizer.setCurve(
                    listOf(
                        TargetPoint(20f, 6f),
                        TargetPoint(100f, 6f),
                        TargetPoint(400f, 0f),
                        TargetPoint(5_000f, 2f),
                        TargetPoint(20_000f, 2f),
                    ),
                    0f,
                )
            }
            assertTrue(equalizer.beginBuffer(rate))
            System.arraycopy(left, b * 480, block[0], 0, 480)
            System.arraycopy(right, b * 480, block[1], 0, 480)
            equalizer.processBlock(block, 480)
            System.arraycopy(block[0], 0, left, b * 480, 480)
        }
        // The output minus the input: the change of a clean sine pair. A click would be a jump in
        // it far above what the two tones' own slopes give.
        val steady = (12_000 until 24_000).maxOf { abs((left[it] - originalLeft[it]) - (left[it - 1] - originalLeft[it - 1])) }
        val aroundChange = (24_000 until 26_000).maxOf { abs((left[it] - originalLeft[it]) - (left[it - 1] - originalLeft[it - 1])) }
        assertTrue("step $aroundChange after the change vs $steady before", aroundChange <= steady * 2.5 + 0.01)
    }

    @Test
    fun `the chain is cheap enough for the slow cores`() {
        // A full AutoEQ correction: every band active. One minute of stereo at 48 kHz.
        val equalizer =
            designed(
                BuiltInAutoEqCatalog.entries
                    .first()
                    .profile.curve,
            )
        val block = Array(2) { DoubleArray(512) { n -> sin(n * 0.1) * 0.3 } }
        repeat(1_000) { equalizer.processBlock(block, 512) }
        val blocks = 48_000 * 60 / 512
        val start = System.nanoTime()
        repeat(blocks) { equalizer.processBlock(block, 512) }
        val seconds = (System.nanoTime() - start) / 1e9
        // About 0.25 s on a desktop; a phone's little core is roughly ten times slower, still a
        // few percent of one core. The bound only catches a regression by an order of magnitude.
        println("CurveEqualizer: 60 s of stereo in ${"%.3f".format(seconds)} s, ${equalizer.activeFilterCount} filters")
        assertTrue("took $seconds s", seconds < 6.0)
    }

    @Test
    fun `parametric filters are run exactly instead of on the third-octave grid`() {
        // A narrow AutoEQ-style peak and a bass shelf, as the user set them.
        val filters =
            listOf(
                ParametricFilter(ParametricFilterType.PEAK, 3_150f, 6f, 6f),
                ParametricFilter(ParametricFilterType.LOW_SHELF, 105f, 4f, 0.7f),
            )
        val equalizer = CurveEqualizer()
        equalizer.configure(2)
        equalizer.setCurve(emptyList(), 0f, parametricFilters = filters)
        assertTrue(equalizer.beginBuffer(rate))
        val coefficients = filters.map { BiquadFilterDesigner.design(it, rate) }
        var hz = 25f
        while (hz <= 16_000f) {
            val exact = coefficients.sumOf { BiquadFilterDesigner.magnitudeResponseDb(it, hz, rate).toDouble() }.toFloat()
            assertEquals("at $hz Hz", exact, equalizer.chainResponseDb(hz), 0.02f)
            hz *= 1.03f
        }
        // Smeared onto the third-octave grid, the Q 6 peak is missed by most of a dB.
        val viaCurve = deviation(ParametricCurve.toCurve(filters))
        assertTrue("grid only: ${viaCurve.maxDb} dB at ${viaCurve.atHz} Hz", viaCurve.maxDb > 0.5f)
    }

    @Test
    fun `parametric filters add to the curve and count for the pre-gain`() {
        val narrow = ParametricFilter(ParametricFilterType.PEAK, 1_000f, 10f, 8f)
        val equalizer = CurveEqualizer()
        equalizer.configure(2)
        equalizer.setCurve(listOf(TargetPoint(20f, 0f), TargetPoint(20_000f, 0f)), 0f, 1f, listOf(narrow))
        assertTrue(equalizer.beginBuffer(rate))

        assertEquals(10f, equalizer.chainResponseDb(1_000f), 0.05f)
        // A narrow +10 dB peak at Q 8 sits between the grid points of the pre-gain search.
        assertEquals(-10f, equalizer.preGain.toDb(), 0.05f)

        val withCurve = CurveEqualizer()
        withCurve.configure(2)
        withCurve.setCurve(flat12Db, 0f, 1f, listOf(narrow))
        assertTrue(withCurve.beginBuffer(rate))
        assertEquals(22f, withCurve.chainResponseDb(1_000f), 0.5f)
        assertEquals(-22f, withCurve.preGain.toDb(), 0.5f)
    }

    @Test
    fun `bypassed or flat parametric filters keep the equalizer off`() {
        val equalizer = CurveEqualizer()
        equalizer.configure(2)
        equalizer.setCurve(
            emptyList(),
            0f,
            parametricFilters =
                listOf(
                    ParametricFilter(ParametricFilterType.PEAK, 1_000f, 6f, 1f, bypassed = true),
                    ParametricFilter(ParametricFilterType.PEAK, 2_000f, 0f, 1f),
                ),
        )
        assertFalse(equalizer.beginBuffer(rate))

        equalizer.setCurve(emptyList(), 0f, parametricFilters = listOf(ParametricFilter(ParametricFilterType.PEAK, 1_000f, 6f, 1f)))
        assertTrue(equalizer.beginBuffer(rate))
        assertEquals(1, equalizer.activeFilterCount)
    }

    @Test
    fun `adding a parametric filter during playback keeps the curve's memory`() {
        val equalizer = CurveEqualizer()
        equalizer.configure(1)
        val curve = listOf(TargetPoint(20f, 6f), TargetPoint(200f, 6f), TargetPoint(1_000f, 0f), TargetPoint(20_000f, 0f))
        equalizer.setCurve(curve, 0f, 1f)
        val out = FloatArray(48_000)
        for (block in 0 until 48) {
            // Halfway a narrow cut far above the tone is added: the 80 Hz tone must run on.
            if (block == 24) equalizer.setCurve(curve, 0f, 1f, listOf(ParametricFilter(ParametricFilterType.PEAK, 8_000f, -3f, 4f)))
            assertTrue(equalizer.beginBuffer(rate))
            for (n in block * 1_000 until (block + 1) * 1_000) {
                out[n] = equalizer.process(0, (0.5 * sin(2 * PI * 80 * n / rate)).toFloat())
                equalizer.endFrame()
            }
        }
        val steady = (12_000 until 24_000).maxOf { abs(out[it] - out[it - 1]) }
        val aroundChange = (24_000 until 26_000).maxOf { abs(out[it] - out[it - 1]) }
        assertTrue("step $aroundChange after the change vs $steady before", aroundChange <= steady * 1.2f)
    }

    @Test
    fun `the subsonic high-pass cuts well below and passes well above the cutoff`() {
        val equalizer = designed(emptyList(), cutoffHz = 40f)

        assertTrue("at half the cutoff", equalizer.chainResponseDb(20f) <= -20f)
        assertTrue("at twice the cutoff", equalizer.chainResponseDb(80f) >= -1f)
    }

    @Test
    fun `a flat curve without a cutoff keeps the equalizer off`() {
        val equalizer = CurveEqualizer()
        equalizer.configure(2)
        equalizer.setCurve(listOf(TargetPoint(100f, 0f), TargetPoint(5000f, 0f)), 0f)

        assertFalse(equalizer.beginBuffer(rate))
        assertEquals(1f, equalizer.preGain, 0f)
    }

    @Test
    fun `with a ratio of 1 the pre-gain makes room for the whole boost`() {
        val equalizer = designed(listOf(TargetPoint(20f, 6f), TargetPoint(20_000f, 6f)), headroomSafetyRatio = 1f)

        assertTrue("pre-gain was ${equalizer.preGain}", equalizer.preGain < 0.6f)
    }

    @Test
    fun `the pre-gain takes back the safety ratio's share of a 12 dB boost`() {
        assertEquals(-12f, CurveEqualizer.preGainDb(12f, 1f), 1e-4f)
        assertEquals(-6f, CurveEqualizer.preGainDb(12f, 0.5f), 1e-4f)
        assertEquals(-9.6f, CurveEqualizer.preGainDb(12f, 0.8f), 1e-4f)
        assertEquals(0f, CurveEqualizer.preGainDb(12f, 0f))
        // Out of range ratios are clamped; a cut needs no headroom.
        assertEquals(-12f, CurveEqualizer.preGainDb(12f, 2f), 1e-4f)
        assertEquals(0f, CurveEqualizer.preGainDb(-6f, 0.5f))
    }

    @Test
    fun `the designed chain's pre-gain follows the safety ratio`() {
        val full = designed(flat12Db, headroomSafetyRatio = 1f).preGain.toDb()
        assertEquals("the whole chain peaks at about +12 dB", -12f, full, 0.5f)

        assertEquals(full * 0.5f, designed(flat12Db, headroomSafetyRatio = 0.5f).preGain.toDb(), 1e-3f)
        assertEquals(full * 0.8f, designed(flat12Db, headroomSafetyRatio = 0.8f).preGain.toDb(), 1e-3f)
        assertEquals(1f, designed(flat12Db, headroomSafetyRatio = 0f).preGain, 0f)
        assertEquals("default", full * 0.5f, designed(flat12Db).preGain.toDb(), 1e-3f)
    }

    @Test
    fun `a new safety ratio glides instead of jumping`() {
        // Headphones (0.5) to a Bluetooth box (0.8): same curve, 3.6 dB more cut.
        val equalizer = designed(flat12Db, headroomSafetyRatio = 0.5f)
        val before = equalizer.appliedGain
        equalizer.setCurve(flat12Db, 0f, 0.8f)
        assertTrue(equalizer.beginBuffer(rate))
        equalizer.process(0, 0f)
        equalizer.endFrame()

        val afterOneFrame = equalizer.appliedGain
        assertTrue("jumped from $before to $afterOneFrame", before - afterOneFrame < 0.01f)
        assertTrue("the target is lower", equalizer.preGain < before - 0.1f)
        repeat(4_800) { equalizer.endFrame() }
        assertEquals(equalizer.preGain, equalizer.appliedGain, 1e-6f)
    }

    @Test
    fun `with the limiter off the player takes back the whole boost so nothing clips`() {
        fun peakOut(limiterEnabled: Boolean): Int {
            val dsp = PlayerDspPcm16()
            dsp.configure(2)
            dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = limiterEnabled))
            dsp.updateCurve(listOf(TargetPoint(20f, 6f), TargetPoint(20_000f, 6f)), 0f, headroomSafetyRatio = 0.5f)
            val samples = ShortArray(32_000) { (sin(2 * PI * 1_000 * (it / 2) / rate) * 30_000).toInt().toShort() }
            dsp.process(samples, 0, samples.size, rate.toInt())
            return (16_000 until samples.size).maxOf { abs(samples[it].toInt()) }
        }

        val unlimited = peakOut(limiterEnabled = false)
        assertTrue("peak was $unlimited", unlimited <= 30_100)
    }

    @Test
    fun `switching the limiter off during playback takes back the whole boost`() {
        val dsp = PlayerDspPcm16()
        dsp.configure(2)
        dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = true))
        dsp.updateCurve(listOf(TargetPoint(20f, 6f), TargetPoint(20_000f, 6f)), 0f, headroomSafetyRatio = 0.5f)
        val warmUp = ShortArray(4_000) { (sin(2 * PI * 1_000 * (it / 2) / rate) * 30_000).toInt().toShort() }
        dsp.process(warmUp, 0, warmUp.size, rate.toInt())

        dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = false))
        val samples = ShortArray(32_000) { (sin(2 * PI * 1_000 * (it / 2) / rate) * 30_000).toInt().toShort() }
        dsp.process(samples, 0, samples.size, rate.toInt())

        val peak = (16_000 until samples.size).maxOf { abs(samples[it].toInt()) }
        assertTrue("peak was $peak", peak <= 30_100)
    }

    @Test
    fun `a boosted loud sine stays under the limiter threshold`() {
        val dsp = PlayerDspPcm16()
        dsp.configure(2)
        // Soft clipper off: the limiter alone has to hold the boosted sine.
        dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = true, softClipEnabled = false))
        dsp.updateCurve(listOf(TargetPoint(20f, 6f), TargetPoint(20_000f, 6f)), 0f)
        val samples = ShortArray(32_000) { (sin(2 * PI * 1_000 * (it / 2) / rate) * 32_000).toInt().toShort() }

        dsp.process(samples, 0, samples.size, rate.toInt())

        val peak = (16_000 until samples.size).maxOf { abs(samples[it].toInt()) }
        // The safety ceiling, -0.2 dBFS (32 022), plus one step of dither.
        assertTrue("peak was $peak", peak <= 32_023)
    }

    @Test
    fun `with every stage off the audio is untouched even if a curve is set`() {
        val dsp = PlayerDspPcm16()
        dsp.configure(2)
        dsp.updateSettings(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = false, softClipEnabled = false))
        dsp.updateCurve(emptyList(), 0f)
        val samples = ShortArray(2_000) { (sin(2 * PI * 200 * (it / 2) / rate) * 20_000).toInt().toShort() }
        val copy = samples.copyOf()

        dsp.process(samples, 0, samples.size, rate.toInt())

        assertTrue(copy.contentEquals(samples))
    }

    @Test
    fun `changing the curve during playback does not click`() {
        val equalizer = CurveEqualizer()
        equalizer.configure(1)
        equalizer.setCurve(listOf(TargetPoint(20f, 6f), TargetPoint(200f, 6f), TargetPoint(1_000f, 0f)), 0f)
        val out = FloatArray(48_000)
        for (block in 0 until 48) {
            // Halfway through, the bass boost is dragged down by 3 dB, as on the graph.
            if (block == 24) equalizer.setCurve(listOf(TargetPoint(20f, 3f), TargetPoint(200f, 3f), TargetPoint(1_000f, 0f)), 0f)
            assertTrue(equalizer.beginBuffer(rate))
            for (n in block * 1_000 until (block + 1) * 1_000) {
                out[n] = equalizer.process(0, (0.5 * sin(2 * PI * 80 * n / rate)).toFloat())
                equalizer.endFrame()
            }
        }
        // The steepest sample-to-sample step of a clean 80 Hz sine at this level, with margin.
        val steady = (12_000 until 24_000).maxOf { abs(out[it] - out[it - 1]) }
        val aroundChange = (24_000 until 26_000).maxOf { abs(out[it] - out[it - 1]) }
        assertTrue("step $aroundChange after the change vs $steady before", aroundChange <= steady * 1.2f)
    }

    @Test
    fun `the pre-gain glides to a new curve instead of jumping`() {
        val equalizer = designed(listOf(TargetPoint(20f, 0.5f), TargetPoint(20_000f, 0.5f)))
        val before = equalizer.appliedGain
        equalizer.setCurve(listOf(TargetPoint(20f, 9f), TargetPoint(20_000f, 9f)), 0f)
        assertTrue(equalizer.beginBuffer(rate))
        equalizer.process(0, 0f)
        equalizer.endFrame()

        val afterOneFrame = equalizer.appliedGain
        assertTrue("jumped from $before to $afterOneFrame", before - afterOneFrame < 0.01f)
        repeat(4_800) { equalizer.endFrame() }
        assertEquals(equalizer.preGain, equalizer.appliedGain, 1e-6f)
    }
}
