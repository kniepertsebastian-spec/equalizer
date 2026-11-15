package com.auracurve.app.di

import com.auracurve.app.discovery.DiscoverySource
import com.auracurve.app.discovery.NoDiscoverySource
import com.auracurve.app.integration.NoOnlineSources
import com.auracurve.app.integration.OnlineSources
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Flavor "store": no online service built in (AuraCurve roadmap 02, 1.3).
@Module
@InstallIn(SingletonComponent::class)
abstract class DistributionModule {
    @Binds
    @Singleton
    abstract fun bindOnlineSources(impl: NoOnlineSources): OnlineSources

    @Binds
    @Singleton
    abstract fun bindDiscoverySource(impl: NoDiscoverySource): DiscoverySource
}
