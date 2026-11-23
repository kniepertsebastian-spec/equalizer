package com.auracurve.app.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor

// The audio processors behind DefaultAudioSink's own conversion, in this order:
//   silence skipping (16-bit only) -> the player's sound chain (16-bit in, float out) -> speed (Sonic).
//
// Media3's DefaultAudioProcessorChain does the same work but puts custom processors *first*,
// in front of SilenceSkippingAudioProcessor - which throws on anything but 16-bit PCM even when
// skipping is off. With the sound chain handing out 32-bit float (roadmap 09, P9-1), that order
// would fail every track; here silence skipping sees the 16-bit input instead, and Sonic, which
// takes PCM16 and float since Media3 1.11.1 (SonicAudioProcessor.configure()), the float output.
//
// The rest mirrors DefaultAudioProcessorChain (Media3 1.11.1): Sonic does speed and pitch and
// maps durations, silence skipping counts the frames it dropped.
@OptIn(UnstableApi::class)
class PlayerAudioProcessorChain(
    soundChain: AudioProcessor,
) : DefaultAudioSink.AudioProcessorChain {
    private val silenceSkipping = SilenceSkippingAudioProcessor()
    private val sonic = SonicAudioProcessor()
    private val processors = arrayOf<AudioProcessor>(silenceSkipping, soundChain, sonic)

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        sonic.setSpeed(playbackParameters.speed)
        sonic.setPitch(playbackParameters.pitch)
        return playbackParameters
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
        silenceSkipping.setEnabled(skipSilenceEnabled)
        return skipSilenceEnabled
    }

    override fun getMediaDuration(playoutDuration: Long): Long =
        if (sonic.isActive) sonic.getMediaDuration(playoutDuration) else playoutDuration

    override fun getSkippedOutputFrameCount(): Long = silenceSkipping.skippedFrames
}
