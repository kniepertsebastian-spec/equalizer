package com.soundcloud.equalizer.player.service

import com.soundcloud.equalizer.player.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hardbasseq.eq.auto.AutoMediaId
import com.hardbasseq.eq.playlist.PlaybackFailureAction
import com.hardbasseq.eq.playlist.PlaybackFailureClassifier
import com.hardbasseq.eq.playlist.PlaybackFailureKind
import com.hardbasseq.eq.playlist.PlaybackFailurePolicy
import com.hardbasseq.eq.playlist.QueueNavigation
import com.soundcloud.equalizer.player.PlayerLaunch
import com.soundcloud.equalizer.player.auth.SoundCloudLoginActivity
import com.soundcloud.equalizer.player.playback.AutoCatalogHolder
import com.soundcloud.equalizer.player.playback.AutoSessionState
import com.soundcloud.equalizer.player.playback.NowPlaying
import com.soundcloud.equalizer.player.playback.NowPlayingState
import com.soundcloud.equalizer.player.playback.PlaybackQueueState
import com.soundcloud.equalizer.player.playback.PlayedTracksState
import com.soundcloud.equalizer.player.playback.PlayerDspAudioProcessor
import com.soundcloud.equalizer.player.playback.PlayerDspState
import com.soundcloud.equalizer.player.soundcloud.SoundCloudClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class AudioPlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "sound_cloud_equalizer_player_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.soundcloud.equalizer.player.PLAY"
        const val ACTION_PAUSE = "com.soundcloud.equalizer.player.PAUSE"
        const val ACTION_STOP = "com.soundcloud.equalizer.player.STOP"
        const val ACTION_TOGGLE_PLAYBACK = "com.soundcloud.equalizer.player.TOGGLE_PLAYBACK"
        // Queue control: PlaybackQueueState.queue holds the list, these move within it.
        const val ACTION_PLAY_INDEX = "com.soundcloud.equalizer.player.PLAY_INDEX"
        const val ACTION_NEXT = "com.soundcloud.equalizer.player.NEXT"
        const val ACTION_PREVIOUS = "com.soundcloud.equalizer.player.PREVIOUS"
        const val ACTION_SEEK_TO = "com.soundcloud.equalizer.player.SEEK_TO"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_TRACK_TITLE = "extra_track_title"
        const val EXTRA_ARTIST_NAME = "extra_artist_name"
        const val EXTRA_QUEUE_INDEX = "extra_queue_index"
        const val EXTRA_POSITION_MS = "extra_position_ms"

        // How often playback position is published for the progress bar.
        private const val PROGRESS_INTERVAL_MS = 500L
    }

    private val binder = LocalBinder()
    private var exoPlayer: ExoPlayer? = null

    // What the car, Bluetooth devices, the lock screen and the notification see: the
    // title, artist and cover of the playing track and the transport buttons. Without
    // a media session they show nothing (a car display says "content not found").
    private var mediaSession: MediaSession? = null
    private var notifiedKey: Triple<String, String, Boolean>? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var isAudioSessionActive = false
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentArtworkUrl: String? = null

    // -1 = not playing from PlaybackQueueState.queue (a single track started with playTrack).
    private var queueIndex = -1
    private var isLoadingTrack = false
    private var isPreviewStream = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val soundCloudClient = SoundCloudClient()
    private var loadJob: Job? = null

    // What a failed track means (see PlaybackFailurePolicy): lost connection waits and retries the
    // same track, only a track that cannot be played at all moves the queue on - at most three
    // times in a row.
    private val failurePolicy = PlaybackFailurePolicy()
    private var networkRetry: Runnable? = null
    private var lastStreamUrl: String? = null

    // Where a retried track picks up: a stream that dropped mid-song continues from there.
    private var retryPositionMs = 0L

    private val progressTicker = object : Runnable {
        override fun run() {
            publishNowPlaying()
            mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        PlayerDspState.load(this)
        createNotificationChannel()
        initExoPlayer()
    }

    // Same sink DefaultRenderersFactory would build, plus the player's own audio
    // chain - curve, virtual bass, mono bass, limiter (see PlayerDspAudioProcessor).
    @OptIn(UnstableApi::class)
    private fun buildRenderersFactory() = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(PlayerDspAudioProcessor()))
            .build()
    }

    private fun initExoPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(this, buildRenderersFactory())
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // Keeps the CPU and the Wi-Fi connection awake while music plays: without it
            // the phone dozes when the screen locks and the stream stutters or stops.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        super.onPlaybackStateChanged(playbackState)
                        if (playbackState == Player.STATE_READY && isPlaying) {
                            openAudioSession()
                        }
                        // A finished track moves on through the queue; with no queue
                        // (or at its end) the bar just shows the paused, finished state.
                        if (playbackState == Player.STATE_ENDED) {
                            playNext()
                        }
                        publishNowPlaying()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        super.onIsPlayingChanged(isPlaying)
                        if (isPlaying) {
                            failurePolicy.onPlaybackStarted()
                            openAudioSession()
                        }
                        // Covers every reason isPlaying can flip, not just taps on our
                        // own play/pause controls - audio focus loss, headphones
                        // unplugged, playback reaching the end - so the mini-player
                        // bar HardBass EQ's main screen shows never goes stale.
                        publishNowPlaying()
                    }

                    // A stream that fails mid-queue: with no connection every track would fail, so
                    // skipping on would run through the whole queue. A network failure therefore
                    // pauses and retries this track later; only a track that cannot be played
                    // (expired URL, removed) moves on - see PlaybackFailurePolicy.
                    override fun onPlayerError(error: PlaybackException) {
                        retryPositionMs = currentPosition.coerceAtLeast(0L)
                        val networkCode =
                            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        handlePlaybackFailure(PlaybackFailureClassifier.classify(isOnline(), networkCode, error))
                    }
                })
            }

        currentAudioSessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        exoPlayer?.let { createMediaSession(it) }
    }

    // The queue is kept by this service, not by ExoPlayer (which only ever holds the one
    // track that plays), so the session's player passes next / previous on to it.
    @OptIn(UnstableApi::class)
    private fun createMediaSession(player: ExoPlayer) {
        val queueAwarePlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .addAll(
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    )
                    .build()

            override fun isCommandAvailable(command: Int): Boolean =
                command == Player.COMMAND_SEEK_TO_NEXT ||
                    command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                    super.isCommandAvailable(command)

            override fun hasNextMediaItem(): Boolean = true

            override fun hasPreviousMediaItem(): Boolean = true

            override fun seekToNext() = playNext()

            override fun seekToNextMediaItem() = playNext()

            override fun seekToPrevious() = playPrevious()

            override fun seekToPreviousMediaItem() = playPrevious()

            override fun stop() = stopPlayer()

            // Play and pause from the lock screen, a headset or the car go through the service,
            // so they also restart a track that failed (the idle ExoPlayer ignores a plain play())
            // and end a pending retry (see PlaybackFailurePolicy).
            override fun play() = resumeTrack()

            override fun pause() = pauseTrack()

            // "Play this" from the car (Android Auto) arrives as a media item that only
            // has an id; it stands for a whole folder of the car's browser, which becomes
            // the play queue. Anything else goes to ExoPlayer as usual.
            override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
                if (!startFromCar(mediaItems)) super.setMediaItems(mediaItems, startIndex, startPositionMs)
            }

            override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
                if (!startFromCar(mediaItems)) super.setMediaItems(mediaItems, resetPosition)
            }

            override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
                if (!startFromCar(mediaItems)) super.setMediaItems(mediaItems)
            }

            override fun setMediaItem(mediaItem: MediaItem) {
                if (!startFromCar(listOf(mediaItem))) super.setMediaItem(mediaItem)
            }

            override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
                if (!startFromCar(listOf(mediaItem))) super.setMediaItem(mediaItem, startPositionMs)
            }

            override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
                if (!startFromCar(listOf(mediaItem))) super.setMediaItem(mediaItem, resetPosition)
            }
        }
        mediaSession = MediaSession.Builder(this, queueAwarePlayer)
            .setSessionActivity(openPlayerPendingIntent())
            .setCallback(object : MediaSession.Callback {
                // Items from a car carry only an id (see startFromCar); keep them as they are
                // instead of letting the session reject items without a stream address.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)
            })
            .build()
        // media3 1.9+ no longer offers sessionCompatToken: the platform token is wrapped here for the
        // car browser (MediaBrowserServiceCompat).
        AutoSessionState.sessionToken = mediaSession?.platformToken?.let { MediaSessionCompat.Token.fromToken(it) }
    }

    // True when [items] is a request from the car's browser (ids of AutoMediaId): the
    // folder behind it is loaded and played as the queue, from the chosen track on.
    private fun startFromCar(items: List<MediaItem>): Boolean {
        val first = items.firstOrNull() ?: return false
        val id = first.mediaId.takeIf { AutoMediaId.isTrack(it) }
        // Voice search ("play X") arrives as an item with a search query and no id.
        val query = if (id == null && first.mediaId.isEmpty()) first.requestMetadata.searchQuery else null
        if (id == null && query == null) return false
        val catalog = AutoCatalogHolder.catalog ?: return true
        // A service that was only bound (not started) would end when the car lets go.
        runCatching { startService(Intent(this, AudioPlayerService::class.java)) }
        serviceScope.launch {
            val queue = runCatching { if (id != null) catalog.queueFor(id) else catalog.queueForSearch(query.orEmpty()) }.getOrNull()
            if (queue == null || queue.tracks.isEmpty()) return@launch
            PlaybackQueueState.setQueue(queue.tracks)
            playIndex(queue.startIndex.coerceIn(0, queue.tracks.lastIndex))
        }
        return true
    }

    fun openAudioSession() {
        val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && (!isAudioSessionActive || currentAudioSessionId != sessionId)) {
            currentAudioSessionId = sessionId

            // Must match the action AudioSessionRepository actually listens for
            // (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) - this used to
            // broadcast a custom "OPEN_AUDIO_EFFECT_SESSION" action (missing
            // "_CONTROL_") that nothing was ever listening for, so HardBass EQ never
            // saw this player's session at all.
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

            sendBroadcast(intent)
            isAudioSessionActive = true
        }
    }

    fun closeAudioSession() {
        if (isAudioSessionActive && currentAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)

            sendBroadcast(intent)
            isAudioSessionActive = false
        }
    }

    // A single track outside any queue (kept for ACTION_PLAY and older callers).
    fun playTrack(streamUrl: String, title: String, artist: String) {
        resetFailureState()
        loadJob?.cancel()
        queueIndex = -1
        currentTitle = title
        currentArtist = artist
        currentArtworkUrl = null
        isPreviewStream = false
        startStream(streamUrl)
        startForeground(NOTIFICATION_ID, buildNotification(title, artist, true))
    }

    /**
     * Plays PlaybackQueueState.queue[index]. The stream URL is resolved fresh here,
     * by track id, rather than taken from the list: SoundCloud stream URLs are
     * short-lived, and tracks from a saved playlist never had one.
     */
    fun playIndex(index: Int) {
        resetFailureState()
        loadQueueTrack(index)
    }

    // Also used to skip on after an unplayable track and to retry the current one, which must
    // not clear the failure counters the way a user's own choice does (see playIndex).
    private fun loadQueueTrack(index: Int) {
        val queue = PlaybackQueueState.queue.value
        if (!QueueNavigation.isValid(index, queue.size)) return
        val track = queue[index]

        val startPositionMs = retryPositionMs
        retryPositionMs = 0L
        queueIndex = index
        currentTitle = track.title
        currentArtist = track.artist
        currentArtworkUrl = track.artworkUrl
        isLoadingTrack = true
        isPreviewStream = false
        exoPlayer?.pause()
        startProgressTicker()
        publishNowPlaying()
        startForeground(NOTIFICATION_ID, buildNotification(track.title, track.artist, true))

        loadJob?.cancel()
        loadJob = serviceScope.launch {
            val token = SoundCloudLoginActivity.getSavedToken(this@AudioPlayerService)
            soundCloudClient.setUserAuthToken(token)
            val lookup = runCatching { soundCloudClient.getTrack(track.id) }
            // Another track was chosen while this one resolved: its answer no longer matters
            // (and a cancellation must not be mistaken for a failed lookup below).
            ensureActive()
            val resolved = lookup.getOrNull()
            val url = resolved?.streamUrl ?: track.streamUrl
            isPreviewStream = resolved?.isPreview == true
            if (isPreviewStream) {
                val hint = if (token == null) {
                    getString(R.string.svc_preview_signin)
                } else {
                    getString(R.string.svc_preview_account)
                }
                Toast.makeText(this@AudioPlayerService, hint, Toast.LENGTH_LONG).show()
            }
            if (url.isNullOrEmpty()) {
                // No stream address: no connection (retry later) or a track SoundCloud gives
                // no stream for (skip).
                retryPositionMs = startPositionMs
                handlePlaybackFailure(PlaybackFailureClassifier.classify(isOnline(), cause = lookup.exceptionOrNull()))
            } else {
                PlayedTracksState.mark(track.id)
                startStream(url, startPositionMs)
            }
        }
    }

    fun playNext() {
        // queueIndex -1 = a single track outside any queue: nothing follows it.
        if (queueIndex < 0) return
        val next = QueueNavigation.next(queueIndex, PlaybackQueueState.queue.value.size)
        if (next != null) playIndex(next)
    }

    fun playPrevious() {
        val previous = if (queueIndex < 0) null else QueueNavigation.previous(queueIndex, exoPlayer?.currentPosition ?: 0L)
        if (previous != null) playIndex(previous) else seekTo(0L)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
        publishNowPlaying()
    }

    private fun startStream(streamUrl: String, startPositionMs: Long = 0L) {
        val player = exoPlayer ?: return
        lastStreamUrl = streamUrl
        isLoadingTrack = false
        val metadata = MediaMetadata.Builder()
            .setTitle(currentTitle)
            .setArtist(currentArtist)
            .setArtworkUri(currentArtworkUrl?.let { Uri.parse(it) })
            .build()
        val item = MediaItem.Builder().setUri(streamUrl).setMediaMetadata(metadata).build()
        if (startPositionMs > 0L) player.setMediaItem(item, startPositionMs) else player.setMediaItem(item)
        player.prepare()
        player.play()
        startProgressTicker()
        publishNowPlaying()
        openAudioSession()
    }

    // The current track failed. What follows depends on why (see PlaybackFailurePolicy).
    private fun handlePlaybackFailure(kind: PlaybackFailureKind) {
        isLoadingTrack = false
        when (val action = failurePolicy.onFailure(kind)) {
            is PlaybackFailureAction.RetryLater -> waitAndRetry(action)
            PlaybackFailureAction.SkipToNext -> skipUnplayableTrack()
            PlaybackFailureAction.GiveUp -> giveUp(kind)
        }
    }

    // No connection: pause on this track and try it again after a growing wait, from where it
    // stopped. The queue stays as it is.
    private fun waitAndRetry(action: PlaybackFailureAction.RetryLater) {
        cancelNetworkRetry()
        exoPlayer?.pause()
        if (action.attempt == 1) {
            Toast.makeText(this, getString(R.string.svc_network_waiting), Toast.LENGTH_SHORT).show()
        }
        publishNowPlaying()
        val retry = Runnable {
            networkRetry = null
            restartCurrentTrack()
        }
        networkRetry = retry
        mainHandler.postDelayed(retry, action.delayMs)
    }

    // This track cannot be played (no stream, stream error while online): say so and carry on
    // with the queue instead of hanging on it.
    private fun skipUnplayableTrack() {
        if (currentTitle.isNotEmpty()) {
            Toast.makeText(this, getString(R.string.svc_not_playable, currentTitle), Toast.LENGTH_SHORT).show()
        }
        val next = if (queueIndex < 0) null else QueueNavigation.next(queueIndex, PlaybackQueueState.queue.value.size)
        // The position belongs to the track that failed, not to the one that follows.
        retryPositionMs = 0L
        if (next != null) loadQueueTrack(next) else publishNowPlaying()
    }

    // Too many failures in a row: stay paused on the current track; pressing play loads it again.
    private fun giveUp(kind: PlaybackFailureKind) {
        exoPlayer?.pause()
        val message = if (kind == PlaybackFailureKind.NETWORK) R.string.svc_network_gave_up else R.string.svc_skips_gave_up
        Toast.makeText(this, getString(message), Toast.LENGTH_LONG).show()
        publishNowPlaying()
    }

    // Loads the current track again: a queue track gets a fresh stream address (they expire),
    // a single track its last one.
    private fun restartCurrentTrack() {
        if (queueIndex >= 0) {
            loadQueueTrack(queueIndex)
        } else {
            val url = lastStreamUrl ?: return
            val startPositionMs = retryPositionMs
            retryPositionMs = 0L
            startStream(url, startPositionMs)
        }
    }

    // A choice of the user (a new track, stop, play after a failure) starts with a clean slate.
    private fun resetFailureState() {
        failurePolicy.reset()
        cancelNetworkRetry()
        retryPositionMs = 0L
    }

    private fun cancelNetworkRetry() {
        networkRetry?.let { mainHandler.removeCallbacks(it) }
        networkRetry = null
    }

    private fun isOnline(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startProgressTicker() {
        mainHandler.removeCallbacks(progressTicker)
        mainHandler.postDelayed(progressTicker, PROGRESS_INTERVAL_MS)
    }

    private fun publishNowPlaying() {
        if (currentTitle.isEmpty()) return
        val player = exoPlayer
        val playing = isLoadingTrack || player != null &&
            (player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING))
        val duration = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0 && !isLoadingTrack } ?: 0L
        refreshNotification(playing)
        NowPlayingState.update(
            NowPlaying(
                title = currentTitle,
                artist = currentArtist,
                isPlaying = playing,
                artworkUrl = currentArtworkUrl,
                durationMs = duration,
                positionMs = if (isLoadingTrack) 0L else (player?.currentPosition ?: 0L).coerceAtLeast(0L),
                queueIndex = queueIndex,
                isLoading = isLoadingTrack,
                isPreview = isPreviewStream,
            )
        )
    }

    fun pauseTrack() {
        // A pause during the wait for the connection means: do not start again by yourself.
        cancelNetworkRetry()
        exoPlayer?.pause()
    }

    fun resumeTrack() {
        val player = exoPlayer ?: return
        // After a failure the player is idle: playing again means loading the track again.
        if (player.playbackState == Player.STATE_IDLE && !isLoadingTrack && (queueIndex >= 0 || lastStreamUrl != null)) {
            failurePolicy.reset()
            cancelNetworkRetry()
            restartCurrentTrack()
            return
        }
        player.play()
        openAudioSession()
    }

    fun stopPlayer() {
        resetFailureState()
        lastStreamUrl = null
        loadJob?.cancel()
        mainHandler.removeCallbacks(progressTicker)
        exoPlayer?.stop()
        closeAudioSession()
        currentTitle = ""
        currentArtist = ""
        currentArtworkUrl = null
        queueIndex = -1
        isLoadingTrack = false
        isPreviewStream = false
        notifiedKey = null
        NowPlayingState.update(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    fun togglePlayback() {
        if (isPlaying()) pauseTrack() else resumeTrack()
    }

    fun getAudioSessionId(): Int {
        return exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_STREAM_URL)
                val title = intent.getStringExtra(EXTRA_TRACK_TITLE) ?: getString(R.string.svc_default_title)
                val artist = intent.getStringExtra(EXTRA_ARTIST_NAME) ?: getString(R.string.svc_default_artist)
                if (!url.isNullOrEmpty()) {
                    playTrack(url, title, artist)
                }
            }
            ACTION_PLAY_INDEX -> playIndex(intent.getIntExtra(EXTRA_QUEUE_INDEX, -1))
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_SEEK_TO -> seekTo(intent.getLongExtra(EXTRA_POSITION_MS, 0L))
            ACTION_PAUSE -> pauseTrack()
            ACTION_STOP -> stopPlayer()
            ACTION_TOGGLE_PLAYBACK -> togglePlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        cancelNetworkRetry()
        mainHandler.removeCallbacks(progressTicker)
        serviceScope.cancel()
        closeAudioSession()
        NowPlayingState.update(null)
        AutoSessionState.sessionToken = null
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.svc_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // Keeps the notification (and with it the lock screen and the car) in step with what
    // plays: only re-posted when the track or the play/pause state really changed.
    private fun refreshNotification(playing: Boolean) {
        val key = Triple(currentTitle, currentArtist, playing)
        if (key == notifiedKey) return
        notifiedKey = key
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(currentTitle, currentArtist, playing))
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode,
        Intent(this, AudioPlayerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Tapping the notification, the lock screen card or the car's "open on phone" shows the
    // Compose player in MainActivity (the old View-based PlayerActivity is gone).
    private fun openPlayerPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, PlayerLaunch.openPlayerIntent(this),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @OptIn(UnstableApi::class)
    private fun buildNotification(title: String, artist: String, isPlaying: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPlayerPendingIntent())
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.svc_action_previous), actionIntent(ACTION_PREVIOUS, 1))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) getString(R.string.svc_action_pause) else getString(R.string.svc_action_play),
                actionIntent(ACTION_TOGGLE_PLAYBACK, 2)
            )
            .addAction(android.R.drawable.ic_media_next, getString(R.string.svc_action_next), actionIntent(ACTION_NEXT, 3))
        mediaSession?.let { session ->
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1, 2))
        }
        return builder.build()
    }
}
