package com.auracurve.app.remote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
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
import com.auracurve.app.discovery.DiscoveryRepository
import com.auracurve.app.discovery.DiscoveryUpdater
import com.auracurve.app.eq.EqController
import com.auracurve.app.integration.PlayerController
import com.auracurve.app.player.spotify.SpotifyApiClient
import com.auracurve.app.remote.feed.FeedRemoteApi
import com.auracurve.app.remote.library.AndroidSpotifyStarter
import com.auracurve.app.remote.library.ApiClientLibraryReader
import com.auracurve.app.remote.library.FileSpotifyLibraryStore
import com.auracurve.app.remote.library.SpotifyLibraryRemoteApi
import com.auracurve.app.remote.playback.AndroidVolumeBackend
import com.auracurve.app.remote.playback.PlaybackRemoteApi
import com.auracurve.app.remote.playback.PlayerPlaybackBackend
import com.auracurve.app.remote.playback.SpotifyPlaybackBackend
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TimeZone
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TAG = "RemoteServerService"
private const val CHANNEL_ID = "remote_server"
private const val NOTIFICATION_ID = 4080
private const val ACTION_STOP = "com.auracurve.app.remote.STOP"
private const val ADDRESS_CHECK_MS = 15_000L
private const val LIBRARY_FILE = "spotify_library.json"
private val LIBRARY_CHECK_PERIOD = 15.minutes
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

    private val eqApi by lazy { EqRemoteApi(eqController, texts, Dispatchers.Main.immediate, scope = lifecycleScope) }

    // 04c: the built-in player through PlayerController, Spotify through its MediaSession.
    @Inject
    lateinit var playerController: PlayerController

    private val spotifyBackend by lazy { SpotifyPlaybackBackend(applicationContext) }

    private val playbackApi by lazy {
        PlaybackRemoteApi(
            player = PlayerPlaybackBackend(playerController),
            spotify = spotifyBackend,
            volume = AndroidVolumeBackend(applicationContext),
            mainDispatcher = Dispatchers.Main.immediate,
        )
    }

    // 04d: the same list and the same pull as the player screen.
    @Inject
    lateinit var discoveryRepository: DiscoveryRepository

    @Inject
    lateinit var discoveryUpdater: DiscoveryUpdater

    private val feedApi by lazy {
        FeedRemoteApi(
            repository = discoveryRepository,
            refresh = {
                val now = System.currentTimeMillis()
                discoveryUpdater.refresh(now, TimeZone.getDefault().getOffset(now).toLong(), onlyIfDue = false)
            },
            online = ::hasInternet,
            playQueue = { playerController.playQueue(it, 0) },
            scope = lifecycleScope,
        )
    }

    // Way B: a copy of the Spotify playlists, synced while online, played through the Spotify app.
    private val libraryApi by lazy {
        SpotifyLibraryRemoteApi(
            reader = ApiClientLibraryReader(SpotifyApiClient.get(applicationContext)),
            store = FileSpotifyLibraryStore(File(filesDir, LIBRARY_FILE)),
            online = ::hasInternet,
            starter = AndroidSpotifyStarter(applicationContext, spotifyBackend),
            mainDispatcher = Dispatchers.Main.immediate,
            scope = lifecycleScope,
        )
    }

    private fun hasInternet(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun systemStatus(): SystemStatus = statusSource.current().copy(activeSource = playbackApi.activeSource.value.name)

    private var server: EmbeddedServer<*, *>? = null

    // 04e (R-19): `<name>.local` for as long as the server runs.
    private var mdns: MdnsResponder? = null
    private var multicastLock: WifiManager.MulticastLock? = null

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
            startMdns(settings.hostName.first())
            launch { settings.hostName.collect { mdns?.name = it } }
            launch {
                // Every EQ change, also from the app or a tile; number inputs come in quick bursts.
                eqApi.changes().drop(1).throttleLatest(EQ_EVENT_PERIOD).collect {
                    events.tryEmit(RemoteEvent("eq.changed", it))
                }
            }
            launch {
                feedApi.updates.collect { events.tryEmit(RemoteEvent("feed.updated", it)) }
            }
            launch {
                libraryApi.updates.collect { events.tryEmit(RemoteEvent("library.updated", it)) }
            }
            launch {
                // At home in the Wi-Fi the copy renews itself; in the car (no internet) nothing happens.
                while (true) {
                    runCatching { libraryApi.syncIfDue() }.onFailure { Log.w(TAG, "Library sync check failed", it) }
                    delay(LIBRARY_CHECK_PERIOD)
                }
            }
            launch {
                playbackApi.changes().drop(1).collect { events.tryEmit(RemoteEvent("playback.changed", it)) }
            }
            launch {
                playbackApi.volumeChanges().drop(1).collect { events.tryEmit(RemoteEvent("volume.changed", it)) }
            }
            launch {
                playbackApi.activeSource.drop(1).collect { events.tryEmit(RemoteEvent("source.changed", systemStatus().toJson())) }
            }
            launch {
                statusSource.changes().drop(1).throttleLatest(5.seconds).collect {
                    events.tryEmit(RemoteEvent("system.changed", it.copy(activeSource = playbackApi.activeSource.value.name).toJson()))
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
        stopMdns()
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
                    systemStatus = { withContext(Dispatchers.Main.immediate) { systemStatus() } },
                    events = events,
                    webRoot = { getExternalFilesDir(WEB_DIR) },
                    eq = eqApi,
                    playback = playbackApi,
                    feed = feedApi,
                    library = libraryApi,
                    // 04f: the last web remote gone ends a comparison it started.
                    onWebSocketClients = { count -> if (count == 0) lifecycleScope.launch { eqApi.onAllClientsGone() } },
                )
            server = embeddedServer(CIO, port = LocalAddress.PORT, host = "0.0.0.0") { remoteApi(config) }.start(wait = false)
            true
        } catch (e: Exception) {
            // Typically the port is taken (another server, a second copy of the app).
            Log.w(TAG, "Remote control server did not start: ${e.javaClass.simpleName}")
            false
        }

    private fun startMdns(name: String) {
        // As a Wi-Fi client Android filters multicast without this lock; in the hotspot it is harmless.
        multicastLock =
            runCatching {
                getSystemService(WifiManager::class.java)?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.onFailure { Log.w(TAG, "Multicast lock not taken: ${it.javaClass.simpleName}") }
                .getOrNull()
        mdns = MdnsResponder(name).also { it.start() }
    }

    private fun stopMdns() {
        mdns?.stop()
        mdns = null
        multicastLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        multicastLock = null
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
        private const val MULTICAST_LOCK_TAG = "auracurve-remote-mdns"
    }
}
