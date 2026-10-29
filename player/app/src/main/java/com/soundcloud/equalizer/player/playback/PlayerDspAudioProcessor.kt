package com.soundcloud.equalizer.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.hardbasseq.eq.dsp.PlayerDspPcm16
import java.nio.ByteBuffer

// The player's whole audio chain: the exact target curve, virtual bass, mono bass and a
// lookahead limiter (see PlayerDspPcm16), each switchable through PlayerCurveState,
// VirtualBassState and PlayerDspState without a player rebuild. One processor, so the
// stages hand floats to each other and nothing clips in between. The limiter delays the
// audio by a few milliseconds.
//
// Virtual bass only exists here: the system-wide effect path HardBass EQ uses for other
// apps (Android Equalizer/DynamicsProcessing attached to their audio session) has no hook
// for custom sample processing, so Spotify & co. cannot get it - see docs/DECISIONS.md.
@OptIn(UnstableApi::class)
class PlayerDspAudioProcessor : BaseAudioProcessor() {
    private val dsp = PlayerDspPcm16()
    private var sampleRateHz = 0
    private var appliedCurve: PlayerCurve? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Anything but 16-bit PCM (float output, 24-bit, ...) stays unprocessed: NOT_SET
        // marks this processor inactive so ExoPlayer skips it entirely, instead of failing
        // playback with UnhandledAudioFormatException.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        sampleRateHz = inputAudioFormat.sampleRate
        dsp.configure(inputAudioFormat.channelCount)
        dsp.updateSettings(PlayerDspState.settings.value)
        appliedCurve = null
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        dsp.updateSettings(PlayerDspState.settings.value)
        dsp.updateVirtualBass(VirtualBassState.mix)
        val curve = PlayerCurveState.current
        if (curve !== appliedCurve) {
            dsp.updateCurve(curve.curve, curve.subsonicCutoffHz)
            appliedCurve = curve
        }

        val shorts = inputBuffer.asShortBuffer()
        val samples = ShortArray(shorts.remaining())
        shorts.get(samples)
        inputBuffer.position(inputBuffer.limit())

        dsp.process(samples, 0, samples.size, sampleRateHz)

        val output = replaceOutputBuffer(samples.size * 2)
        output.asShortBuffer().put(samples)
        output.position(samples.size * 2)
        output.flip()
    }

    override fun onFlush() {
        dsp.reset()
    }

    override fun onReset() {
        dsp.reset()
        sampleRateHz = 0
    }
}
