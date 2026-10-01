package com.soundcloud.equalizer.player.playback

import android.support.v4.media.session.MediaSessionCompat
import com.soundcloud.equalizer.player.model.TrackItem

// One entry of the list a car (Android Auto) shows: a folder to open or a track to play.
data class AutoNode(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val playable: Boolean,
)

// What starting a track in the car plays: the queue (the folder the track is in) and
// where in it to begin.
data class AutoQueue(
    val tracks: List<TrackItem>,
    val startIndex: Int,
)

// What the car can browse and play. The app (which owns the playlists) provides it; the
// player service only asks. Ids are described in com.hardbasseq.eq.auto.AutoMediaId.
interface AutoCatalog {
    suspend fun children(parentId: String): List<AutoNode>

    suspend fun queueFor(mediaId: String): AutoQueue?
}

// Same-process hand-over points between the app (browser service, catalog) and the
// player service (media session), like NowPlayingState.
object AutoCatalogHolder {
    @Volatile
    var catalog: AutoCatalog? = null
}

object AutoSessionState {
    // Token of the player's media session; the car's browser service gives it to Android
    // Auto so that the controls (play, pause, next, "play this") reach the player.
    @Volatile
    var sessionToken: MediaSessionCompat.Token? = null
}
