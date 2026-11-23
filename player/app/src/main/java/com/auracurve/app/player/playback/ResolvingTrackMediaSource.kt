package com.auracurve.app.player.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.auracurve.app.playlist.QueueMediaId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * Builds the sources for the play queue. A queue entry only names its track (see
 * TrackMediaItems); its stream address is looked up when ExoPlayer starts loading the entry -
 * the playing track, or the next one while the current one is still playing (preloading).
 * Anything else goes to [delegate] unchanged.
 *
 * Why not a ResolvingDataSource: SoundCloud serves a track either as a plain file or as an HLS
 * playlist, and which ExoPlayer source fits (progressive or HLS) is only known after the lookup.
 * A data source can swap the address but not the kind of source.
 *
 * ExoPlayer prepares playlist entries lazily (ExoPlayer.Builder.setUseLazyPreparation, on by
 * default), so a long queue does not look up every track at once.
 *
 * Lookups are coroutines in one scope of their own, never on ExoPlayer's playback thread. At most
 * [MAX_ACTIVE_LOOKUPS] run at a time (the playing track and the preloaded next one need no more):
 * skipping quickly through the queue or a hanging connection used to pile up one blocked thread
 * per entry. An entry ExoPlayer no longer uses (skipped past, disabled) cancels its lookup; one
 * still waiting for its turn never reaches the network.
 *
 * @param onResolved told on the main thread which stream a track got (preview or full length)
 * @param lookupContext where lookups run. Tests pass one that runs them at once, in step with
 *   the fake clock.
 */
@OptIn(UnstableApi::class)
class ResolvingTrackMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val resolver: StreamResolver,
    private val onResolved: (trackId: Long, stream: ResolvedStream) -> Unit,
    lookupContext: CoroutineContext = defaultLookupContext(),
) : MediaSource.Factory {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lookupScope = CoroutineScope(SupervisorJob() + lookupContext)

    // Bounds the lookups that are actually running, whatever dispatcher the resolver switches
    // to inside (SoundCloudClient moves to Dispatchers.IO, outside any limitedParallelism view).
    private val activeLookups = Semaphore(MAX_ACTIVE_LOOKUPS)

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val trackId =
            mediaItem.localConfiguration
                ?.uri
                ?.toString()
                ?.let(QueueMediaId::trackIdFromStreamUri)
                ?: return delegate.createMediaSource(mediaItem)
        return ResolvingTrackMediaSource(mediaItem, trackId)
    }

    // The player is gone: no new lookups, running ones are cancelled.
    fun release() {
        lookupScope.cancel()
    }

    // One queue entry. Prepared (by ExoPlayer, on its playback thread) only when the entry is
    // loaded: then the stream is looked up, and the real progressive or HLS source is built and
    // prepared inside this one. Released and prepared again - after an error, for instance -
    // it looks the stream up anew, so an expired address is replaced.
    //
    // ExoPlayer disables an entry it no longer uses (the user skipped past it before it played):
    // a lookup still running then is cancelled, and enabling the entry again starts it anew.
    private inner class ResolvingTrackMediaSource(
        private val mediaItem: MediaItem,
        private val trackId: Long,
    ) : CompositeMediaSource<Int>() {
        private var playbackHandler: Handler? = null
        private var stream: MediaSource? = null
        private var lookupError: IOException? = null
        private var lookup: Job? = null

        // Counts lookups: an answer that arrives after a release or a cancel belongs to an old one.
        private var lookupGeneration = 0

        override fun getMediaItem(): MediaItem = mediaItem

        override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
            super.prepareSourceInternal(mediaTransferListener)
            playbackHandler = Util.createHandlerForCurrentLooper()
            startLookup()
        }

        private fun startLookup() {
            val handler = playbackHandler ?: return
            val thisLookup = ++lookupGeneration
            if (!lookupScope.isActive) {
                lookupError = IOException("Player released")
                return
            }
            lookup =
                lookupScope.launch {
                    val result =
                        try {
                            Result.success(activeLookups.withPermit { resolver.resolve(trackId) })
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    handler.post { onLookupDone(thisLookup, result) }
                }
        }

        private fun cancelLookup() {
            lookup?.cancel()
            lookup = null
            lookupGeneration++
        }

        override fun enableInternal() {
            super.enableInternal()
            // Its lookup was cancelled while disabled: the entry is due again, look it up now.
            if (stream == null && lookupError == null && lookup == null) startLookup()
        }

        override fun disableInternal() {
            if (stream == null) cancelLookup()
            super.disableInternal()
        }

        private fun onLookupDone(
            fromLookup: Int,
            result: Result<ResolvedStream>,
        ) {
            if (fromLookup != lookupGeneration || playbackHandler == null) return
            lookup = null
            val resolved = result.getOrElse { e ->
                lookupError = e as? IOException ?: IOException(e.message, e)
                return
            }
            mainHandler.post { onResolved(trackId, resolved) }
            val streamItem =
                mediaItem
                    .buildUpon()
                    .setUri(resolved.url)
                    .setMimeType(if (resolved.isHls) MimeTypes.APPLICATION_M3U8 else null)
                    .build()
            val source = delegate.createMediaSource(streamItem)
            stream = source
            prepareChildSource(CHILD_ID, source)
        }

        // A failed lookup surfaces where ExoPlayer expects preparation errors: when this entry
        // is (or becomes) the one that plays - an error while preloading the next track does
        // not stop the current one.
        @Throws(IOException::class)
        override fun maybeThrowSourceInfoRefreshError() {
            lookupError?.let { throw it }
            super.maybeThrowSourceInfoRefreshError()
        }

        override fun onChildSourceInfoRefreshed(
            childSourceId: Int?,
            mediaSource: MediaSource,
            newTimeline: Timeline,
        ) {
            refreshSourceInfo(QueueEntryTimeline(newTimeline, mediaItem))
        }

        // ExoPlayer only asks for a period once this source reported its timeline, which
        // happens after the stream source was built.
        override fun createPeriod(
            id: MediaSource.MediaPeriodId,
            allocator: Allocator,
            startPositionUs: Long,
        ): MediaPeriod = checkNotNull(stream) { "Stream of track $trackId not resolved yet" }.createPeriod(id, allocator, startPositionUs)

        override fun releasePeriod(mediaPeriod: MediaPeriod) {
            stream?.releasePeriod(mediaPeriod)
        }

        override fun releaseSourceInternal() {
            cancelLookup()
            playbackHandler?.removeCallbacksAndMessages(null)
            playbackHandler = null
            stream = null
            lookupError = null
            super.releaseSourceInternal()
        }
    }

    // The stream source's timeline, but its window still shows the queue entry rather than the
    // short-lived stream address: what the player, the session and the screens see stays the
    // entry that was queued.
    private class QueueEntryTimeline(
        timeline: Timeline,
        private val mediaItem: MediaItem,
    ) : ForwardingTimeline(timeline) {
        override fun getWindow(
            windowIndex: Int,
            window: Timeline.Window,
            defaultPositionProjectionUs: Long,
        ): Timeline.Window {
            super.getWindow(windowIndex, window, defaultPositionProjectionUs)
            window.mediaItem = mediaItem
            return window
        }
    }

    companion object {
        // The playing track and the preloaded next one.
        const val MAX_ACTIVE_LOOKUPS = 2

        private const val CHILD_ID = 0

        private fun defaultLookupContext(): CoroutineContext = Dispatchers.IO.limitedParallelism(MAX_ACTIVE_LOOKUPS, "stream-lookups")
    }
}
