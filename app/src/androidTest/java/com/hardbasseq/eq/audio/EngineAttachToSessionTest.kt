package com.hardbasseq.eq.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Roadmap 06, P6-3: the engine attaches to a real audio session - the thing the JVM tests can only
 * fake. An AudioTrack of this test process stands in for another app's player. On a device without
 * the Equalizer / DynamicsProcessing effect the test is skipped, not failed.
 */
@RunWith(AndroidJUnit4::class)
class EngineAttachToSessionTest {
    private lateinit var track: AudioTrack
    private lateinit var engine: AndroidAudioEngine

    @Before
    fun setUp() {
        val audioManager = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(AudioManager::class.java)
        track =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                ).setBufferSizeInBytes(SAMPLE_RATE)
                .setSessionId(audioManager.generateAudioSessionId())
                .build()
        engine = AndroidAudioEngine(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        engine.markDetached()
        track.release()
    }

    @Test
    fun attach_toALiveSession_reportsActiveAndThenDetaches() =
        runBlocking {
            val attached = engine.attach(AudioSession(sessionId = track.audioSessionId, packageName = "androidTest"))
            assumeTrue("no usable audio effect on this device", attached)

            assertTrue("state was ${engine.state.value}", engine.state.value is AudioEngineState.Active)

            engine.detach()
            assertTrue("state was ${engine.state.value}", engine.state.value !is AudioEngineState.Active)
        }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
