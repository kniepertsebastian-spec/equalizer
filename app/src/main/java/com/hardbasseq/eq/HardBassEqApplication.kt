package com.hardbasseq.eq

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hardbasseq.eq.discovery.DiscoveryWorker
import com.hardbasseq.eq.service.AudioSessionForegroundService
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class HardBassEqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Starts as early as the app process exists (not only once MainActivity/
        // MainViewModel is created), so the session-open broadcast a player sends
        // is much less likely to be missed. See AudioSessionForegroundService.
        ContextCompat.startForegroundService(this, Intent(this, AudioSessionForegroundService::class.java))
        scheduleDiscoveryRefresh()
    }

    // "Interesting new uploads" rebuilds itself when a new week starts: this check runs
    // every few hours (only with a connection) and does nothing until Monday has
    // passed. KEEP leaves an already scheduled job alone across app starts.
    private fun scheduleDiscoveryRefresh() {
        val request =
            PeriodicWorkRequestBuilder<DiscoveryWorker>(DISCOVERY_CHECK_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork(DISCOVERY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val DISCOVERY_WORK_NAME = "discovery_weekly_refresh"
        const val DISCOVERY_CHECK_HOURS = 6L
    }
}
