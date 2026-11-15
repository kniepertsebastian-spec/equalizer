package com.auracurve.app.di

import android.os.Build
import com.auracurve.app.audio.AndroidAudioEffectRepository
import com.auracurve.app.audio.AndroidAudioEngine
import com.auracurve.app.audio.AndroidAudioPlaybackMonitor
import com.auracurve.app.audio.AndroidAudioRouteRepository
import com.auracurve.app.audio.AndroidAudioSessionRepository
import com.auracurve.app.audio.AndroidSystemVolumeRepository
import com.auracurve.app.audio.AudioEffectRepository
import com.auracurve.app.audio.AudioEngine
import com.auracurve.app.audio.AudioPlaybackMonitor
import com.auracurve.app.audio.AudioRouteRepository
import com.auracurve.app.audio.AudioSessionRepository
import com.auracurve.app.audio.DumpsysSessionScanner
import com.auracurve.app.audio.NoOpAudioPlaybackMonitor
import com.auracurve.app.audio.SessionScanner
import com.auracurve.app.audio.SystemVolumeRepository
import com.auracurve.app.auto.AndroidCarConnectionRepository
import com.auracurve.app.auto.CarConnectionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    @Singleton
    abstract fun bindAudioEffectRepository(impl: AndroidAudioEffectRepository): AudioEffectRepository

    @Binds
    @Singleton
    abstract fun bindAudioEngine(impl: AndroidAudioEngine): AudioEngine

    @Binds
    @Singleton
    abstract fun bindAudioSessionRepository(impl: AndroidAudioSessionRepository): AudioSessionRepository

    @Binds
    @Singleton
    abstract fun bindAudioRouteRepository(impl: AndroidAudioRouteRepository): AudioRouteRepository

    @Binds
    @Singleton
    abstract fun bindSystemVolumeRepository(impl: AndroidSystemVolumeRepository): SystemVolumeRepository

    @Binds
    @Singleton
    abstract fun bindCarConnectionRepository(impl: AndroidCarConnectionRepository): CarConnectionRepository

    companion object {
        @Provides
        @Singleton
        fun provideAudioPlaybackMonitor(
            androidMonitor: AndroidAudioPlaybackMonitor,
            noOpMonitor: NoOpAudioPlaybackMonitor,
        ): AudioPlaybackMonitor =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidMonitor
            } else {
                noOpMonitor
            }
    }

    @Binds
    @Singleton
    abstract fun bindSessionScanner(impl: DumpsysSessionScanner): SessionScanner
}
