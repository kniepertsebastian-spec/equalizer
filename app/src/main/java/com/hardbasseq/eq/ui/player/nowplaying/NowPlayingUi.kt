package com.hardbasseq.eq.ui.player.nowplaying

import com.hardbasseq.eq.playlist.PlayerFormat
import com.soundcloud.equalizer.player.playback.NowPlaying
import com.soundcloud.equalizer.player.playback.PlayerNotice
import com.soundcloud.equalizer.player.playback.RepeatSetting

// What the mini player and "Now playing" show for a NowPlaying value: one mapping for both, so
// the two never disagree. Kept free of Compose and resources so every state from
// docs/PLAYER_REDESIGN.md (section 3.6) can be unit-tested.
data class NowPlayingUi(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val isLoading: Boolean,
    val isPreview: Boolean,
    val shuffle: Boolean,
    val repeat: RepeatSetting,
    val notice: PlayerNotice?,
    // Transport (play, next, previous, shuffle, repeat) can act.
    val controlsEnabled: Boolean,
    // The seek bar can act: the stream is ready and has a length.
    val seekEnabled: Boolean,
    // A notice the user has to answer: playback stopped and waits for "Try again".
    val showRetry: Boolean,
    // 0..1 for the progress line; 0 while the length is unknown.
    val progress: Float,
    val positionMs: Long,
    val durationMs: Long,
) {
    companion object {
        fun from(playing: NowPlaying): NowPlayingUi {
            val notice = playing.notice
            return NowPlayingUi(
                title = playing.title,
                artist = playing.artist,
                artworkUrl = playing.artworkUrl,
                isPlaying = playing.isPlaying,
                isLoading = playing.isLoading,
                isPreview = playing.isPreview,
                shuffle = playing.shuffle,
                repeat = playing.repeat,
                notice = notice,
                controlsEnabled = true,
                seekEnabled = !playing.isLoading && playing.durationMs > 0,
                showRetry = !playing.isPlaying && (notice == PlayerNotice.NetworkGaveUp || notice == PlayerNotice.TooManyUnplayable),
                progress = PlayerFormat.progress(playing.positionMs, playing.durationMs),
                positionMs = playing.positionMs,
                durationMs = playing.durationMs,
            )
        }
    }
}
