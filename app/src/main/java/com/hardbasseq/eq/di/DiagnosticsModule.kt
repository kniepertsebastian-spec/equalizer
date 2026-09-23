package com.hardbasseq.eq.di

import com.hardbasseq.eq.diagnostics.DiagnosticsRecorder
import com.hardbasseq.eq.diagnostics.InMemoryDiagnosticsRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindDiagnosticsRecorder(impl: InMemoryDiagnosticsRecorder): DiagnosticsRecorder
}
