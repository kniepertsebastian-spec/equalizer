package com.hardbasseq.eq.integration

/**
 * Binds HardBass EQ's master on/off bar to the SC Equalizer Player screen (see
 * roadmap.md: SoundCloud never sends ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, so
 * there's no session for us to attach to until something we control is playing).
 * Switching the master bar on with no session attached and picking a source starts
 * the player screen for that source; switching it off stops playback.
 *
 * Both apps merged into one (player/ is now a library module :app depends on), so
 * this is a same-process call - no more cross-app broadcast/launch-intent contract.
 */
interface PlayerBridge {
    fun launchPlayer(source: PlayerSource)

    fun stopPlayer()

    /** Play/pause the currently loaded track without bringing the player screen forward. */
    fun togglePlayback()
}
