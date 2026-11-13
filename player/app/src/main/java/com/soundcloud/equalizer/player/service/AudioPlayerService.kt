package com.soundcloud.equalizer.player.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.hardbasseq.eq.auto.AutoMediaId
import com.hardbasseq.eq.playlist.PlaybackFailureAction
import com.hardbasseq.eq.playlist.PlaybackFailureClassifier
import com.hardbasseq.eq.playlist.PlaybackFailureKind
import com.hardbasseq.eq.playlist.PlaybackFailurePolicy
import com.hardbasseq.eq.playlist.QueueNavigation
import com.hardbasseq.eq.playlist.QueueOrder
import com.soundcloud.equalizer.player.PlayerLaunch
import com.soundcloud.equalizer.player.R
import com.soundcloud.equalizer.player.model.TrackItem
import com.soundcloud.equalizer.player.playback.AutoCatalogHolder
import com.soundcloud.equalizer.player.playback.AutoNode
import com.soundcloud.equalizer.player.playback.NowPlaying
import com.soundcloud.equalizer.player.playback.NowPlayingState
import com.soundcloud.equalizer.player.playback.PlaybackQueueState
import com.soundcloud.equalizer.player.playback.PlayedTracksState
import com.soundcloud.equalizer.player.playback.PlayerDspAudioProcessor
import com.soundcloud.equalizer.player.playback.PlayerDspState
import com.soundcloud.equalizer.player.playback.PlayerNotice
import com.soundcloud.equalizer.player.playback.QueueRequest
import com.soundcloud.equalizer.player.playback.QueueRequests
import com.soundcloud.equalizer.player.playback.RepeatSetting
import com.soundcloud.equalizer.player.playback.ResolvedStream
import com.soundcloud.equalizer.player.playback.ResolvingTrackMediaSourceFactory
import com.soundcloud.equalizer.player.playback.SoundCloudStreamResolver
import com.soundcloud.equalizer.player.playback.TrackMediaItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The built-in player. The whole play queue lives in ExoPlayer as media items; each stream
 * address is looked up only when ExoPlayer loads that entry (ResolvingTrackMediaSourceFactory),
 * so the next track is preloaded while the current one plays and follows it without a gap.
 *
 * One media library session serves everything outside the app: the notification (Media3's
 * own, kept in step with the player), the lock screen, Bluetooth buttons, Wear, and Android
 * Auto, which also browses the app's playlists through it (AutoCatalogHolder).
 *
 * The app's screens talk to it with intents (ACTION_*; tracks for a new queue wait in
 * QueueRequests) and read NowPlayingState / PlaybackQueueState, all in the same process.
 */
