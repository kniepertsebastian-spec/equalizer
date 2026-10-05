package com.hardbasseq.eq.desktop.player

import com.hardbasseq.eq.desktop.ResolvedSound
import com.hardbasseq.eq.desktop.VirtualBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

private const val RATE = 48000
private const val SKIP = 12000 // settle filters and the limiter's lookahead delay

private fun sine(
    freqHz: Float,
    amplitude: Float,
    frames: Int,
    channels: Int = 1,
    silentChannels: Set<Int> = emptySet(),
): ShortArray =
    ShortArray(frames * channels) { index ->
        val frame = index / channels
        val channel = index % channels
        if (channel in silentChannels) 0 else (amplitude * 32767f * sin(2.0 * PI * freqHz * frame / RATE)).toInt().toShort()
    }

private fun rms(
    samples: ShortArray,
    channels: Int = 1,
    channel: Int = 0,
): Double {
    var sum = 0.0
    var count = 0
    var frame = SKIP
    while (frame * channels + channel < samples.size) {
        val v = samples[frame * channels + channel] / 32768.0
        sum += v * v
        count++
        frame++
    }
    return sqrt(sum / count)
}

private fun dbBetween(
    a: Double,
    b: Double,
): Double = 20 * log10(a / b)

private fun chain(
    gains: Map<Int, Float> = emptyMap(),
    subsonicHz: Float = 0f,
    channels: Int = 1,
): DesktopDspChain {
    val dsp = DesktopDspChain(ResolvedSound("test", gains, subsonicHz))
    dsp.configure(channels, RATE)
    return dsp
}

private fun band(freqHz: Int) = VirtualBands.bands.first { it.centerFreqHz == freqHz }.index

class DesktopDspChainTest {
    @Test
    fun `flat sound passes audio at unity level`() {
        val input = sine(1000f, 0.25f, RATE)
        val output = input.copyOf().also { chain().process(it) }

        assertEquals(0.0, dbBetween(rms(output), rms(input)), 0.2)
    }

    @Test
    fun `a boosted band raises its own frequency relative to others`() {
        val gains = mapOf(band(1000) to 6f)
        val atBand = sine(1000f, 0.2f, RATE).also { chain(gains).process(it) }
        val away = sine(100f, 0.2f, RATE).also { chain(gains).process(it) }

        // Preamp pulls the whole curve down, so compare the two tones with each other.
        assertEquals(6.0, dbBetween(rms(atBand), rms(away)), 1.0)
    }

    @Test
    fun `preamp keeps a fully boosted curve below clipping`() {
        val gains = VirtualBands.bands.associate { it.index to 6f }
        val sound = ResolvedSound("loud", gains, 0f)
        val dsp = DesktopDspChain(sound).also { it.configure(1, RATE) }
        val samples = sine(63f, 0.9f, RATE)

        dsp.process(samples)

        val peak = samples.drop(SKIP).maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak < 32767)
    }

    @Test
    fun `subsonic high-pass cuts deep bass but keeps the kick range`() {
        val deep = sine(20f, 0.2f, RATE * 2).also { chain(subsonicHz = 40f).process(it) }
        val kick = sine(200f, 0.2f, RATE * 2).also { chain(subsonicHz = 40f).process(it) }

        assertTrue(dbBetween(rms(deep), rms(kick)) < -9.0)
    }

    @Test
    fun `limiter holds a near full-scale signal under its ceiling`() {
        val samples = sine(1000f, 0.99f, RATE)

        chain().process(samples)

        val peak = samples.drop(SKIP).maxOf { abs(it.toInt()) } / 32768.0
        assertTrue("peak $peak", peak <= 0.91)
    }

    @Test
    fun `channels are filtered independently`() {
        val stereo = sine(1000f, 0.3f, RATE, channels = 2, silentChannels = setOf(1))

        chain(mapOf(band(1000) to 6f), channels = 2).process(stereo)

        assertTrue(rms(stereo, 2, 0) > 0.05)
        assertEquals(0.0, rms(stereo, 2, 1), 1e-6)
    }

    @Test
    fun `process before configure fails loudly`() {
        val unconfigured = DesktopDspChain(ResolvedSound("x", emptyMap(), 0f))

        val failed = runCatching { unconfigured.process(ShortArray(8)) }.isFailure

        assertTrue(failed)
    }

    @Test
    fun `rendering keeps the stream length and decodes 8-bit input`() {
        val frames = 4000
        val format = AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, RATE.toFloat(), 8, 1, 1, RATE.toFloat(), false)
        val stream = AudioInputStream(ByteArrayInputStream(ByteArray(frames) { 128.toByte() }), format, frames.toLong())

        val out = renderThroughChain(stream, DesktopDspChain(ResolvedSound("x", emptyMap(), 0f)))

        assertEquals(frames * 2, out.size)
    }
}
