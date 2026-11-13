package com.hardbasseq.eq.ui.player.nowplaying

import com.soundcloud.equalizer.player.playback.NowPlaying
import com.soundcloud.equalizer.player.playback.PlayerNotice
import com.soundcloud.equalizer.player.playback.RepeatSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The states of docs/PLAYER_REDESIGN.md, section 3.6, as the mini player and "Now playing" see them.
class NowPlayingUiTest {
    private fun playing(
        isPlaying: Boolean = true,
        isLoading: Boolean = false,
        durationMs: Long = 200_000L,
        positionMs: Long = 50_000L,
        notice: PlayerNotice? = null,
        isPreview: Boolean = false,
    ) = NowPlaying(
        title = "Kick",
        artist = "DJ",
        isPlaying = isPlaying,
        durationMs = durationMs,
        positionMs = positionMs,
        isLoading = isLoading,
        isPreview = isPreview,
        notice = notice,
        shuffle = true,
        repeat = RepeatSetting.ONE,
    )

    @Test
    fun playing_hasProgressAndSeek() {
        val ui = NowPlayingUi.from(playing())

        assertTrue(ui.isPlaying)
        assertTrue(ui.seekEnabled)
        assertTrue(ui.controlsEnabled)
        assertEquals(0.25f, ui.progress, 0.001f)
        assertTrue(ui.shuffle)
        assertEquals(RepeatSetting.ONE, ui.repeat)
        assertFalse(ui.showRetry)
    }

    @Test
    fun paused_keepsPositionAndControls() {
        val ui = NowPlayingUi.from(playing(isPlaying = false))

        assertFalse(ui.isPlaying)
        assertTrue(ui.seekEnabled)
        assertEquals(50_000L, ui.positionMs)
    }

    @Test
    fun loading_locksTheSeekBarButNotTheTransport() {
        val ui = NowPlayingUi.from(playing(isLoading = true))

        assertTrue(ui.isLoading)
        assertFalse(ui.seekEnabled)
        assertTrue(ui.controlsEnabled)
    }

    @Test
    fun unknownLength_hasNoProgressAndNoSeek() {
        val ui = NowPlayingUi.from(playing(durationMs = 0L))

        assertEquals(0f, ui.progress, 0f)
        assertFalse(ui.seekEnabled)
    }

    @Test
    fun preview_isMarked() {
        assertTrue(NowPlayingUi.from(playing(isPreview = true)).isPreview)
    }

    @Test
    fun waitingForNetwork_isShownWithoutRetry() {
        val ui = NowPlayingUi.from(playing(isPlaying = false, notice = PlayerNotice.WaitingForNetwork))

        assertEquals(PlayerNotice.WaitingForNetwork, ui.notice)
        assertFalse(ui.showRetry)
    }

    @Test
    fun stoppedPlayback_offersRetry() {
        assertTrue(NowPlayingUi.from(playing(isPlaying = false, notice = PlayerNotice.NetworkGaveUp)).showRetry)
        assertTrue(NowPlayingUi.from(playing(isPlaying = false, notice = PlayerNotice.TooManyUnplayable)).showRetry)
        // A skipped track is information; playback goes on.
        assertFalse(NowPlayingUi.from(playing(notice = PlayerNotice.NotPlayable("x"))).showRetry)
    }
}
