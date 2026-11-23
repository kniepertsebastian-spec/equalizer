package com.auracurve.app.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auracurve.app.preset.TargetPoint
import com.google.common.collect.ImmutableList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Roadmap 09, P9-1: the player's sound chain takes 16/24/32-bit and float PCM, works in float
// and hands float on - never NOT_SET (= silently skipped) for audio it can meet.
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlayerDspAudioProcessorTest {
    private val rate = 44_100

    @After
    fun tearDown() {
        VirtualBassState.mix = 0f
        PlayerCurveState.current = PlayerCurve(emptyList(), 0f)
    }

    private fun format(encoding: Int) = AudioProcessor.AudioFormat(rate, 2, encoding)

    // Stereo sine, interleaved, as -1..1 floats.
    private fun sine(
        hz: Double,
        level: Double,
        frames: Int = 8_192,
    ) = FloatArray(frames * 2) { (level * sin(2 * PI * hz * (it / 2) / rate)).toFloat() }

    private fun buffer(
        samples: FloatArray,
        encoding: Int,
    ): ByteBuffer {
        val bytesPerSample =
            when (encoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_24BIT -> 3
                else -> 4
            }
        val buffer = ByteBuffer.allocateDirect(samples.size * bytesPerSample).order(ByteOrder.nativeOrder())
        for (sample in samples) {
            when (encoding) {
                C.ENCODING_PCM_16BIT -> buffer.putShort((sample * 32_767f).roundToInt().toShort())
                C.ENCODING_PCM_24BIT -> {
                    val value = (sample * 8_388_607f).roundToInt()
                    buffer.put(value.toByte())
                    buffer.put((value shr 8).toByte())
                    buffer.put((value shr 16).toByte())
                }
                C.ENCODING_PCM_32BIT -> buffer.putInt((sample * 2_147_483_647.0).roundToInt())
                else -> buffer.putFloat(sample)
            }
        }
        buffer.flip()
        return buffer
    }

    private fun run(
        processor: AudioProcessor,
        input: ByteBuffer,
    ): ByteBuffer {
        processor.queueInput(input)
        return processor.output
    }

    private fun floats(output: ByteBuffer): FloatArray {
        val out = FloatArray(output.remaining() / 4)
        for (i in out.indices) out[i] = output.getFloat(output.position() + i * 4)
        return out
    }

    private fun configured(
        encoding: Int,
        floatOutput: Boolean = true,
    ) = PlayerDspAudioProcessor(floatOutput).also {
        it.configure(format(encoding))
        it.flush()
    }

    private fun rms(
        samples: FloatArray,
        from: Int,
    ): Double {
        var sum = 0.0
        for (i in from until samples.size) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / (samples.size - from))
    }

    @Test
    fun `float input is processed and stays float`() {
        val processor = PlayerDspAudioProcessor()

        val output = processor.configure(format(C.ENCODING_PCM_FLOAT))

        assertEquals(C.ENCODING_PCM_FLOAT, output.encoding)
        assertTrue(processor.isActive)
    }

    @Test
    fun `16, 24 and 32-bit input become float`() {
        for (encoding in intArrayOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)) {
            val processor = PlayerDspAudioProcessor()

            val output = processor.configure(format(encoding))

            assertEquals("encoding $encoding", C.ENCODING_PCM_FLOAT, output.encoding)
            assertEquals(rate, output.sampleRate)
            assertEquals(2, output.channelCount)
            assertTrue(processor.isActive)
        }
    }

    @Test
    fun `the 16-bit fallback hands 16-bit on`() {
        val processor = PlayerDspAudioProcessor(floatOutput = false)

        assertEquals(C.ENCODING_PCM_16BIT, processor.configure(format(C.ENCODING_PCM_16BIT)).encoding)
        assertEquals(C.ENCODING_PCM_16BIT, processor.configure(format(C.ENCODING_PCM_FLOAT)).encoding)
    }

    @Test
    fun `an encoding it cannot read is bypassed, not a playback error`() {
        val processor = PlayerDspAudioProcessor()

        processor.configure(format(C.ENCODING_PCM_8BIT))

        assertFalse(processor.isActive)
    }

    @Test
    fun `every input encoding decodes to the same audio`() {
        val source = sine(220.0, 0.4)
        val reference = floats(run(configured(C.ENCODING_PCM_FLOAT), buffer(source, C.ENCODING_PCM_FLOAT)))

        for (encoding in intArrayOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)) {
            val decoded = floats(run(configured(encoding), buffer(source, encoding)))
            assertEquals(reference.size, decoded.size)
            val worst = reference.indices.maxOf { abs(reference[it] - decoded[it]) }
            // One 16-bit step is 3e-5.
            assertTrue("encoding $encoding: worst difference $worst", worst < 1e-4f)
        }
    }

    @Test
    fun `the input buffer is consumed`() {
        val input = buffer(sine(220.0, 0.4, frames = 100), C.ENCODING_PCM_16BIT)

        val output = run(configured(C.ENCODING_PCM_16BIT), input)

        assertFalse(input.hasRemaining())
        assertEquals(100 * 2 * 4, output.remaining())
    }

    @Test
    fun `the target curve changes the float signal`() {
        val source = sine(1_000.0, 0.3)
        val flat = floats(run(configured(C.ENCODING_PCM_FLOAT), buffer(source, C.ENCODING_PCM_FLOAT)))

        // +6 dB in the bass: the pre-gain makes room for it, so 1 kHz comes out about 6 dB lower.
        PlayerCurveState.current = PlayerCurve(listOf(TargetPoint(30f, 6f), TargetPoint(120f, 6f), TargetPoint(400f, 0f)), 0f)
        val curved = floats(run(configured(C.ENCODING_PCM_FLOAT), buffer(source, C.ENCODING_PCM_FLOAT)))

        val changeDb = 20 * log10(rms(curved, 4_000) / rms(flat, 4_000))
        assertTrue("1 kHz changed by $changeDb dB", changeDb < -3.0)
    }

    @Test
    fun `virtual bass changes the float signal`() {
        val source = sine(55.0, 0.3)
        val plain = floats(run(configured(C.ENCODING_PCM_FLOAT), buffer(source, C.ENCODING_PCM_FLOAT)))

        VirtualBassState.mix = 1f
        val excited = floats(run(configured(C.ENCODING_PCM_FLOAT), buffer(source, C.ENCODING_PCM_FLOAT)))

        val difference = (4_000 until plain.size).maxOf { abs(plain[it] - excited[it]) }
        assertTrue("largest difference $difference", difference > 0.01f)
    }

    @Test
    fun `the chain keeps 16-bit-only silence skipping in front and float-capable Sonic behind`() {
        val chain = PlayerAudioProcessorChain(PlayerDspAudioProcessor())
        val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(chain.audioProcessors))

        // Silence skipping on and a speed change, so both of Media3's own processors are active.
        chain.applySkipSilenceEnabled(true)
        chain.applyPlaybackParameters(PlaybackParameters(1.5f))
        val output = pipeline.configure(format(C.ENCODING_PCM_16BIT))

        assertEquals(C.ENCODING_PCM_FLOAT, output.encoding)
        assertTrue(chain.audioProcessors.all { it.isActive })
    }

    @Test
    fun `the chain at normal speed still hands float to the sink`() {
        val chain = PlayerAudioProcessorChain(PlayerDspAudioProcessor())
        val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(chain.audioProcessors))

        val output = pipeline.configure(format(C.ENCODING_PCM_16BIT))

        assertEquals(C.ENCODING_PCM_FLOAT, output.encoding)
        assertEquals(1_000L, chain.getMediaDuration(1_000L))
        assertEquals(0L, chain.skippedOutputFrameCount)
    }
}
