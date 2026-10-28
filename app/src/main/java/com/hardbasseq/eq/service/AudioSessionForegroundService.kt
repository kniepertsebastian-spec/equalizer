package com.hardbasseq.eq.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hardbasseq.eq.MainActivity
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEngine
import com.hardbasseq.eq.audio.AudioRouteRepository
import com.hardbasseq.eq.audio.AudioSessionRepository
import com.hardbasseq.eq.audio.SystemVolumeRepository
import com.hardbasseq.eq.diagnostics.DiagnosticsRecorder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AudioSessionFgService"
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
    lateinit var volumeRepository: SystemVolumeRepository

    @Inject
    lateinit var audioEngine: AudioEngine

    @Inject
    lateinit var diagnosticsRecorder: DiagnosticsRecorder

    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        if (!promoteToForegroundSafely()) {
            stopSelf()
            return
        }

        sessionRepository.startListening()
        routeRepository.startMonitoring()

        lifecycleScope.launch {
            sessionRepository.activeSession.collect { session ->
                if (session != null) {
                    // Only monitor volume changes while an active session exists to avoid
                    // unnecessary background wakeups and curve recalculations while idle.
                    volumeRepository.startMonitoring()
                    diagnosticsRecorder.record("Session attached: id=${session.sessionId} package=${session.packageName}")
                    audioEngine.attach(session)
                } else {
                    volumeRepository.stopMonitoring()
                    diagnosticsRecorder.record("Session detached (entering low-overhead listening state)")
                    audioEngine.detach()
                }
            }
        }

        lifecycleScope.launch {
            routeRepository.activeRoute.collect { route ->
                diagnosticsRecorder.record("Route changed: ${route.type.name} (${route.name})")
            }
        }

        lifecycleScope.launch {
            audioEngine.state.collect { state ->
                diagnosticsRecorder.record("Engine state: $state")
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        if (!foregroundStarted && !promoteToForegroundSafely()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // START_NOT_STICKY prevents Android 14/15 from blindly recreating the mediaPlayback
        // service in the background when the app is not visible, which triggers
        // ForegroundServiceStartNotAllowedException and Samsung's "deep standby" crash warning.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        diagnosticsRecorder.record("Service destroyed (releasing listeners and audio effects)")
        sessionRepository.stopListening()
        routeRepository.stopMonitoring()
        volumeRepository.stopMonitoring()
        audioEngine.markDetached()
        super.onDestroy()
    }

    private fun promoteToForegroundSafely(): Boolean =
        try {
            val fgsType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), fgsType)
            foregroundStarted = true
            true
        } catch (e: Exception) {
            val isBgRestriction =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException
            Log.w(TAG, "Unable to start foreground service (bgRestriction=$isBgRestriction)", e)
            diagnosticsRecorder.record(
                "Foreground start rejected (${e.javaClass.simpleName}): ${e.localizedMessage ?: "no message"}",
            )
            false
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

        return NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.session_service_notification_title))
            .setContentText(getString(R.string.session_service_notification_text))
            .setSmallIcon(R.drawable.ic_notification_eq)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
