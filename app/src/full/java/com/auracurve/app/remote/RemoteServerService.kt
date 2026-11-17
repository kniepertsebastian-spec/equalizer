package com.auracurve.app.remote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.auracurve.app.BuildConfig
import com.auracurve.app.MainActivity
import com.auracurve.app.R
import com.auracurve.app.diagnostics.DiagnosticsRecorder
import com.auracurve.app.eq.EqController
import com.auracurve.app.service.AudioSessionForegroundService
import com.auracurve.app.text.TextProvider
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "RemoteServerService"
private const val CHANNEL_ID = "remote_server"
private const val NOTIFICATION_ID = 4080
private const val ACTION_STOP = "com.auracurve.app.remote.STOP"
private const val ADDRESS_CHECK_MS = 15_000L
private val EQ_EVENT_PERIOD = 250.milliseconds

/**
 * The local remote-control server (Pixel-Car module 04a): Ktor CIO on port 8080, answering only
 * inside a private network, with a lasting notification "Remote control on · <address>:8080" and
 * a Stop action.
 *
 * Foreground type specialUse: the server neither plays media nor syncs data, and specialUse is
 * the type Android 15 still allows to start from BOOT_COMPLETED (module 04e), unlike
 * mediaPlayback and dataSync.
 *
 * While it runs it keeps the app process alive, and through RemoteServerControl.running the
 * session service too (no idle stop): the equalizer keeps following whatever plays.
 */
@AndroidEntryPoint
class RemoteServerService : LifecycleService() {
    @Inject
    lateinit var control: AndroidRemoteServerControl

    @Inject
    lateinit var settings: RemoteServerSettings

    @Inject
    lateinit var statusSource: SystemStatusSource

    @Inject
    lateinit var diagnostics: DiagnosticsRecorder

    // 04b: the EQ endpoints steer the process-wide controller (E-008 stage 2), never a view model.
    @Inject
    lateinit var eqController: EqController

    @Inject
    lateinit var texts: TextProvider

    private val eqApi by lazy { EqRemoteApi(eqController, texts, Dispatchers.Main.immediate) }

    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    private var token: String? = null

    private val events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onCreate() {
        super.onCreate()
        if (!control.isAvailable || !promoteToForeground(LocalAddress.privateIpv4())) {
            control.onServerStopped(getString(R.string.remote_server_error_not_allowed))
            stopSelf()
            return
        }
        // The equalizer must follow sessions while the server runs: make sure the session service
        // is there (it may have closed itself for idleness before). It stays then, see idleShutdown.
        runCatching { ContextCompat.startForegroundService(this, Intent(this, AudioSessionForegroundService::class.java)) }
            .onFailure { Log.w(TAG, "Session service not started", it) }

        lifecycleScope.launch {
            token = settings.ensureToken()
            launch { settings.token.collect { token = it } }
            val started = withContext(Dispatchers.IO) { startServer() }
            if (!started) {
                control.onServerStopped(getString(R.string.remote_server_error_port, LocalAddress.PORT))
                stopSelf()
                return@launch
            }
            val address = LocalAddress.privateIpv4()
            control.onServerStarted(address)
            updateNotification(address)
            diagnostics.record("Remote control server started on port ${LocalAddress.PORT}")
            launch { watchAddress(address) }
            launch {
                // Every EQ change, also from the app or a tile; number inputs come in quick bursts.
                eqApi.changes().drop(1).throttleLatest(EQ_EVENT_PERIOD).collect {
                    events.tryEmit(RemoteEvent("eq.changed", it))
                }
            }
            launch {
                statusSource.changes().drop(1).throttleLatest(5.seconds).collect {
                    events.tryEmit(RemoteEvent("system.changed", it.toJson()))
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
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val running = server
        server = null
        // Stopping waits for open calls; not on the main thread.
        if (running != null) Thread({ runCatching { running.stop(STOP_GRACE_MS, STOP_TIMEOUT_MS) } }, "remote-server-stop").start()
        control.onServerStopped()
        diagnostics.record("Remote control server stopped")
        super.onDestroy()
    }

    private fun startServer(): Boolean =
        try {
            val config =
                RemoteApiConfig(
                    appVersion = BuildConfig.VERSION_NAME,
                    token = { token },
                    systemStatus = { statusSource.current() },
                    events = events,
                    webRoot = { getExternalFilesDir(WEB_DIR) },
                    eq = eqApi,
                )
            server = embeddedServer(CIO, port = LocalAddress.PORT, host = "0.0.0.0") { remoteApi(config) }.start(wait = false)
            true
        } catch (e: Exception) {
            // Typically the port is taken (another server, a second copy of the app).
            Log.w(TAG, "Remote control server did not start: ${e.javaClass.simpleName}")
            false
        }

    private suspend fun watchAddress(initial: String?) {
        var last = initial
        while (true) {
            delay(ADDRESS_CHECK_MS)
            val now = LocalAddress.privateIpv4()
            if (now != last) {
                last = now
                control.onAddressChanged(now)
                updateNotification(now)
            }
        }
    }

    private fun promoteToForeground(address: String?): Boolean =
        try {
            val type =
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(address), type)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Foreground start rejected: ${e.javaClass.simpleName}")
            false
        }

    // Without the notification permission the foreground notification is hidden anyway.
    private fun updateNotification(address: String?) {
        val allowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(address))
    }

    private fun buildNotification(address: String?): Notification {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.remote_server_channel), NotificationManager.IMPORTANCE_LOW)
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, RemoteServerService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val text =
            if (address != null) {
                getString(R.string.remote_server_notification_text, "$address:${LocalAddress.PORT}")
            } else {
                getString(R.string.remote_server_notification_no_network)
            }
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.remote_server_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_eq)
            .setContentIntent(open)
            .addAction(0, getString(R.string.remote_server_action_stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        /** Under getExternalFilesDir: Android/data/com.auracurve.app.full/files/web/ (U-3, adb push). */
        const val WEB_DIR = "web"
        private const val STOP_GRACE_MS = 300L
        private const val STOP_TIMEOUT_MS = 1_500L
    }
}
