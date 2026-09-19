package com.hardbasseq.eq

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hardbasseq.eq.service.AudioSessionForegroundService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HardBassEqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Starts as early as the app process exists (not only once MainActivity/
        // MainViewModel is created), so the session-open broadcast a player sends
        // is much less likely to be missed. See AudioSessionForegroundService.
        ContextCompat.startForegroundService(this, Intent(this, AudioSessionForegroundService::class.java))
    }
}
