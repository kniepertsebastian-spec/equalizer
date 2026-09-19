package com.hardbasseq.eq.di

import com.hardbasseq.eq.audio.AndroidAudioEffectRepository
import com.hardbasseq.eq.audio.AudioEffectRepository
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
}
