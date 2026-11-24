package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

// Roadmap 09, P9-1: the player's chain on 32-bit floats, and its PCM16 adapter.
class PlayerDspChainTest {
    private val rate = 44_100
    private val curve = listOf(TargetPoint(30f, 5f), TargetPoint(100f, 3f), TargetPoint(1_000f, 0f), TargetPoint(8_000f, 2f))

    private fun chain(settings: PlayerDspSettings = PlayerDspSettings()) =
        PlayerDspChain().also {
            it.configure(2)
            it.updateSettings(settings)
        }

    // Stereo, interleaved; bass and mids at [level] of full scale.
    private fun music(
        frames: Int,
        level: Double,
    ) = FloatArray(frames * 2) {
        val n = it / 2
        val bass = sin(2 * PI * 55.0 * n / rate)
        val mid = sin(2 * PI * 700.0 * n / rate)
        val sign = if (it % 2 == 0) 1.0 else -1.0
        (level * (0.6 * bass + sign * 0.4 * mid)).toFloat()
    }

    private fun toPcm16(samples: FloatArray) = ShortArray(samples.size) { Math.round(samples[it] * 32768f).toShort() }

    private fun rms(
        samples: FloatArray,
        from: Int,
    ): Double {
        var sum = 0.0
        for (i in from until samples.size) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / (samples.size - from))
    }

    private fun rms(
        samples: ShortArray,
        from: Int,
    ) = rms(FloatArray(samples.size) { samples[it] / 32768f }, from)

    private fun peak(
        samples: FloatArray,
        from: Int = 0,
    ) = (from until samples.size).maxOf { abs(samples[it]) }

    @Test
    fun `float and PCM16 paths give the same level in the linear range`() {
        val settings = PlayerDspSettings(monoBassEnabled = true, limiterEnabled = true, softClipEnabled = true)
        val source = music(30_000, level = 0.3)

        val floatChain = chain(settings).also { it.updateCurve(curve, 25f) }
        floatChain.updateVirtualBass(0.5f)
        val asFloat = source.copyOf()
        floatChain.processFloat(asFloat, 0, asFloat.size, rate)

        val pcm =
            PlayerDspPcm16().also {
                it.configure(2)
                it.updateSettings(settings)
                it.updateCurve(curve, 25f)
                it.updateVirtualBass(0.5f)
            }
        val asPcm16 = toPcm16(source)
        pcm.process(asPcm16, 0, asPcm16.size, rate)

        val differenceDb = 20 * log10(rms(asFloat, 10_000) / rms(asPcm16, 10_000))
        assertTrue("float vs PCM16: $differenceDb dB", abs(differenceDb) < 0.1)
        assertTrue("the chain changed the audio", (source.indices).any { abs(source[it] - asFloat[it]) > 0.01f })
    }

    @Test
    fun `PCM16 in, float out equals converting first and processing floats`() {
        val pcm = toPcm16(music(8_000, level = 0.5))
        val viaPcm16 = FloatArray(pcm.size + 4)
        chain().also { it.updateCurve(curve, 25f) }.processPcm16ToFloat(pcm, 0, viaPcm16, 4, pcm.size, rate)

        val floats = FloatArray(pcm.size) { pcm[it] / 32768f }
        chain().also { it.updateCurve(curve, 25f) }.processFloat(floats, 0, floats.size, rate)

        assertArrayEquals(floats, viaPcm16.copyOfRange(4, viaPcm16.size), 0f)
    }

    @Test
    fun `with every stage off float audio is untouched`() {
        val samples = music(4_000, level = 1.4)
        val copy = samples.copyOf()

        chain(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = false, softClipEnabled = false))
            .processFloat(samples, 0, samples.size, rate)

        assertArrayEquals(copy, samples, 0f)
    }

    @Test
    fun `with every stage off PCM16 still becomes float without loss`() {
        val pcm = shortArrayOf(Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE, 12_345)
        val out = FloatArray(pcm.size)

        chain(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = false, softClipEnabled = false))
            .processPcm16ToFloat(pcm, 0, out, 0, pcm.size, rate)

        for (i in pcm.indices) assertEquals(pcm[i] / 32768f, out[i], 0f)
    }

    @Test
    fun `the float path keeps headroom above full scale when nothing limits`() {
        val samples = music(4_000, level = 0.9)

        chain(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = false, softClipEnabled = false, outputDriveDb = 4f))
            .processFloat(samples, 0, samples.size, rate)

        // 0.9 * +4 dB = 1.43: a 16-bit path would have clipped this at 1.0.
        assertTrue("peak was ${peak(samples)}", peak(samples) > 1.3f)
    }

    @Test
    fun `soft clipper and ceiling hold a driven loud signal under full scale`() {
        val samples = music(20_000, level = 1.2)

        chain(PlayerDspSettings(monoBassEnabled = true, limiterEnabled = true, softClipEnabled = true, outputDriveDb = 4f))
            .processFloat(samples, 0, samples.size, rate)

        assertTrue("peak was ${peak(samples)}", peak(samples) <= 0.9773f + 1e-4f)
    }

    @Test
    fun `the output drive raises quiet audio by its gain`() {
        val quiet = music(20_000, level = 0.1)
        val plain = quiet.copyOf()
        chain(PlayerDspSettings(monoBassEnabled = false)).processFloat(plain, 0, plain.size, rate)
        val driven = quiet.copyOf()
        chain(PlayerDspSettings(monoBassEnabled = false, outputDriveDb = 3f)).processFloat(driven, 0, driven.size, rate)

        val gainDb = 20 * log10(rms(driven, 1_000) / rms(plain, 1_000))
        assertEquals(3.0, gainDb, 0.05)
    }

    @Test
    fun `the drive is clamped to 0 to +4 dB`() {
        val quiet = music(20_000, level = 0.05)
        val reference = quiet.copyOf()
        chain(PlayerDspSettings(monoBassEnabled = false, outputDriveDb = 4f)).processFloat(reference, 0, reference.size, rate)
        val tooMuch = quiet.copyOf()
        chain(PlayerDspSettings(monoBassEnabled = false, outputDriveDb = 12f)).processFloat(tooMuch, 0, tooMuch.size, rate)
        val negative = quiet.copyOf()
        chain(PlayerDspSettings(monoBassEnabled = false, outputDriveDb = -6f)).processFloat(negative, 0, negative.size, rate)
        val unity = quiet.copyOf()
        chain(PlayerDspSettings(monoBassEnabled = false)).processFloat(unity, 0, unity.size, rate)

        assertArrayEquals(reference, tooMuch, 0f)
        assertArrayEquals(unity, negative, 0f)
    }

    @Test
    fun `the float path gives the same output however the audio is cut into buffers`() {
        val source = music(20_000, level = 1.1)

        fun configured() =
            chain().also {
                it.updateCurve(curve, 25f)
                it.updateVirtualBass(0.6f)
            }

        val atOnce = source.copyOf()
        configured().processFloat(atOnce, 0, atOnce.size, rate)

        val inBuffers = source.copyOf()
        val dsp = configured()
        var from = 0
        var size = 37
        while (from < 20_000) {
            val frames = minOf(size, 20_000 - from)
            dsp.processFloat(inBuffers, from * 2, frames * 2, rate)
            from += frames
            size = if (size > 2_000) 37 else size * 3
        }

        assertArrayEquals(atOnce, inBuffers, 0f)
    }

    @Test
    fun `the safety ceiling delays the audio by 2 ms, not 5`() {
        // Soft clipper off, so only the limiter's delay shows; an impulse well under the ceiling.
        val dsp = chain(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = true, softClipEnabled = false))
        val samples = FloatArray(2 * 1_000)
        samples[0] = 0.5f
        samples[1] = 0.5f

        dsp.processFloat(samples, 0, samples.size, rate)

        val arrival = (0 until 1_000).first { samples[2 * it] != 0f }
        assertEquals(Math.round(0.002 * rate).toInt(), arrival)
    }

    // --- Roadmap 10 ----------------------------------------------------------------------------

    // Level of one frequency in the left channel of interleaved stereo, by direct correlation over
    // whole periods (frequencies that divide the sample rate's second), in dB re full scale.
    private fun levelDb(
        samples: FloatArray,
        freqHz: Double,
        fromFrame: Int,
        frames: Int,
    ): Double {
        var real = 0.0
        var imag = 0.0
        for (n in fromFrame until fromFrame + frames) {
            val phase = 2 * PI * freqHz * n / rate
            real += samples[2 * n] * kotlin.math.cos(phase)
            imag += samples[2 * n] * sin(phase)
        }
        return 20 * log10(2 * sqrt(real * real + imag * imag) / frames + 1e-12)
    }

    private fun tone(
        freqHz: Double,
        level: Double,
        frames: Int,
    ) = FloatArray(frames * 2) { (level * sin(2 * PI * freqHz * (it / 2) / rate)).toFloat() }

    // Only the stages under test: no mono bass, no soft clipper, no ceiling.
    private val bare = PlayerDspSettings(monoBassEnabled = false, limiterEnabled = false, softClipEnabled = false)

    @Test
    fun `the virtual bass takes its bass from before the subsonic high-pass`() {
        // A 45 Hz kick tone on a small speaker preset: the 65 Hz cut takes the tone itself away,
        // but its harmonics (90 and 135 Hz) are generated from the uncut bass.
        val frames = rate * 2
        val input = tone(45.0, 0.25, frames)

        fun harmonicsAndFundamental(cutoffHz: Float): Pair<Double, Double> {
            val dsp = chain(bare)
            dsp.updateCurve(listOf(TargetPoint(20f, 0f), TargetPoint(20_000f, 0f)), cutoffHz, 1f)
            dsp.updateVirtualBass(1f)
            val out = input.copyOf()
            dsp.processFloat(out, 0, out.size, rate)
            return levelDb(out, 90.0, rate, rate) to levelDb(out, 45.0, rate, rate)
        }
        val (octaveUncut, fundamentalUncut) = harmonicsAndFundamental(0f)
        val (octaveCut, fundamentalCut) = harmonicsAndFundamental(65f)

        assertTrue("the high-pass still cuts the tone: $fundamentalCut vs $fundamentalUncut dB", fundamentalCut < fundamentalUncut - 12.0)
        // Before, the generator only got what the high-pass let through: ~20 dB less octave.
        assertEquals("octave with vs without the cut", octaveUncut, octaveCut, 1.5)
    }

    @Test
    fun `the virtual bass character changes the 3rd harmonic's share`() {
        val frames = rate * 2
        val input = tone(60.0, 0.25, frames)

        fun thirdReSecond(share: Float): Double {
            val dsp = chain(bare.copy(virtualBassThirdHarmonicShare = share))
            dsp.updateVirtualBass(1f)
            val out = input.copyOf()
            dsp.processFloat(out, 0, out.size, rate)
            return levelDb(out, 180.0, rate, rate) - levelDb(out, 120.0, rate, rate)
        }
        val warm = thirdReSecond(BassExciterSettings.WARM_THIRD_HARMONIC_SHARE)
        val balanced = thirdReSecond(BassExciterSettings.DEFAULT_THIRD_HARMONIC_SHARE)
        val punch = thirdReSecond(BassExciterSettings.PUNCH_THIRD_HARMONIC_SHARE)
        assertEquals("warm", 10 * log10(0.15 / 0.85), warm, 1.0)
        assertEquals("balanced", 10 * log10(0.25 / 0.75), balanced, 1.0)
        assertEquals("punch", 10 * log10(0.45 / 0.55), punch, 1.0)

        // Switching while playing takes effect without rebuilding the chain.
        val dsp = chain(bare)
        dsp.updateVirtualBass(1f)
        dsp.updateSettings(bare.copy(virtualBassThirdHarmonicShare = BassExciterSettings.PUNCH_THIRD_HARMONIC_SHARE))
        val out = input.copyOf()
        dsp.processFloat(out, 0, out.size, rate)
        assertEquals(punch, levelDb(out, 180.0, rate, rate) - levelDb(out, 120.0, rate, rate), 0.1)
    }

    @Test
    fun `parametric filters reach the player's equalizer`() {
        val frames = rate
        val input = tone(1_000.0, 0.1, frames)
        val dsp = chain(bare)
        // Ratio 1: the whole boost is taken back by the pre-gain, so the tone at the peak stays put
        // and one an octave away drops by the same amount.
        dsp.updateCurve(emptyList(), 0f, 1f, listOf(ParametricFilter(ParametricFilterType.PEAK, 1_000f, 6f, 4f)))
        val out = input.copyOf()
        dsp.processFloat(out, 0, out.size, rate)
        assertEquals(20 * log10(0.1), levelDb(out, 1_000.0, rate / 2, rate / 2), 0.1)
    }
}
