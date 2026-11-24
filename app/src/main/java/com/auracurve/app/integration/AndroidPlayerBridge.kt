package com.auracurve.app.integration

import android.content.Context
import com.auracurve.app.dsp.ParametricFilter
import com.auracurve.app.player.PlayerLaunch
import com.auracurve.app.player.playback.NowPlayingState
import com.auracurve.app.player.playback.PlayerCurve
import com.auracurve.app.player.playback.PlayerDspStore
import com.auracurve.app.player.playback.SharedPlayerDspStore
import com.auracurve.app.player.service.PlayerCommandSender
import com.auracurve.app.player.service.PlayerSessionCommands
import com.auracurve.app.preset.TargetPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AndroidPlayerBridge
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        // Curve and virtual bass go to the player through its store (ADR 0004, step 1 of the
        // roadmap 11 addendum), not straight into the global objects behind it.
        private val dspStore: PlayerDspStore = SharedPlayerDspStore(context),
    ) : PlayerBridge {
        // stopPlayer and togglePlayback are called from the background too (quick tile, widget,
        // remote server): they reach the player over its media session, never with
        // startService(), which Android 12+ refuses in the background (see PlayerSessionCommands).
        // Replaced in tests.
        internal var playerCommands: PlayerCommandSender = PlayerSessionCommands.sender(context)

        // SoundCloud is the only source and the player screen is the same for every source, so
        // [source] needs no extra: the Compose player in MainActivity is brought forward.
        override fun launchPlayer(source: PlayerSource) {
            context.startActivity(PlayerLaunch.openPlayerIntent(context))
        }

        // No track loaded: no player runs (or it holds nothing), there is nothing to stop - the
        // service is not started only to be stopped again.
        override fun stopPlayer() {
            if (playerCommands.hasLoadedTrack) playerCommands.send(PlayerSessionCommands.STOP)
        }

        // Nothing loaded: nothing to play or pause (the service ignored that all along).
        override fun togglePlayback() {
            if (playerCommands.hasLoadedTrack) playerCommands.send(PlayerSessionCommands.TOGGLE_PLAYBACK)
        }

        // NowPlaying.isPlaying is already true while the chosen track is still loading.
        override val ownPlayerPlaying: Flow<Boolean> =
            NowPlayingState.current.map { it?.isPlaying == true }.distinctUntilChanged()

        override fun setVirtualBassMix(mix: Float) = dspStore.setVirtualBassMix(mix)

        override fun setPlayerCurve(
            curve: List<TargetPoint>,
            subsonicCutoffHz: Float,
            headroomSafetyRatio: Float,
            parametricFilters: List<ParametricFilter>,
        ) {
            dspStore.setCurve(PlayerCurve(curve, subsonicCutoffHz, headroomSafetyRatio.coerceIn(0f, 1f), parametricFilters))
        }
    }
