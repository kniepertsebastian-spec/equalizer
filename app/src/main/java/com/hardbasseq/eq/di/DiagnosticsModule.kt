package com.hardbasseq.eq.di

import com.hardbasseq.eq.diagnostics.AndroidSystemDiagnosticsSource
import com.hardbasseq.eq.diagnostics.DiagnosticsRecorder
import com.hardbasseq.eq.diagnostics.InMemoryDiagnosticsRecorder
import com.hardbasseq.eq.diagnostics.SystemDiagnosticsSource
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

    @Binds
    @Singleton
    abstract fun bindSystemDiagnosticsSource(impl: AndroidSystemDiagnosticsSource): SystemDiagnosticsSource
}
