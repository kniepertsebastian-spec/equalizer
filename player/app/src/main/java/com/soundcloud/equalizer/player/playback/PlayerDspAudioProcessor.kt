package com.soundcloud.equalizer.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.hardbasseq.eq.dsp.PlayerDspPcm16
import java.nio.ByteBuffer

// The last stage of the player's own audio chain: mono bass and a lookahead limiter
// (see PlayerDspPcm16), each switchable through PlayerDspState without a player
// rebuild. Goes after the virtual-bass processor so the limiter also catches the
// harmonics it adds. The limiter delays the audio by a few milliseconds.
@OptIn(UnstableApi::class)
class PlayerDspAudioProcessor : BaseAudioProcessor() {
    private val dsp = PlayerDspPcm16()
    private var sampleRateHz = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Same rule as the virtual-bass processor: only 16-bit PCM is processed.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        sampleRateHz = inputAudioFormat.sampleRate
        dsp.configure(inputAudioFormat.channelCount)
        dsp.updateSettings(PlayerDspState.settings.value)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        dsp.updateSettings(PlayerDspState.settings.value)

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
