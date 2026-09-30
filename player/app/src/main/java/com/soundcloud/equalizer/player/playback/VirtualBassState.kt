package com.soundcloud.equalizer.player.playback

// How much synthesized bass harmonics the player's own audio chain adds (0 = off,
// 1 = full), set by HardBass EQ's main screen. Same-process shared singleton for
// the same reason as NowPlayingState: AudioPlayerService is not Hilt-managed and
// there is no service connection to pass the value over. @Volatile because the
// writer is the UI thread and the reader is ExoPlayer's playback thread - a
// single float needs no further locking.
object VirtualBassState {
    @Volatile
    var mix: Float = 0f
}
