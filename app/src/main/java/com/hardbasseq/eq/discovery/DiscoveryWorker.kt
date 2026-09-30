package com.hardbasseq.eq.discovery

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.TimeZone

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DiscoveryWorkerEntryPoint {
    fun discoveryUpdater(): DiscoveryUpdater
}

// Runs every few hours and rebuilds the playlist when a new week has started - so
// Monday's list is ready without opening the app. Android decides the exact moment
// (battery, network); the work only runs with a connection. A failed attempt (offline,
// SoundCloud down) is retried with backoff.
class DiscoveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updater = EntryPointAccessors.fromApplication(applicationContext, DiscoveryWorkerEntryPoint::class.java).discoveryUpdater()
        val now = System.currentTimeMillis()
        val offset = TimeZone.getDefault().getOffset(now).toLong()
        return when (updater.refresh(now, offset, onlyIfDue = true)) {
            is RefreshResult.Failed -> Result.retry()
            else -> Result.success()
        }
    }
}
