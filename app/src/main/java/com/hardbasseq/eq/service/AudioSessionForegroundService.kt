package com.hardbasseq.eq.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hardbasseq.eq.MainActivity
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEngine
import com.hardbasseq.eq.audio.AudioRouteRepository
import com.hardbasseq.eq.audio.AudioSessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val NOTIFICATION_CHANNEL_ID = "audio_session_listener"
private const val NOTIFICATION_ID = 1

// Keeps AudioSessionRepository/AudioRouteRepository listening and reacts to session
// changes by attaching/detaching AudioEngine for as long as the app process is alive,
// independent of whether MainActivity/MainViewModel is open. Without this, a player's
// one-shot OPEN_AUDIO_EFFECT_CONTROL_SESSION broadcast is missed for good if HardBass
// EQ's UI wasn't already open at that exact moment (roadmap.md Session 11/12).
@AndroidEntryPoint
class AudioSessionForegroundService : LifecycleService() {
    @Inject
    lateinit var sessionRepository: AudioSessionRepository

    @Inject
    lateinit var routeRepository: AudioRouteRepository

    @Inject
    lateinit var audioEngine: AudioEngine

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        sessionRepository.startListening()
        routeRepository.startMonitoring()

        lifecycleScope.launch {
            sessionRepository.activeSession.collect { session ->
                if (session != null) {
                    audioEngine.attach(session)
                } else {
                    audioEngine.detach()
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        sessionRepository.stopListening()
        routeRepository.stopMonitoring()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.session_service_notification_title),
                    NotificationManager.IMPORTANCE_LOW,
                )
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }

        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.session_service_notification_title))
            .setContentText(getString(R.string.session_service_notification_text))
            .setSmallIcon(R.drawable.ic_notification_eq)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
