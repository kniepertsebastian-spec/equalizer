package com.auracurve.app.di

import com.auracurve.app.eq.EqControllerScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EqControllerModule {
    // Process lifetime, and the main thread as in viewModelScope: the controller's state was written
    // from the main thread only before E-008 stage 2, and stays so. Callers on other threads (the
    // remote server) switch to the main thread before calling it.
    @Provides
    @Singleton
    @EqControllerScope
    fun provideEqControllerScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
