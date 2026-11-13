package com.soundcloud.equalizer.player.playback

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
import com.hardbasseq.eq.playlist.QueueMediaId
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
 * @param onResolved told on the main thread which stream a track got (preview or full length)
 */
@OptIn(UnstableApi::class)
class ResolvingTrackMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val resolver: StreamResolver,
    private val onResolved: (trackId: Long, stream: ResolvedStream) -> Unit,
) : MediaSource.Factory {
    // Lookups block on the network: their own threads, never ExoPlayer's playback thread.
    private val lookups: ExecutorService = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

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

    // The player is gone: no new lookups, running ones are interrupted.
    fun release() {
        lookups.shutdownNow()
    }

    // One queue entry. Prepared (by ExoPlayer, on its playback thread) only when the entry is
    // loaded: then the stream is looked up, and the real progressive or HLS source is built and
    // prepared inside this one. Released and prepared again - after an error, for instance -
    // it looks the stream up anew, so an expired address is replaced.
    private inner class ResolvingTrackMediaSource(
        private val mediaItem: MediaItem,
        private val trackId: Long,
    ) : CompositeMediaSource<Int>() {
        private var playbackHandler: Handler? = null
        private var stream: MediaSource? = null
        private var lookupError: IOException? = null

        // Counts preparations: a lookup that answers after a release belongs to an old one.
        private var preparation = 0

        override fun getMediaItem(): MediaItem = mediaItem

        override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
            super.prepareSourceInternal(mediaTransferListener)
            val handler = Util.createHandlerForCurrentLooper()
            playbackHandler = handler
            val thisPreparation = ++preparation
            try {
                lookups.execute {
                    val result =
                        try {
                            Result.success(resolver.resolve(trackId))
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    handler.post { onLookupDone(thisPreparation, result) }
                }
            } catch (e: java.util.concurrent.RejectedExecutionException) {
                lookupError = IOException("Player released", e)
            }
        }

        private fun onLookupDone(
            fromPreparation: Int,
            result: Result<ResolvedStream>,
        ) {
            if (fromPreparation != preparation || playbackHandler == null) return
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

    private companion object {
        const val CHILD_ID = 0
    }
}
