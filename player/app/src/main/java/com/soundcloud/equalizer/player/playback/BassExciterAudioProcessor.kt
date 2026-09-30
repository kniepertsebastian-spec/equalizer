package com.soundcloud.equalizer.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.hardbasseq.eq.dsp.BassExciterPcm16
import com.hardbasseq.eq.dsp.BassExciterSettings
import java.nio.ByteBuffer

// "Virtual bass" for the player's own playback: synthesizes harmonics of the
// deep bass (BassExciter) so small speakers - a phone, a Bluetooth box, car door
// speakers - *sound* like they reproduce more low end than they physically can.
//
// This only exists in ExoPlayer's own audio chain. The system-wide effect path
// HardBass EQ uses for other apps (Android Equalizer/DynamicsProcessing attached
// to their audio session) has no hook for custom sample processing, so Spotify &
// co. cannot get it - see docs/DECISIONS.md.
//
// Always in the chain for 16-bit PCM and passes audio through untouched while
// VirtualBassState.mix is 0, so toggling it needs no player rebuild.
@OptIn(UnstableApi::class)
class BassExciterAudioProcessor : BaseAudioProcessor() {
    private val exciter = BassExciterPcm16()
    private var sampleRateHz = 0
    private var appliedMix = -1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Anything but 16-bit PCM (float output, 24-bit, ...) stays unprocessed:
        // NOT_SET marks this processor inactive so ExoPlayer skips it entirely,
        // instead of failing playback with UnhandledAudioFormatException.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        sampleRateHz = inputAudioFormat.sampleRate
        exciter.configure(inputAudioFormat.channelCount)
        appliedMix = -1f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        val mix = VirtualBassState.mix
        if (mix != appliedMix) {
            exciter.updateSettings(BassExciterSettings(enabled = mix > 0f, mix = mix))
            appliedMix = mix
        }

        val output = replaceOutputBuffer(size)
        if (mix <= 0f) {
            output.put(inputBuffer)
        } else {
            val shorts = inputBuffer.asShortBuffer()
            val samples = ShortArray(shorts.remaining())
            shorts.get(samples)
            inputBuffer.position(inputBuffer.limit())

            exciter.process(samples, 0, samples.size, sampleRateHz)

            output.asShortBuffer().put(samples)
            output.position(samples.size * 2)
        }
        output.flip()
    }

    // A seek or track change: drop the filters' memory of the previous audio so it
    // cannot click into the new one.
    override fun onFlush() {
        exciter.reset()
    }

    override fun onReset() {
        exciter.reset()
        sampleRateHz = 0
        appliedMix = -1f
    }
}
