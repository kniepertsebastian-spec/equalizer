package com.hardbasseq.eq.di

import android.os.Build
import com.hardbasseq.eq.audio.AndroidAudioEffectRepository
import com.hardbasseq.eq.audio.AndroidAudioEngine
import com.hardbasseq.eq.audio.AndroidAudioPlaybackMonitor
import com.hardbasseq.eq.audio.AndroidAudioRouteRepository
import com.hardbasseq.eq.audio.AndroidAudioSessionRepository
import com.hardbasseq.eq.audio.AndroidSystemVolumeRepository
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.AudioEngine
import com.hardbasseq.eq.audio.AudioPlaybackMonitor
import com.hardbasseq.eq.audio.AudioRouteRepository
import com.hardbasseq.eq.audio.AudioSessionRepository
import com.hardbasseq.eq.audio.DumpsysSessionScanner
import com.hardbasseq.eq.audio.NoOpAudioPlaybackMonitor
import com.hardbasseq.eq.audio.SessionScanner
import com.hardbasseq.eq.audio.SystemVolumeRepository
import com.hardbasseq.eq.auto.AndroidCarConnectionRepository
import com.hardbasseq.eq.auto.CarConnectionRepository
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
