package com.soundcloud.equalizer.player.playback

import androidx.media3.exoplayer.ExoPlayer
import com.hardbasseq.eq.playback.SleepTimerSetting
import com.hardbasseq.eq.playback.SleepTimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PlaybackFadeControllerTest {
    private lateinit var player: ExoPlayer
    private var now = 1_000_000L
    private val published = mutableListOf<SleepTimerStatus?>()
    private var pauses = 0
    private var volume = 1f
    private var pauseAtEnd = false
    private lateinit var controller: PlaybackFadeController

    @Before
    fun setUp() {
        player = mock(ExoPlayer::class.java)
        `when`(player.duration).thenReturn(180_000L)
        `when`(player.currentPosition).thenReturn(60_000L)
        `when`(player.isPlaying).thenReturn(true)
        org.mockito.Mockito
            .doAnswer { volume = it.getArgument(0) }
            .`when`(player)
            .volume = org.mockito.ArgumentMatchers.anyFloat()
        org.mockito.Mockito
            .doAnswer { pauseAtEnd = it.getArgument(0) }
            .`when`(player)
            .pauseAtEndOfMediaItems = org.mockito.ArgumentMatchers.anyBoolean()
        controller =
            PlaybackFadeController(
                player = player,
                elapsedRealtimeMs = { now },
                publish = { published += it },
                pause = { pauses++ },
            )
    }

    @Test
    fun timer_countsDownPausesAndRestoresTheVolume() {
        controller.startSleepTimer(SleepTimerSetting.AfterMinutes(1))
        assertTrue(controller.sleepTimerActive)
        assertEquals(60_000L, published.last()?.remainingMs)

        now += 30_000L
        controller.tick()
        assertEquals(1f, volume, 0f)
        assertEquals(30_000L, published.last()?.remainingMs)

        // Inside the last fade: quieter, still playing.
        now += 25_000L
        controller.tick()
        assertTrue("volume $volume", volume < 1f && volume > 0f)
        assertEquals(0, pauses)

        now += 5_000L
        controller.tick()
        assertEquals(1, pauses)
        assertFalse(controller.sleepTimerActive)
        assertNull(published.last())
        assertEquals("full volume again for the next play", 1f, volume, 0f)
    }

    @Test
    fun cancel_removesTheTimerAndLoudnessReturns() {
        controller.startSleepTimer(SleepTimerSetting.AfterMinutes(1))
        now += 55_000L
        controller.tick()
        assertTrue(volume < 1f)

        controller.cancelSleepTimer()

        assertFalse(controller.sleepTimerActive)
        assertEquals(1f, volume, 0f)
        assertNull(published.last())
        assertEquals(0, pauses)
    }

    @Test
    fun endOfTrack_letsExoPlayerPauseAtTheEndAndClearsOnThat() {
        controller.startSleepTimer(SleepTimerSetting.EndOfTrack)
        assertTrue(pauseAtEnd)
        assertTrue(published.last()?.endOfTrack == true)
        assertNull(published.last()?.remainingMs)

        controller.onPausedAtEndOfItem()

        assertFalse(pauseAtEnd)
        assertFalse(controller.sleepTimerActive)
        assertNull(published.last())
        assertEquals("ExoPlayer paused itself, the service must not pause again", 0, pauses)
    }

    @Test
    fun endOfTrack_fadesOutInTheLastSeconds() {
        `when`(player.currentPosition).thenReturn(172_000L)
        controller.startSleepTimer(SleepTimerSetting.EndOfTrack)

        assertTrue("volume $volume", volume < 1f)
    }

    @Test
    fun trackFade_dipsAtTheStartOfATrack() {
        controller.trackFadeMs = 4_000L
        `when`(player.currentPosition).thenReturn(1_000L)

        controller.tick()

        assertTrue("volume $volume", volume in 0.1f..0.9f)

        `when`(player.currentPosition).thenReturn(60_000L)
        controller.tick()
        assertEquals(1f, volume, 0f)
    }

    @Test
    fun needsTicks_onlyWhileSomethingToDo() {
        assertFalse(controller.needsTicks)

        controller.trackFadeMs = 3_000L
        assertTrue(controller.needsTicks)
        `when`(player.isPlaying).thenReturn(false)
        assertFalse(controller.needsTicks)

        controller.startSleepTimer(SleepTimerSetting.AfterMinutes(5))
        assertTrue("a sleep timer counts down while paused too", controller.needsTicks)
    }

    @Test
    fun release_resetsVolumeAndStatus() {
        controller.startSleepTimer(SleepTimerSetting.AfterMinutes(1))
        now += 55_000L
        controller.tick()

        controller.release()

        assertEquals(1f, volume, 0f)
        assertNull(published.last())
        verify(player).volume = 1f
    }
}
