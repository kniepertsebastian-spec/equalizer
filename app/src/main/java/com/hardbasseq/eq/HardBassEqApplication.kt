package com.hardbasseq.eq

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.hardbasseq.eq.auto.AppAutoCatalog
import com.hardbasseq.eq.discovery.DiscoveryWorker
import com.hardbasseq.eq.service.AppVisibility
import com.hardbasseq.eq.service.AudioSessionForegroundService
import com.soundcloud.equalizer.player.PlayerText
import com.soundcloud.equalizer.player.playback.AutoCatalogHolder
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class HardBassEqApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var autoCatalog: AppAutoCatalog

    // Tells the session service whether a screen of this app is open (it never closes itself then).
    @Inject
    lateinit var appVisibility: AppVisibility

    override fun onCreate() {
        super.onCreate()
        PlayerText.init(this)
        // What the car (Android Auto) may browse and play; also read by the player service.
        AutoCatalogHolder.catalog = autoCatalog
        registerActivityLifecycleCallbacks(appVisibility.callbacks)
        // Starts as early as the app process exists (not only once MainActivity/
        // MainViewModel is created), so the session-open broadcast a player sends
        // is much less likely to be missed. See AudioSessionForegroundService.
        startSessionService()
        scheduleDiscoveryRefresh()
    }

    // Covers in the player (lists and the large one) come from SoundCloud's servers: kept in
    // memory while scrolling and on disk across app starts, so they are not downloaded again.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, COVER_MEMORY_SHARE).build() }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve(COVER_CACHE_DIR).toOkioPath())
                    .maxSizeBytes(COVER_DISK_CACHE_BYTES)
                    .build()
            }.crossfade(true)
            .build()

    // A process that was created in the background (the discovery worker below, Android Auto binding
    // the media browser) may not start a foreground service on Android 12+: that throws an
    // IllegalStateException (ForegroundServiceStartNotAllowedException). Unguarded in onCreate it
    // would crash the process at start. MainActivity calls this again when the app comes to the
    // foreground, so the service still starts on the next visit.
    fun startSessionService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, AudioSessionForegroundService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Session service not started: the app is in the background", e)
        }
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
        const val TAG = "HardBassEqApplication"
        const val DISCOVERY_WORK_NAME = "discovery_weekly_refresh"
        const val DISCOVERY_CHECK_HOURS = 6L
        const val COVER_CACHE_DIR = "covers"
        const val COVER_DISK_CACHE_BYTES = 64L * 1024 * 1024
        const val COVER_MEMORY_SHARE = 0.15
    }
}