@OptIn(UnstableApi::class)
class AudioPlayerService : MediaLibraryService() {
    companion object {
        const val CHANNEL_ID = "sound_cloud_equalizer_player_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE = "com.soundcloud.equalizer.player.PAUSE"
        const val ACTION_STOP = "com.soundcloud.equalizer.player.STOP"
        const val ACTION_TOGGLE_PLAYBACK = "com.soundcloud.equalizer.player.TOGGLE_PLAYBACK"

        // Takes the oldest request out of QueueRequests (new queue, play next, add to queue).
        const val ACTION_QUEUE_REQUEST = "com.soundcloud.equalizer.player.QUEUE_REQUEST"
        const val ACTION_PLAY_INDEX = "com.soundcloud.equalizer.player.PLAY_INDEX"
        const val ACTION_NEXT = "com.soundcloud.equalizer.player.NEXT"
        const val ACTION_PREVIOUS = "com.soundcloud.equalizer.player.PREVIOUS"
        const val ACTION_SEEK_TO = "com.soundcloud.equalizer.player.SEEK_TO"
        const val ACTION_SET_SHUFFLE = "com.soundcloud.equalizer.player.SET_SHUFFLE"
        const val ACTION_SET_REPEAT = "com.soundcloud.equalizer.player.SET_REPEAT"
        const val ACTION_MOVE_QUEUE_ITEM = "com.soundcloud.equalizer.player.MOVE_QUEUE_ITEM"
        const val ACTION_REMOVE_QUEUE_ITEM = "com.soundcloud.equalizer.player.REMOVE_QUEUE_ITEM"
        const val EXTRA_QUEUE_INDEX = "extra_queue_index"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_SHUFFLE = "extra_shuffle"

        // RepeatSetting.name
        const val EXTRA_REPEAT = "extra_repeat"
        const val EXTRA_FROM_INDEX = "extra_from_index"
        const val EXTRA_TO_INDEX = "extra_to_index"

        // How often the playback position is published for the progress bar - only while
        // something plays.
        private const val PROGRESS_INTERVAL_MS = 500L

        // The car's browser: lists, not grids, for folders and tracks alike.
        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_LIST = 1

        // The car's list shows the user's playlists: only the system itself, Android Auto and
        // the assistant may browse it, not any app that asks.
        private val TRUSTED_BROWSERS =
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

    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private var sourceFactory: ResolvingTrackMediaSourceFactory? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Browsing and "play this" for the car load playlists and likes (network, database).
    private val libraryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The tracks as the screens or the car handed them over, by id: richer than what a media
    // item carries (PlaybackQueueState publishes these).
    private val knownTracks = HashMap<Long, TrackItem>()

    // Tracks whose current stream is only a preview (see ResolvedStream.isPreview).
    private val previewTrackIds = HashSet<Long>()

    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var isAudioSessionActive = false

    // What a failed track means (see PlaybackFailurePolicy): a lost connection waits and tries
    // the same track again, only a track that cannot be played at all moves the queue on - at
    // most three times in a row.
    private val failurePolicy = PlaybackFailurePolicy()
    private var networkRetry: Runnable? = null
    private var notice: PlayerNotice? = null

    // A stream address expires; one that fails with an HTTP error after the track already
    // played is looked up once more before the track counts as unplayable.
    private var currentTrackHasPlayed = false
    private var streamRefreshedForCurrentTrack = false

    private val progressTicker =
        object : Runnable {
            override fun run() {
                publishNowPlaying()
                mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        PlayerDspState.load(this)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider
                .Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.svc_channel_name)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_player) },
        )

        val exoPlayer = buildPlayer()
        player = exoPlayer
        val librarySession =
            MediaLibrarySession
                .Builder(this, SessionPlayer(exoPlayer), LibraryCallback())
                .setSessionActivity(openPlayerPendingIntent())
                .build()
        session = librarySession
        // The app's own screens send intents instead of connecting a controller; without this the
        // notification would only appear once the system or a car connected.
        addSession(librarySession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    private fun buildPlayer(): ExoPlayer {
        val factory =
            ResolvingTrackMediaSourceFactory(
                delegate = DefaultMediaSourceFactory(this),
                resolver = SoundCloudStreamResolver(this),
                onResolved = ::onStreamResolved,
            )
        sourceFactory = factory
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        return ExoPlayer
            .Builder(this, buildRenderersFactory())
            .setMediaSourceFactory(factory)
            // ExoPlayer's default, set on purpose: queue entries are only prepared (their stream
            // looked up) when ExoPlayer loads them, not the whole queue at once.
            .setUseLazyPreparation(true)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // Keeps the CPU and the Wi-Fi connection awake while music plays: without it the
            // phone dozes when the screen locks and the stream stutters or stops.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // "Previous" restarts the track after this long, like before.
            .setMaxSeekToPreviousPositionMs(QueueNavigation.RESTART_THRESHOLD_MS)
            .build()
            .apply {
                addListener(playerListener)
                currentAudioSessionId = audioSessionId
            }
    }

    // Same sink DefaultRenderersFactory would build, plus the player's own audio chain - curve,
    // virtual bass, mono bass, limiter (see PlayerDspAudioProcessor).
    private fun buildRenderersFactory() =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink
                    .Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(PlayerDspAudioProcessor()))
                    .build()
        }

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val exoPlayer = player ?: return
                if (isPlaying) {
                    failurePolicy.onPlaybackStarted()
                    notice = null
                    currentTrackHasPlayed = true
                    currentTrackId(exoPlayer)?.let(PlayedTracksState::mark)
                    openAudioSession()
                    startProgressTicker()
                } else {
                    // Paused, buffering, ended: one last position, then the ticker rests.
                    stopProgressTicker()
                }
                publishNowPlaying()
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                currentTrackHasPlayed = false
                streamRefreshedForCurrentTrack = false
                publishNowPlaying()
            }

            override fun onTimelineChanged(
                timeline: Timeline,
                reason: Int,
            ) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) publishQueue()
                publishNowPlaying()
            }

            override fun onPlaybackStateChanged(playbackState: Int) = publishNowPlaying()

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) = publishNowPlaying()

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = publishNowPlaying()

            override fun onRepeatModeChanged(repeatMode: Int) = publishNowPlaying()

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // The EQ follows the player's session: hand it the new one.
                closeAudioSession()
                currentAudioSessionId = audioSessionId
                if (player?.isPlaying == true) openAudioSession()
            }

            // Without a connection every track would fail, so skipping on would run through the
            // whole queue. A network failure therefore pauses and tries this track again later;
            // only a track that cannot be played (no stream, removed) moves on - see
            // PlaybackFailurePolicy. A failed lookup of the preloaded next track only shows up
            // here once that track is due.
            override fun onPlayerError(error: PlaybackException) {
                val exoPlayer = player ?: return
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
                    currentTrackHasPlayed &&
                    !streamRefreshedForCurrentTrack
                ) {
                    // Most likely the address expired (a long pause): preparing again looks up a
                    // fresh one and goes on from the same position.
                    streamRefreshedForCurrentTrack = true
                    exoPlayer.prepare()
                    return
                }
                val networkCode =
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                handlePlaybackFailure(exoPlayer, PlaybackFailureClassifier.classify(isOnline(), networkCode, error))
            }
        }

    // The current track failed. What follows depends on why (see PlaybackFailurePolicy).
    private fun handlePlaybackFailure(
        exoPlayer: ExoPlayer,
        kind: PlaybackFailureKind,
    ) {
        when (val action = failurePolicy.onFailure(kind)) {
            is PlaybackFailureAction.RetryLater -> waitAndRetry(exoPlayer, action)
            PlaybackFailureAction.SkipToNext -> skipUnplayableTrack(exoPlayer)
            PlaybackFailureAction.GiveUp -> giveUp(exoPlayer, kind)
        }
        publishNowPlaying()
    }

    // No connection: pause on this track and try it again after a growing wait, from where it
    // stopped (ExoPlayer keeps the position). The queue stays as it is.
    private fun waitAndRetry(
        exoPlayer: ExoPlayer,
        action: PlaybackFailureAction.RetryLater,
    ) {
        cancelNetworkRetry()
        exoPlayer.pause()
        notice = PlayerNotice.WaitingForNetwork
        val retry =
            Runnable {
                networkRetry = null
                exoPlayer.prepare()
                exoPlayer.play()
            }
        networkRetry = retry
        mainHandler.postDelayed(retry, action.delayMs)
    }

    // This track cannot be played (no stream, stream error while online): say so and carry on
    // with the queue instead of hanging on it.
    private fun skipUnplayableTrack(exoPlayer: ExoPlayer) {
        notice = PlayerNotice.NotPlayable(exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty())
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.prepare()
        } else {
            exoPlayer.pause()
        }
    }

    // Too many failures in a row: stay paused on the current track; pressing play loads it again.
    private fun giveUp(
        exoPlayer: ExoPlayer,
        kind: PlaybackFailureKind,
    ) {
        exoPlayer.pause()
        notice = if (kind == PlaybackFailureKind.NETWORK) PlayerNotice.NetworkGaveUp else PlayerNotice.TooManyUnplayable
    }

    // A choice of the user (a new queue, play, next, stop) starts with a clean slate.
    private fun resetFailureState() {
        failurePolicy.reset()
        cancelNetworkRetry()
        notice = null
    }

    private fun cancelNetworkRetry() {
        networkRetry?.let(mainHandler::removeCallbacks)
        networkRetry = null
    }

    private fun isOnline(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun onStreamResolved(
        trackId: Long,
        stream: ResolvedStream,
    ) {
        if (stream.isPreview) previewTrackIds.add(trackId) else previewTrackIds.remove(trackId)
        publishNowPlaying()
    }

    // --- Commands from the app's screens -------------------------------------------------

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val exoPlayer = player
        if (intent != null && exoPlayer != null) handleCommand(exoPlayer, intent)
        return result
    }

    private fun handleCommand(
        exoPlayer: ExoPlayer,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_QUEUE_REQUEST -> QueueRequests.take()?.let { applyQueueRequest(exoPlayer, it) }
            ACTION_PLAY_INDEX -> playIndex(exoPlayer, intent.getIntExtra(EXTRA_QUEUE_INDEX, -1))
            ACTION_NEXT -> userSkip(exoPlayer) { exoPlayer.seekToNext() }
            ACTION_PREVIOUS -> userSkip(exoPlayer) { exoPlayer.seekToPrevious() }
            ACTION_SEEK_TO -> exoPlayer.seekTo(intent.getLongExtra(EXTRA_POSITION_MS, 0L).coerceAtLeast(0L))
            ACTION_PAUSE -> pausePlayer(exoPlayer)
            ACTION_STOP -> stopPlayer(exoPlayer)
            ACTION_TOGGLE_PLAYBACK -> if (isPlayingOrLoading(exoPlayer)) pausePlayer(exoPlayer) else resumePlayer(exoPlayer)
            ACTION_SET_SHUFFLE -> exoPlayer.shuffleModeEnabled = intent.getBooleanExtra(EXTRA_SHUFFLE, false)
            ACTION_SET_REPEAT ->
                exoPlayer.repeatMode =
                    when (intent.getStringExtra(EXTRA_REPEAT)) {
                        RepeatSetting.ALL.name -> Player.REPEAT_MODE_ALL
                        RepeatSetting.ONE.name -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
            ACTION_MOVE_QUEUE_ITEM -> {
                val from = intent.getIntExtra(EXTRA_FROM_INDEX, -1)
                val to = intent.getIntExtra(EXTRA_TO_INDEX, -1)
                val size = exoPlayer.mediaItemCount
                if (from in 0 until size && to in 0 until size && from != to) exoPlayer.moveMediaItem(from, to)
            }
            ACTION_REMOVE_QUEUE_ITEM -> {
                val index = intent.getIntExtra(EXTRA_QUEUE_INDEX, -1)
                if (index in 0 until exoPlayer.mediaItemCount) exoPlayer.removeMediaItem(index)
            }
        }
        publishNowPlaying()
    }

    private fun applyQueueRequest(
        exoPlayer: ExoPlayer,
        request: QueueRequest,
    ) {
        when (request) {
            is QueueRequest.Replace -> playTracks(exoPlayer, request.tracks, request.startIndex)
            is QueueRequest.PlayNext ->
                if (exoPlayer.mediaItemCount == 0) {
                    playTracks(exoPlayer, request.tracks, 0)
                } else {
                    insertTracks(exoPlayer, exoPlayer.currentMediaItemIndex + 1, request.tracks)
                }
            is QueueRequest.Append ->
                if (exoPlayer.mediaItemCount == 0) {
                    playTracks(exoPlayer, request.tracks, 0)
                } else {
                    insertTracks(exoPlayer, exoPlayer.mediaItemCount, request.tracks)
                }
        }
    }

    // A new queue, started at [startIndex].
    private fun playTracks(
        exoPlayer: ExoPlayer,
        tracks: List<TrackItem>,
        startIndex: Int,
    ) {
        if (tracks.isEmpty()) return
        resetFailureState()
        rememberTracks(tracks)
        exoPlayer.setMediaItems(tracks.map(TrackMediaItems::toMediaItem), startIndex.coerceIn(0, tracks.lastIndex), 0L)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Adds [tracks] at [index] of the queue. Right behind the current track they also come next
    // while shuffling (ExoPlayer would scatter them through its shuffled order).
    private fun insertTracks(
        exoPlayer: ExoPlayer,
        index: Int,
        tracks: List<TrackItem>,
    ) {
        if (tracks.isEmpty()) return
        rememberTracks(tracks)
        exoPlayer.addMediaItems(index, tracks.map(TrackMediaItems::toMediaItem))
        if (exoPlayer.shuffleModeEnabled && index == exoPlayer.currentMediaItemIndex + 1) {
            val order = QueueOrder.playNext(shuffledOrder(exoPlayer.currentTimeline), exoPlayer.currentMediaItemIndex, index, tracks.size)
            exoPlayer.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
        }
    }

    // Queue positions in the order the player steps through them while shuffling.
    private fun shuffledOrder(timeline: Timeline): List<Int> {
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order.add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        return order
    }

    private fun playIndex(
        exoPlayer: ExoPlayer,
        index: Int,
    ) {
        if (index !in 0 until exoPlayer.mediaItemCount) return
        resetFailureState()
        exoPlayer.seekTo(index, 0L)
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        exoPlayer.play()
    }

    // Next / previous from the screen: like before, the track that comes up plays.
    private fun userSkip(
        exoPlayer: ExoPlayer,
        skip: () -> Unit,
    ) {
        resetFailureState()
        skip()
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun pausePlayer(exoPlayer: ExoPlayer) {
        // A pause during the wait for the connection means: do not start again by yourself.
        cancelNetworkRetry()
        exoPlayer.pause()
    }

    // After a failure the player is idle: playing again means preparing (and looking up) again.
    // Nothing queued (the master bar's play/pause while the player is empty): nothing to do.
    private fun resumePlayer(exoPlayer: ExoPlayer) {
        if (exoPlayer.mediaItemCount == 0) return
        resetFailureState()
        Util.handlePlayButtonAction(exoPlayer)
    }

    private fun stopPlayer(exoPlayer: ExoPlayer) {
        resetFailureState()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        closeAudioSession()
        knownTracks.clear()
        previewTrackIds.clear()
    }

    private fun rememberTracks(tracks: List<TrackItem>) {
        tracks.forEach { knownTracks[it.id] = it }
    }

    private fun isPlayingOrLoading(exoPlayer: ExoPlayer): Boolean =
        exoPlayer.isPlaying || (exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_BUFFERING)

    private fun currentTrackId(exoPlayer: ExoPlayer): Long? = exoPlayer.currentMediaItem?.let(TrackMediaItems::trackId)

    // --- What the screens see ------------------------------------------------------------

    private fun publishQueue() {
        val exoPlayer = player ?: return
        val tracks =
            (0 until exoPlayer.mediaItemCount).mapNotNull { index ->
                val item = exoPlayer.getMediaItemAt(index)
                TrackMediaItems.trackId(item)?.let { knownTracks[it] } ?: TrackMediaItems.toTrackItem(item)
            }
        // Only what is still queued is worth keeping.
        val queuedIds = tracks.mapTo(HashSet()) { it.id }
        knownTracks.keys.retainAll(queuedIds)
        previewTrackIds.retainAll(queuedIds)
        PlaybackQueueState.publish(tracks)
    }

    private fun publishNowPlaying() {
        val exoPlayer = player ?: return
        val item = exoPlayer.currentMediaItem
        if (item == null) {
            NowPlayingState.update(null)
            return
        }
        val trackId = TrackMediaItems.trackId(item)
        val known = trackId?.let { knownTracks[it] }
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        // Chosen but not ready yet: the stream is being looked up or the first data loads.
        val loading = exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_BUFFERING && duration == null
        NowPlayingState.update(
            NowPlaying(
                title = known?.title ?: item.mediaMetadata.title?.toString().orEmpty(),
                artist = known?.artist ?: item.mediaMetadata.artist?.toString().orEmpty(),
                isPlaying = isPlayingOrLoading(exoPlayer),
                artworkUrl = known?.artworkUrl ?: TrackMediaItems.toTrackItem(item)?.artworkUrl,
                durationMs = duration ?: 0L,
                positionMs = if (loading) 0L else exoPlayer.currentPosition.coerceAtLeast(0L),
                queueIndex = exoPlayer.currentMediaItemIndex,
                isLoading = loading,
                isPreview = trackId != null && trackId in previewTrackIds,
                shuffle = exoPlayer.shuffleModeEnabled,
                repeat =
                    when (exoPlayer.repeatMode) {
                        Player.REPEAT_MODE_ALL -> RepeatSetting.ALL
                        Player.REPEAT_MODE_ONE -> RepeatSetting.ONE
                        else -> RepeatSetting.OFF
                    },
                notice = notice,
            ),
        )
    }

    private fun startProgressTicker() {
        mainHandler.removeCallbacks(progressTicker)
        mainHandler.postDelayed(progressTicker, PROGRESS_INTERVAL_MS)
    }

    private fun stopProgressTicker() {
        mainHandler.removeCallbacks(progressTicker)
    }

    // --- The EQ's hook into this player ----------------------------------------------------

    private fun openAudioSession() {
        val sessionId = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && (!isAudioSessionActive || currentAudioSessionId != sessionId)) {
            currentAudioSessionId = sessionId
            // Must match the action AudioSessionRepository listens for
            // (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            sendBroadcast(intent)
            isAudioSessionActive = true
        }
    }

    private fun closeAudioSession() {
        if (isAudioSessionActive && currentAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            sendBroadcast(intent)
            isAudioSessionActive = false
        }
    }

    override fun onDestroy() {
        cancelNetworkRetry()
        stopProgressTicker()
        libraryScope.cancel()
        closeAudioSession()
        NowPlayingState.update(null)
        PlaybackQueueState.publish(emptyList())
        // The session's player forwards to ExoPlayer: releasing it releases ExoPlayer.
        session?.let { librarySession ->
            librarySession.player.release()
            librarySession.release()
        }
        session = null
        player = null
        sourceFactory?.release()
        sourceFactory = null
        super.onDestroy()
    }

    // Tapping the notification, the lock screen card or the car's "open on phone" shows the
    // Compose player in MainActivity.
    private fun openPlayerPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            PlayerLaunch.openPlayerIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // What the notification, the lock screen, Bluetooth buttons and the car send arrives here.
    // The queue itself is ExoPlayer's; this only adds what the failure handling needs: a choice
    // of the user starts with a clean slate, and a skip after a failure loads the new track.
    private inner class SessionPlayer(
        private val exoPlayer: ExoPlayer,
    ) : ForwardingPlayer(exoPlayer) {
        override fun play() {
            resetFailureState()
            if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
            super.play()
        }

        override fun pause() {
            cancelNetworkRetry()
            super.pause()
        }

        override fun stop() {
            resetFailureState()
            super.stop()
        }

        override fun seekToNext() = afterFailure { super.seekToNext() }

        override fun seekToNextMediaItem() = afterFailure { super.seekToNextMediaItem() }

        override fun seekToPrevious() = afterFailure { super.seekToPrevious() }

        override fun seekToPreviousMediaItem() = afterFailure { super.seekToPreviousMediaItem() }

        // A track picked from the car's queue list.
        override fun seekTo(
            mediaItemIndex: Int,
            positionMs: Long,
        ) = afterFailure { super.seekTo(mediaItemIndex, positionMs) }

        private fun afterFailure(skip: () -> Unit) {
            resetFailureState()
            skip()
            if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) exoPlayer.prepare()
        }
    }

    // --- Android Auto: browsing the app's lists and "play this" ------------------------------

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!isTrustedBrowser(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            val extras =
                Bundle().apply {
                    putBoolean(CONTENT_STYLE_SUPPORTED, true)
                    putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST)
                    putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
                }
            return Futures.immediateFuture(
                LibraryResult.ofItem(folderItem(AutoMediaId.ROOT), LibraryParams.Builder().setExtras(extras).build()),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!isTrustedBrowser(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            val catalog = AutoCatalogHolder.catalog ?: return Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
            return libraryFuture {
                val nodes = runCatching { catalog.children(parentId) }.getOrDefault(emptyList())
                val start = page.toLong() * pageSize
                val pageNodes = if (start >= nodes.size) emptyList() else nodes.drop(start.toInt()).take(pageSize)
                LibraryResult.ofItemList(pageNodes.map(::nodeItem), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!isTrustedBrowser(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            val id = AutoMediaId.parse(mediaId) ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            val item = if (id is AutoMediaId.Track) trackStub(mediaId) else folderItem(mediaId)
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        // "Play this" in the car names a track of a folder: the whole folder becomes the queue,
        // from that track on. Voice ("play X") arrives as an item without id but with the words.
        // Items that already are queue entries (with an address) go through unchanged.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            resetFailureState()
            if (mediaItems.isNotEmpty() && mediaItems.all { it.localConfiguration != null }) {
                return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
            }
            val first = mediaItems.firstOrNull() ?: return Futures.immediateFailedFuture(UnsupportedOperationException())
            val catalog = AutoCatalogHolder.catalog ?: return Futures.immediateFailedFuture(UnsupportedOperationException())
            val trackId = first.mediaId.takeIf { AutoMediaId.isTrack(it) }
            if (trackId == null && first.mediaId.isNotEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException())
            val query = first.requestMetadata.searchQuery.orEmpty()
            return libraryFuture {
                val queue =
                    if (trackId != null) catalog.queueFor(trackId) else catalog.queueForSearch(query)
                if (queue == null || queue.tracks.isEmpty()) throw UnsupportedOperationException("Nothing to play for this request")
                withContext(Dispatchers.Main) { rememberTracks(queue.tracks) }
                MediaSession.MediaItemsWithStartPosition(
                    queue.tracks.map(TrackMediaItems::toMediaItem),
                    queue.startIndex.coerceIn(0, queue.tracks.lastIndex),
                    0L,
                )
            }
        }

        private fun isTrustedBrowser(browser: MediaSession.ControllerInfo): Boolean =
            browser.uid == Process.myUid() || browser.uid < Process.FIRST_APPLICATION_UID || browser.packageName in TRUSTED_BROWSERS

        private fun folderItem(mediaId: String) =
            MediaItem
                .Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build(),
                ).build()

        private fun trackStub(mediaId: String) =
            MediaItem
                .Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build(),
                ).build()

        private fun nodeItem(node: AutoNode) =
            MediaItem
                .Builder()
                .setMediaId(node.id)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(node.title)
                        .setSubtitle(node.subtitle)
                        .setArtist(if (node.playable) node.subtitle else null)
                        .setArtworkUri(node.artworkUrl?.let(Uri::parse))
                        .setIsBrowsable(!node.playable)
                        .setIsPlayable(node.playable)
                        .setMediaType(if (node.playable) MediaMetadata.MEDIA_TYPE_MUSIC else MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build(),
                ).build()
    }

    // Runs [block] off the main thread and hands its result to the session as a future.
    private fun <T> libraryFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        libraryScope.launch {
            try {
                future.set(block())
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
