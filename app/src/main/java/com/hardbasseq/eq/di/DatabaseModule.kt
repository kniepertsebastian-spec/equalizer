package com.hardbasseq.eq.di

import android.content.Context
import androidx.room.Room
import com.hardbasseq.eq.data.db.AppDatabase
import com.hardbasseq.eq.data.db.CorrectionProfileDao
import com.hardbasseq.eq.data.db.DeviceProfileDao
import com.hardbasseq.eq.data.db.PresetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "hardbasseq.db",
            )
            // Only guards against a future downgrade (newer DB opened by an older
            // app build) - there's no forward migration to fall back from today
            // since version 1 is still the only schema this DB has ever had.
            // Per-row corruption (one bad custom preset) is handled in
            // PresetRepository instead, which is what M2's "ein beschädigtes
            // Nutzerpreset kann die App nicht am Start hindern" actually asks for.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun providePresetDao(db: AppDatabase): PresetDao = db.presetDao()

    @Provides
    fun provideDeviceProfileDao(db: AppDatabase): DeviceProfileDao = db.deviceProfileDao()

    @Provides
    fun provideCorrectionProfileDao(db: AppDatabase): CorrectionProfileDao = db.correctionProfileDao()
}
