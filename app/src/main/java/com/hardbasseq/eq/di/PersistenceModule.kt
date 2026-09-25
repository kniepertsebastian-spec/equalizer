package com.hardbasseq.eq.di

import com.hardbasseq.eq.preset.PresetRepository
import com.hardbasseq.eq.preset.RoomPresetRepository
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
}
