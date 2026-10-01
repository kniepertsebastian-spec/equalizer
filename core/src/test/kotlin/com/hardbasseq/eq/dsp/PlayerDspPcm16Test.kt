package com.hardbasseq.eq.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PlayerDspPcm16Test {
    private val rate = 44_100

    private fun stereo(
        frames: Int,
        left: (Int) -> Double,
        right: (Int) -> Double,
    ): ShortArray {
        val out = ShortArray(frames * 2)
        for (n in 0 until frames) {
            out[2 * n] = (left(n) * 20_000).toInt().toShort()
            out[2 * n + 1] = (right(n) * 20_000).toInt().toShort()
        }
        return out
    }

    private fun tone(
        hz: Double,
        n: Int,
    ) = sin(2 * PI * hz * n / rate)

    private fun dsp(settings: PlayerDspSettings) =
        PlayerDspPcm16().also {
            it.configure(2)
            it.updateSettings(settings)
        }

    private fun peak(
        samples: ShortArray,
        fromIndex: Int,
    ) = (fromIndex until samples.size).maxOf { abs(samples[it].toInt()) }

    @Test
    fun `everything off leaves the audio untouched`() {
        val samples = stereo(2_000, { tone(60.0, it) }, { -tone(60.0, it) })
        val copy = samples.copyOf()

        dsp(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = false)).process(samples, 0, samples.size, rate)

        assertArrayEquals(copy, samples)
    }

    @Test
    fun `mono bass cancels out-of-phase deep bass between the channels`() {
        val samples = stereo(8_000, { tone(30.0, it) }, { -tone(30.0, it) })

        dsp(PlayerDspSettings(monoBassEnabled = true, limiterEnabled = false)).process(samples, 0, samples.size, rate)

        assertTrue("deep bass should collapse to mono", peak(samples, 4_000) < 4_000)
    }

    @Test
    fun `mono bass keeps the highs in stereo`() {
        val samples = stereo(8_000, { tone(2_000.0, it) }, { -tone(2_000.0, it) })

        dsp(PlayerDspSettings(monoBassEnabled = true, limiterEnabled = false)).process(samples, 0, samples.size, rate)

        assertTrue("highs must stay", peak(samples, 4_000) > 15_000)
    }

    @Test
    fun `the limiter holds loud audio under its threshold`() {
        val loud = ShortArray(16_000) { (tone(100.0, it / 2) * 32_000).toInt().toShort() }

        dsp(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = true)).process(loud, 0, loud.size, rate)

        assertTrue("limited peak was ${peak(loud, 0)}", peak(loud, 0) <= 29_300)
    }

    @Test
    fun `quiet audio passes the limiter at about the same level`() {
        val samples = stereo(8_000, { tone(1_000.0, it) * 0.5 }, { tone(1_000.0, it) * 0.5 })

        dsp(PlayerDspSettings(monoBassEnabled = false, limiterEnabled = true)).process(samples, 0, samples.size, rate)

        assertTrue(peak(samples, 1_000) in 9_500..10_100)
    }

    @Test
    fun `mono audio only gets the limiter`() {
        val processor = PlayerDspPcm16().also { it.configure(1) }
        val samples = ShortArray(2_000) { (tone(100.0, it) * 32_000).toInt().toShort() }

        processor.process(samples, 0, samples.size, rate)

        assertTrue(peak(samples, 0) <= 29_300)
    }
}
