package com.hardbasseq.eq.di

import com.hardbasseq.eq.audio.AndroidAudioEffectRepository
import com.hardbasseq.eq.audio.AndroidAudioEngine
import com.hardbasseq.eq.audio.AndroidAudioRouteRepository
import com.hardbasseq.eq.audio.AndroidAudioSessionRepository
import com.hardbasseq.eq.audio.AudioEffectRepository
import com.hardbasseq.eq.audio.AudioEngine
import com.hardbasseq.eq.audio.AudioRouteRepository
import com.hardbasseq.eq.audio.AudioSessionRepository
import dagger.Binds
import dagger.Module
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
}
