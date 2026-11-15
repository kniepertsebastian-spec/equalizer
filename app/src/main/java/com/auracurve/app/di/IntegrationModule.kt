package com.auracurve.app.di

import com.auracurve.app.integration.AndroidLocalTrackReader
import com.auracurve.app.integration.AndroidPlaybackTimers
import com.auracurve.app.integration.AndroidPlayerBridge
import com.auracurve.app.integration.AndroidPlayerController
import com.auracurve.app.integration.LocalTrackReader
import com.auracurve.app.integration.PlaybackTimers
import com.auracurve.app.integration.PlayerBridge
import com.auracurve.app.integration.PlayerController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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
}
