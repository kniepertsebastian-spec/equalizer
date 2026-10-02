package com.hardbasseq.eq.di

import com.hardbasseq.eq.correction.CorrectionProfileRepository
import com.hardbasseq.eq.correction.RoomCorrectionProfileRepository
import com.hardbasseq.eq.discovery.DataStoreDiscoveryRepository
import com.hardbasseq.eq.discovery.DiscoveryRepository
import com.hardbasseq.eq.playlist.DataStorePlaylistRepository
import com.hardbasseq.eq.playlist.DataStoreSpotifyImportRepository
import com.hardbasseq.eq.playlist.PlaylistRepository
import com.hardbasseq.eq.playlist.SpotifyImportRepository
import com.hardbasseq.eq.preset.PresetRepository
import com.hardbasseq.eq.preset.RoomPresetRepository
import com.hardbasseq.eq.profile.DeviceAliasStore
import com.hardbasseq.eq.profile.DeviceProfileRepository
import com.hardbasseq.eq.profile.RoomDeviceProfileRepository
import com.hardbasseq.eq.profile.SharedPreferencesDeviceAliasStore
import com.hardbasseq.eq.settings.AppSettingsRepository
import com.hardbasseq.eq.settings.DataStoreAppSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    @Binds
    @Singleton
    abstract fun bindPresetRepository(impl: RoomPresetRepository): PresetRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: DataStoreAppSettingsRepository): AppSettingsRepository

    @Binds
    @Singleton
    abstract fun bindCorrectionProfileRepository(impl: RoomCorrectionProfileRepository): CorrectionProfileRepository

    @Binds
    @Singleton
    abstract fun bindDeviceProfileRepository(impl: RoomDeviceProfileRepository): DeviceProfileRepository

    @Binds
    @Singleton
    abstract fun bindDeviceAliasStore(impl: SharedPreferencesDeviceAliasStore): DeviceAliasStore

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: DataStorePlaylistRepository): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindDiscoveryRepository(impl: DataStoreDiscoveryRepository): DiscoveryRepository

    @Binds
    @Singleton
    abstract fun bindSpotifyImportRepository(impl: DataStoreSpotifyImportRepository): SpotifyImportRepository
}
