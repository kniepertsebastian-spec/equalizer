package com.soundcloud.equalizer.player.playback

import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

// What "repeat" does when the queue (or the track) ends.
enum class RepeatSetting { OFF, ALL, ONE }

// Something the player wants the user to know about. The UI shows it as long as it is set;
// it used to be a toast from the service, which vanished before anyone could read it and
// popped up over other apps.
sealed interface PlayerNotice {
    // No connection: paused, the same track is tried again shortly.
    data object WaitingForNetwork : PlayerNotice

    // [title] could not be played and was skipped.
    data class NotPlayable(
        val title: String,
    ) : PlayerNotice

    // Still no connection after several tries: paused until the user presses play.
    data object NetworkGaveUp : PlayerNotice

    // Several tracks in a row could not be played: paused instead of eating the queue.
    data object TooManyUnplayable : PlayerNotice
}

data class NowPlaying(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val artworkUrl: String? = null,
    // 0 while unknown (before the stream is ready, or for a live stream).
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    // Position inside PlaybackQueueState.queue, -1 when nothing is loaded.
    val queueIndex: Int = -1,
    // True from the moment a track is chosen until its stream is ready to play.
    val isLoading: Boolean = false,
    // The stream being played is only a ~30 second preview, not the full track.
    val isPreview: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatSetting = RepeatSetting.OFF,
    val notice: PlayerNotice? = null,
)

// AudioPlayerService isn't Hilt-managed (predates the merge into :app, see
// settings.gradle.kts), and HardBass EQ's view models have no service connection of their
// own to read playback state from - both are in the same process, so a plain shared
// singleton is enough to let the screens reflect what AudioPlayerService is doing without
// either side needing to bind to the other. Written only by the service.
object NowPlayingState {
    private val _current = MutableStateFlow<NowPlaying?>(null)
    val current: StateFlow<NowPlaying?> = _current.asStateFlow()

    fun update(value: NowPlaying?) {
        _current.value = value
    }
}

// The play queue as ExoPlayer holds it (in queue order, also while shuffling). Written only
// by AudioPlayerService, whenever the player's list changes.
object PlaybackQueueState {
    private val _queue = MutableStateFlow<List<TrackItem>>(emptyList())
    val queue: StateFlow<List<TrackItem>> = _queue.asStateFlow()

    fun publish(tracks: List<TrackItem>) {
        _queue.value = tracks
    }
}

// What a screen asks the player to do with a list of tracks.
sealed interface QueueRequest {
    // Play [tracks] as the new queue, from [startIndex].
    data class Replace(
        val tracks: List<TrackItem>,
        val startIndex: Int,
    ) : QueueRequest

    // Right after the track that plays now ("play next").
    data class PlayNext(
        val tracks: List<TrackItem>,
    ) : QueueRequest

    // At the end of the queue.
    data class Append(
        val tracks: List<TrackItem>,
    ) : QueueRequest
}

// Tracks cannot travel inside an intent (TrackItem is not parcelable), so a screen leaves its
// request here and sends AudioPlayerService.ACTION_QUEUE_REQUEST; the service takes the
// requests out one per intent, in the order they came. Same process, like NowPlayingState.
object QueueRequests {
    private val pending = ConcurrentLinkedQueue<QueueRequest>()

    fun submit(request: QueueRequest) {
        pending.add(request)
    }

    fun take(): QueueRequest? = pending.poll()
}
