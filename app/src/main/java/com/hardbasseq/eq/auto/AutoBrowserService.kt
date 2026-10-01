package com.hardbasseq.eq.auto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import com.soundcloud.equalizer.player.playback.AutoCatalogHolder
import com.soundcloud.equalizer.player.playback.AutoNode
import com.soundcloud.equalizer.player.playback.AutoSessionState
import com.soundcloud.equalizer.player.service.AudioPlayerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

// The media browser Android Auto connects to: it shows the car what can be played (folders
// and tracks from AppAutoCatalog). Playing and the transport controls go through the
// player's media session, whose token this service hands over once the player service is
// bound. Nothing here runs unless a car or another media controller connects.
@AndroidEntryPoint
class AutoBrowserService : MediaBrowserServiceCompat() {
    @Inject
    lateinit var catalog: AppAutoCatalog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playerBound = false

    private val playerConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val token = AutoSessionState.sessionToken
                // The token can only be set once per browser service.
                if (token != null && sessionToken == null) setSessionToken(token)
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

    override fun onCreate() {
        super.onCreate()
        AutoCatalogHolder.catalog = catalog
        // Creates the player service (and with it the media session) if it is not running.
        playerBound = bindService(Intent(this, AudioPlayerService::class.java), playerConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot? {
        if (!isTrustedClient(clientPackageName, clientUid)) return null
        val extras =
            Bundle().apply {
                // Lists, not grids, for folders and tracks alike.
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
            }
        return BrowserRoot(AutoMediaId.ROOT, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        result.detach()
        scope.launch {
            val items =
                runCatching { catalog.children(parentId).map { toMediaItem(it) } }
                    .getOrDefault(emptyList())
            result.sendResult(items.toMutableList())
        }
    }

    override fun onDestroy() {
        scope.cancel()
        if (playerBound) unbindService(playerConnection)
        playerBound = false
        super.onDestroy()
    }

    // The list shows the user's playlists: only the system itself, Android Auto and the
    // assistant may read it, not any app that asks.
    private fun isTrustedClient(
        packageName: String,
        uid: Int,
    ): Boolean = uid == Process.myUid() || uid < Process.FIRST_APPLICATION_UID || packageName in TRUSTED_PACKAGES

    private fun toMediaItem(node: AutoNode): MediaBrowserCompat.MediaItem {
        val description =
            MediaDescriptionCompat
                .Builder()
                .setMediaId(node.id)
                .setTitle(node.title)
                .setSubtitle(node.subtitle)
                .setIconUri(node.artworkUrl?.let { Uri.parse(it) })
                .build()
        val flag = if (node.playable) MediaBrowserCompat.MediaItem.FLAG_PLAYABLE else MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        return MediaBrowserCompat.MediaItem(description, flag)
    }

    private companion object {
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val CONTENT_STYLE_LIST = 1

        val TRUSTED_PACKAGES =
            setOf(
                "com.google.android.projection.gearhead",
                "com.google.android.googlequicksearchbox",
                "com.google.android.carassistant",
                "com.google.android.gms",
                "com.android.systemui",
                "com.android.bluetooth",
                "android",
            )
    }
}
