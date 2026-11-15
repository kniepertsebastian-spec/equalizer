package com.auracurve.app.di

import com.auracurve.app.discovery.AndroidDiscoverySource
import com.auracurve.app.discovery.DiscoverySource
import com.auracurve.app.integration.OnlineSources
import com.auracurve.app.integration.SoundCloudOnlineSources
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Flavor "full": SoundCloud, Spotify and shared links (AuraCurve roadmap 02, 1.3).
@Module
@InstallIn(SingletonComponent::class)
abstract class DistributionModule {
    @Binds
    @Singleton
    abstract fun bindOnlineSources(impl: SoundCloudOnlineSources): OnlineSources

    @Binds
    @Singleton
    abstract fun bindDiscoverySource(impl: AndroidDiscoverySource): DiscoverySource
}
