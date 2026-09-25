package com.hardbasseq.eq.di

import com.hardbasseq.eq.integration.AndroidPlayerBridge
import com.hardbasseq.eq.integration.PlayerBridge
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
}
