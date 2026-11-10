package com.hardbasseq.eq.audio

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O])
class AudioPlaybackMonitorTest {
    private lateinit var context: Context
    private lateinit var monitor: AudioPlaybackMonitor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        monitor = AndroidAudioPlaybackMonitor(context)
    }

    @Test
    fun `initially not playing`() =
        runTest {
            assertFalse(monitor.isAnyAppPlaying.value)
        }

    @Test
    fun `can start monitoring without crash`() {
        monitor.startMonitoring()
        // Should not crash
    }

    @Test
    fun `can stop monitoring after starting`() {
        monitor.startMonitoring()
        monitor.stopMonitoring()
        // Should not crash
    }

    @Test
    fun `double start is safe`() {
        monitor.startMonitoring()
        monitor.startMonitoring()
        // Should not crash
    }

    @Test
    fun `double stop is safe`() {
        monitor.stopMonitoring()
        monitor.stopMonitoring()
        // Should not crash
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class AudioPlaybackMonitorLegacyTest {
    private lateinit var monitor: AudioPlaybackMonitor

    @Before
    fun setup() {
        monitor = NoOpAudioPlaybackMonitor()
    }

    @Test
    fun `noop monitor always returns false`() =
        runTest {
            assertFalse(monitor.isAnyAppPlaying.value)
            monitor.startMonitoring()
            assertFalse(monitor.isAnyAppPlaying.value)
        }

    @Test
    fun `noop monitor operations are safe`() {
        monitor.startMonitoring()
        monitor.stopMonitoring()
        // Should not crash
    }
}
