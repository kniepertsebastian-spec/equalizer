package com.hardbasseq.eq.audio.spike

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * A minimal, fully self-owned audio session for the M0 session-attach spike:
 * plays a quiet, looping sine tone so an [android.media.audiofx.Equalizer]/
 * [android.media.audiofx.DynamicsProcessing] instance attached to its session
 * has something audible to act on. This never touches any other app's
 * session or the global mix (session `0`) — see roadmap §2 on why that
 * distinction matters.
 */
class TestTonePlayer(private val audioManager: AudioManager) {

    private var audioTrack: AudioTrack? = null

    val audioSessionId: Int
        get() = audioTrack?.audioSessionId ?: AudioManager.ERROR

    val isPlaying: Boolean
        get() = audioTrack != null

    fun start(frequencyHz: Double = 220.0) {
        if (audioTrack != null) return

        val sampleRate = 44_100
        val mono = SineWaveGenerator.generateMonoPcm16(
            sampleRateHz = sampleRate,
            frequencyHz = frequencyHz,
            durationSeconds = 1.0,
        )
        val stereo = SineWaveGenerator.interleaveStereo(mono)
        val frameCount = stereo.size / 2

        val sessionId = audioManager.generateAudioSessionId()

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(stereo.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setSessionId(sessionId)
            .build()

        track.write(stereo, 0, stereo.size)
        track.setLoopPoints(0, frameCount, -1)
        track.play()
        audioTrack = track
    }

    fun stop() {
        val track = audioTrack ?: return
        audioTrack = null
        try {
            track.stop()
        } finally {
            track.release()
        }
    }
}
