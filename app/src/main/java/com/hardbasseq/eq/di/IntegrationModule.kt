package com.hardbasseq.eq.di

import com.hardbasseq.eq.integration.AndroidPlayerLauncher
import com.hardbasseq.eq.integration.PlayerLauncher
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
    abstract fun bindPlayerLauncher(impl: AndroidPlayerLauncher): PlayerLauncher
}
