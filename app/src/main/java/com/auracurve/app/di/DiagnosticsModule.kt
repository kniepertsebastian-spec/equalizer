package com.auracurve.app.di

import com.auracurve.app.diagnostics.AndroidSystemDiagnosticsSource
import com.auracurve.app.diagnostics.DiagnosticsRecorder
import com.auracurve.app.diagnostics.InMemoryDiagnosticsRecorder
import com.auracurve.app.diagnostics.SystemDiagnosticsSource
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
