package com.auracurve.app.di

import android.content.Context
import com.auracurve.app.integration.AndroidLocalTrackReader
import com.auracurve.app.integration.AndroidPlaybackTimers
import com.auracurve.app.integration.AndroidPlayerBridge
import com.auracurve.app.integration.AndroidPlayerController
import com.auracurve.app.integration.LocalTrackReader
import com.auracurve.app.integration.PlaybackTimers
import com.auracurve.app.integration.PlayerBridge
import com.auracurve.app.integration.PlayerController
import com.auracurve.app.player.playback.PlayerDspStore
import com.auracurve.app.player.playback.SharedPlayerDspStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IntegrationModule {
    @Binds
    @Singleton
    abstract fun bindPlayerBridge(impl: AndroidPlayerBridge): PlayerBridge

    @Binds
    @Singleton
    abstract fun bindPlayerController(impl: AndroidPlayerController): PlayerController

    @Binds
    @Singleton
    abstract fun bindLocalTrackReader(impl: AndroidLocalTrackReader): LocalTrackReader

    @Binds
    @Singleton
    abstract fun bindPlaybackTimers(impl: AndroidPlaybackTimers): PlaybackTimers

    companion object {
        // The built-in player's DSP settings, curve and virtual bass (roadmap 11 P11-4). Still the
        // process-wide objects underneath: AudioPlayerService reads them and is not Hilt-managed.
        @Provides
        @Singleton
        fun providePlayerDspStore(
            @ApplicationContext context: Context,
        ): PlayerDspStore = SharedPlayerDspStore(context)
    }
}
