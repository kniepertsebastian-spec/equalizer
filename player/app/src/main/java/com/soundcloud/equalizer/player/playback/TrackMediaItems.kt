package com.soundcloud.equalizer.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.hardbasseq.eq.playlist.LocalTrackId
import com.hardbasseq.eq.playlist.PlayerFormat
import com.hardbasseq.eq.playlist.QueueMediaId
import com.soundcloud.equalizer.player.model.TrackItem

// A track as an entry of ExoPlayer's playlist (the play queue), and back. The entry carries
// no stream address, only a placeholder with the track id (see QueueMediaId):
// ResolvingTrackMediaSource asks SoundCloud for a fresh address when ExoPlayer loads it.
object TrackMediaItems {
    // The list-size cover as the track had it; the metadata's artwork is the large one that the
    // notification, the lock screen and the car show.
    private const val EXTRA_ARTWORK_URL = "com.hardbasseq.eq.artwork_url"

    fun toMediaItem(track: TrackItem): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(QueueMediaId.of(track.id))
            // A file on the device plays from its own address (nothing to look up, nothing that expires);
            // everything else is the placeholder ResolvingTrackMediaSource turns into a stream.
            .setUri(track.streamUrl?.takeIf(LocalTrackId::isLocalAddress) ?: QueueMediaId.streamUri(track.id))
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(PlayerFormat.largeArtwork(track.artworkUrl)?.let(Uri::parse))
                    .setDurationMs(track.durationMs.takeIf { it > 0 })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(Bundle().apply { putString(EXTRA_ARTWORK_URL, track.artworkUrl) })
                    .build(),
            ).build()

    // The track id of an entry of ours, null for anything else.
    fun trackId(item: MediaItem): Long? = QueueMediaId.trackId(item.mediaId)

    // Rebuilt from the entry alone; the service prefers the TrackItem it was given.
    fun toTrackItem(item: MediaItem): TrackItem? {
        val id = trackId(item) ?: return null
        val metadata = item.mediaMetadata
        return TrackItem(
            id = id,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            artworkUrl = metadata.extras?.getString(EXTRA_ARTWORK_URL),
            streamUrl = null,
            durationMs = metadata.durationMs ?: 0L,
        )
    }
}
