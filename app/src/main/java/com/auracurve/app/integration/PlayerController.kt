package com.auracurve.app.integration

import android.content.Context
import android.content.Intent
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.player.playback.NowPlaying
import com.auracurve.app.player.playback.NowPlayingState
import com.auracurve.app.player.playback.PlaybackQueueState
import com.auracurve.app.player.playback.QueueRequest
import com.auracurve.app.player.playback.QueueRequests
import com.auracurve.app.player.playback.RepeatSetting
import com.auracurve.app.player.service.AudioPlayerService
import com.auracurve.app.playlist.QueuePositions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LinkImportResult {
    data class Track(
        val track: TrackItem,
    ) : LinkImportResult

    data class Playlist(
        val title: String,
        val sourceUrl: String,
        val tracks: List<TrackItem>,
    ) : LinkImportResult

    data class Failed(
        val message: String,
    ) : LinkImportResult
}

// Result of loading something from the user's SoundCloud account: the value, or a
// message that can be shown as is.
sealed interface LoadResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : LoadResult<T>

    data class Error(
        val message: String,
    ) : LoadResult<Nothing>
}

// What the full player screen needs from the built-in player: its state, a queue
// to step through, and (through OnlineSources) link resolution and the online services. Separate from PlayerBridge (the master
// bar's launch/stop/toggle contract) so that one stays small.
interface PlayerController : OnlineSources {
    val nowPlaying: StateFlow<NowPlaying?>
    val queue: StateFlow<List<TrackItem>>

    fun playQueue(
        tracks: List<TrackItem>,
        startIndex: Int,
    )

    // Right after the track that plays now (a new queue when nothing is loaded).
    fun playNext(tracks: List<TrackItem>)

    // At the end of the queue (a new queue when nothing is loaded).
    fun addToQueue(tracks: List<TrackItem>)

    fun moveQueueItem(
        from: Int,
        to: Int,
    )

    fun removeQueueItem(index: Int)

    // Removes everything behind [index] in one step ("clear upcoming").
    fun removeQueueItemsAfter(index: Int)

    fun setShuffle(enabled: Boolean)

    fun setRepeat(setting: RepeatSetting)

    fun skipToIndex(index: Int)

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun togglePlayback()
}

@Singleton
class AndroidPlayerController
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        online: OnlineSources,
    ) : PlayerController,
        OnlineSources by online {
        override val nowPlaying: StateFlow<NowPlaying?> = NowPlayingState.current
        override val queue: StateFlow<List<TrackItem>> = PlaybackQueueState.queue

        override fun playQueue(
            tracks: List<TrackItem>,
            startIndex: Int,
        ) {
            if (tracks.isEmpty()) return
            request(QueueRequest.Replace(tracks, startIndex.coerceIn(0, tracks.lastIndex)))
        }

        override fun playNext(tracks: List<TrackItem>) {
            if (tracks.isNotEmpty()) request(QueueRequest.PlayNext(tracks))
        }

        override fun addToQueue(tracks: List<TrackItem>) {
            if (tracks.isNotEmpty()) request(QueueRequest.Append(tracks))
        }

        // Queue commands name the entry by its position and by the track the screen saw there:
        // a command sent just before may have shifted it, and the service then finds the track
        // again instead of hitting its neighbour (see QueuePositions).
        override fun moveQueueItem(
            from: Int,
            to: Int,
        ) = send(AudioPlayerService.ACTION_MOVE_QUEUE_ITEM) {
            putExtra(AudioPlayerService.EXTRA_FROM_INDEX, from)
            putExtra(AudioPlayerService.EXTRA_TO_INDEX, to)
            putExtra(AudioPlayerService.EXTRA_TRACK_ID, trackIdAt(from))
            putExtra(AudioPlayerService.EXTRA_TO_TRACK_ID, trackIdAt(to))
        }

        override fun removeQueueItem(index: Int) =
            send(AudioPlayerService.ACTION_REMOVE_QUEUE_ITEM) {
                putExtra(AudioPlayerService.EXTRA_QUEUE_INDEX, index)
                putExtra(AudioPlayerService.EXTRA_TRACK_ID, trackIdAt(index))
            }

        override fun removeQueueItemsAfter(index: Int) =
            send(AudioPlayerService.ACTION_REMOVE_QUEUE_ITEMS_AFTER) {
                putExtra(AudioPlayerService.EXTRA_QUEUE_INDEX, index)
                putExtra(AudioPlayerService.EXTRA_TRACK_ID, trackIdAt(index))
            }

        private fun trackIdAt(index: Int): Long = queue.value.getOrNull(index)?.id ?: QueuePositions.NO_TRACK

        override fun setShuffle(enabled: Boolean) =
            send(AudioPlayerService.ACTION_SET_SHUFFLE) { putExtra(AudioPlayerService.EXTRA_SHUFFLE, enabled) }

        override fun setRepeat(setting: RepeatSetting) =
            send(AudioPlayerService.ACTION_SET_REPEAT) { putExtra(AudioPlayerService.EXTRA_REPEAT, setting.name) }

        // The tracks wait in QueueRequests (an intent cannot carry them); the intent tells the
        // service to take them out.
        private fun request(request: QueueRequest) {
            QueueRequests.submit(request)
            send(AudioPlayerService.ACTION_QUEUE_REQUEST)
        }

        override fun skipToIndex(index: Int) =
            send(AudioPlayerService.ACTION_PLAY_INDEX) {
                putExtra(AudioPlayerService.EXTRA_QUEUE_INDEX, index)
                putExtra(AudioPlayerService.EXTRA_TRACK_ID, trackIdAt(index))
            }

        override fun next() = send(AudioPlayerService.ACTION_NEXT)

        override fun previous() = send(AudioPlayerService.ACTION_PREVIOUS)

        override fun seekTo(positionMs: Long) =
            send(AudioPlayerService.ACTION_SEEK_TO) {
                putExtra(AudioPlayerService.EXTRA_POSITION_MS, positionMs)
            }

        override fun togglePlayback() = send(AudioPlayerService.ACTION_TOGGLE_PLAYBACK)

        private fun send(
            action: String,
            configure: Intent.() -> Unit = {},
        ) {
            val intent =
                Intent(context, AudioPlayerService::class.java).apply {
                    this.action = action
                    configure()
                }
            context.startService(intent)
        }
    }
