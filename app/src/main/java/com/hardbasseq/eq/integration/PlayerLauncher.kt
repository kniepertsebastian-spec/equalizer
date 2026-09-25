package com.hardbasseq.eq.integration

/**
 * Binds HardBass EQ's master on/off bar to the companion SC Equalizer Player app
 * (see roadmap.md: SoundCloud never sends ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION,
 * so there is no session for us to attach to until something we control is playing).
 * Switching the master bar on with no session attached starts the player; switching
 * it off tells the player to stop, and this app goes back to waiting for any session.
 */
interface PlayerLauncher {
    fun isPlayerInstalled(): Boolean

    fun launchPlayer()

    fun stopPlayer()
}
