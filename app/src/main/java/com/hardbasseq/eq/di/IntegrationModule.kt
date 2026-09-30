package com.hardbasseq.eq.di

import com.hardbasseq.eq.integration.AndroidPlayerBridge
import com.hardbasseq.eq.integration.AndroidPlayerController
import com.hardbasseq.eq.integration.PlayerBridge
import com.hardbasseq.eq.integration.PlayerController
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
}
