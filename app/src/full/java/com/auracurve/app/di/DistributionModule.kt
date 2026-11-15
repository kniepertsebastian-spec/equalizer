package com.auracurve.app.di

import com.auracurve.app.BuildConfig
import com.auracurve.app.discovery.AndroidDiscoverySource
import com.auracurve.app.discovery.DiscoverySource
import com.auracurve.app.integration.OnlineSources
import com.auracurve.app.integration.SoundCloudOnlineSources
import com.auracurve.app.integration.SpotifyPlaylistApi
import com.auracurve.app.player.catalog.AudiusCatalog
import com.auracurve.app.player.catalog.AudiusClient
import com.auracurve.app.player.catalog.RadioBrowserClient
import com.auracurve.app.player.catalog.RadioCatalog
import com.auracurve.app.playlist.DataStoreSpotifyImportRepository
import com.auracurve.app.playlist.SpotifyImportRepository
import com.auracurve.app.ui.player.LinkImport
import com.auracurve.app.ui.player.SoundCloudLinkImport
import dagger.Binds
import dagger.Module
import dagger.Provides
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

    @Binds
    @Singleton
    abstract fun bindSpotifyPlaylistApi(impl: SoundCloudOnlineSources): SpotifyPlaylistApi

    @Binds
    @Singleton
    abstract fun bindSpotifyImportRepository(impl: DataStoreSpotifyImportRepository): SpotifyImportRepository

    // Not a singleton: each PlayerViewModel gets its own, which reports into that view model.
    @Binds
    abstract fun bindLinkImport(impl: SoundCloudLinkImport): LinkImport

    // Radio and Audius (AuraCurve roadmap 06): both ask apps to name themselves.
    companion object {
        private val userAgent = "AuraCurve/${BuildConfig.VERSION_NAME} (Android)"

        @Provides
        @Singleton
        fun provideRadioCatalog(): RadioCatalog = RadioBrowserClient(userAgent)

        @Provides
        @Singleton
        fun provideAudiusCatalog(): AudiusCatalog = AudiusClient(appName = "AuraCurve", userAgent = userAgent)
    }
}
