package com.auracurve.app.player.playback

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.auracurve.app.dsp.PlayerDspChain
import com.auracurve.app.dsp.TpdfDither
import java.nio.ByteBuffer

// The player's whole audio chain: the exact target curve, virtual bass, mono bass, output
// drive, soft clipper and the safety ceiling (see PlayerDspChain), each switchable through its
// [source] (by default the process-wide PlayerCurveState, VirtualBassState and PlayerDspState)
// without a player rebuild. One processor, so the stages hand floats to each other and nothing
// clips in between. The ceiling delays the audio by 2 ms.
//
// 32-bit float (roadmap 09, P9-1): whatever linear PCM comes in (16, 24 or 32 bit integer, or
// float) is processed as float and, with [floatOutput] (the default), leaves as
// ENCODING_PCM_FLOAT - no dither and no 16-bit clipping before the system's
// DynamicsProcessing, which gets the full headroom. With floatOutput = false the result goes
// back to PCM16 with TPDF dither, the fallback for an output that takes 16-bit only. The
// processor is never inactive for these four encodings, so the system EQ (zeroed while the
// player's own curve runs) can never leave the audio unprocessed. Float out needs a chain behind it that
// takes float: see PlayerAudioProcessorChain.
//
// Virtual bass only exists here: the system-wide effect path HardBass EQ uses for other
// apps (Android Equalizer/DynamicsProcessing attached to their audio session) has no hook
// for custom sample processing, so Spotify & co. cannot get it - see docs/DECISIONS.md.
@OptIn(UnstableApi::class)
class PlayerDspAudioProcessor(
    private val floatOutput: Boolean = true,
    private val source: PlayerDspSource = PlayerDspSource.ProcessWide,
) : BaseAudioProcessor() {
    private val dsp = PlayerDspChain()
    private val dither = TpdfDither()
    private var sampleRateHz = 0
    private var inputEncoding = C.ENCODING_INVALID
    private var outputEncoding = C.ENCODING_INVALID
    private var appliedCurve: PlayerCurve? = null

    // Reused from buffer to buffer (it only grows): a new array for every buffer, many times a
    // second, kept the garbage collector busy during playback.
    private var samples = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding !in SUPPORTED_ENCODINGS) {
            // Cannot happen behind DefaultAudioSink as configured in AudioPlayerService (its
            // ToInt16PcmAudioProcessor runs first), so say so loudly instead of failing playback.
            Log.w(TAG, "Unsupported encoding $encoding: the player's sound chain is bypassed")
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRateHz = inputAudioFormat.sampleRate
        inputEncoding = encoding
        outputEncoding = if (floatOutput) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT
        dsp.configure(inputAudioFormat.channelCount)
        dsp.updateSettings(source.currentSettings)
        appliedCurve = null
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, outputEncoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        dsp.updateSettings(source.currentSettings)
        dsp.updateVirtualBass(source.virtualBassMix)
        val curve = source.curve
        if (curve !== appliedCurve) {
            dsp.updateCurve(curve.curve, curve.subsonicCutoffHz, curve.headroomSafetyRatio, curve.parametricFilters)
            appliedCurve = curve
        }

        val count = size / bytesPerSample(inputEncoding)
        if (samples.size < count) samples = FloatArray(count)
        val samples = samples
        readSamples(inputBuffer, samples, count)

        dsp.processFloat(samples, 0, count, sampleRateHz)

        val output = replaceOutputBuffer(count * bytesPerSample(outputEncoding))
        // Absolute puts: no view buffer per audio buffer (asFloatBuffer/asShortBuffer allocated
        // one each time on the audio thread). Media3 hands out buffers in native byte order.
        if (outputEncoding == C.ENCODING_PCM_FLOAT) {
            for (i in 0 until count) output.putFloat(i * 4, samples[i])
        } else {
            for (i in 0 until count) output.putShort(i * 2, dither.toPcm16(samples[i]))
        }
        output.position(0)
        output.limit(count * bytesPerSample(outputEncoding))
    }

    // Absolute reads from the input's position on, then the input counts as consumed.
    private fun readSamples(
        input: ByteBuffer,
        out: FloatArray,
        count: Int,
    ) {
        val start = input.position()
        when (inputEncoding) {
            C.ENCODING_PCM_16BIT -> for (i in 0 until count) out[i] = input.getShort(start + i * 2) * PCM16_TO_FLOAT
            C.ENCODING_PCM_FLOAT -> for (i in 0 until count) out[i] = input.getFloat(start + i * 4)
            C.ENCODING_PCM_32BIT -> for (i in 0 until count) out[i] = (input.getInt(start + i * 4) * PCM32_TO_FLOAT).toFloat()
            C.ENCODING_PCM_24BIT -> {
                for (i in 0 until count) {
                    val at = start + i * 3
                    // Little-endian, the top byte carries the sign.
                    val value =
                        (input.get(at).toInt() and 0xFF) or
                            ((input.get(at + 1).toInt() and 0xFF) shl 8) or
                            (input.get(at + 2).toInt() shl 16)
                    out[i] = value * PCM24_TO_FLOAT
                }
            }
        }
        input.position(start + count * bytesPerSample(inputEncoding))
    }

    override fun onFlush() {
        dsp.reset()
    }

    override fun onReset() {
        dsp.reset()
        sampleRateHz = 0
        inputEncoding = C.ENCODING_INVALID
        outputEncoding = C.ENCODING_INVALID
    }

    companion object {
        private const val TAG = "PlayerDsp"
        private val SUPPORTED_ENCODINGS =
            setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)
        private const val PCM16_TO_FLOAT = 1f / 32_768f
        private const val PCM24_TO_FLOAT = 1f / 8_388_608f
        private const val PCM32_TO_FLOAT = 1.0 / 2_147_483_648.0

        private fun bytesPerSample(encoding: Int): Int =
            when (encoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_24BIT -> 3
                else -> 4
            }
    }
}
