package com.hardbasseq.eq.auto

// The ids of what the car's media browser (Android Auto) shows: a few folders and, inside
// them, tracks. Playlist ids can be web addresses with colons and slashes, so the parts
// are separated by "|", which no id contains. Pure functions, free of Android.
//
//   root                       the top level
//   playlists                  folder: the playlists on the device
//   discovery                  folder: this week's "Interesting new uploads"
//   likes                      folder: the signed-in account's likes
//   pl|<playlistId>            folder: the tracks of one playlist
//   pl|<playlistId>|<trackId>  a track of that playlist
//   disc||<trackId>            a track of the discovery list
//   likes||<trackId>           a liked track
sealed interface AutoMediaId {
    data object Root : AutoMediaId

    data object PlaylistsFolder : AutoMediaId

    data object DiscoveryFolder : AutoMediaId

    data object LikesFolder : AutoMediaId

    data class PlaylistFolder(
        val playlistId: String,
    ) : AutoMediaId

    // [source] is where the track sits in: its list becomes the play queue.
    data class Track(
        val source: Source,
        val playlistId: String,
        val trackId: Long,
    ) : AutoMediaId

    enum class Source { PLAYLIST, DISCOVERY, LIKES }

    companion object {
        const val ROOT = "root"
        const val PLAYLISTS = "playlists"
        const val DISCOVERY = "discovery"
        const val LIKES = "likes"
        private const val PLAYLIST_PREFIX = "pl"
        private const val DISCOVERY_PREFIX = "disc"
        private const val LIKES_PREFIX = "likes"
        private const val SEPARATOR = '|'

        fun playlistFolder(playlistId: String): String = "$PLAYLIST_PREFIX$SEPARATOR$playlistId"

        fun playlistTrack(
            playlistId: String,
            trackId: Long,
        ): String = "$PLAYLIST_PREFIX$SEPARATOR$playlistId$SEPARATOR$trackId"

        fun discoveryTrack(trackId: Long): String = "$DISCOVERY_PREFIX$SEPARATOR$SEPARATOR$trackId"

        fun likedTrack(trackId: Long): String = "$LIKES_PREFIX$SEPARATOR$SEPARATOR$trackId"

        // Anything that is not an id of ours reads as null (another app's id, garbage).
        fun parse(id: String): AutoMediaId? {
            when (id) {
                ROOT -> return Root
                PLAYLISTS -> return PlaylistsFolder
                DISCOVERY -> return DiscoveryFolder
                LIKES -> return LikesFolder
            }
            val parts = id.split(SEPARATOR)
            return when (parts.firstOrNull()) {
                PLAYLIST_PREFIX ->
                    when (parts.size) {
                        2 -> parts[1].takeIf { it.isNotEmpty() }?.let { PlaylistFolder(it) }
                        3 ->
                            parts[2].toLongOrNull()?.let { trackId ->
                                parts[1].takeIf { it.isNotEmpty() }?.let { Track(Source.PLAYLIST, it, trackId) }
                            }
                        else -> null
                    }

                DISCOVERY_PREFIX -> if (parts.size == 3) parts[2].toLongOrNull()?.let { Track(Source.DISCOVERY, "", it) } else null
                LIKES_PREFIX -> if (parts.size == 3) parts[2].toLongOrNull()?.let { Track(Source.LIKES, "", it) } else null
                else -> null
            }
        }

        // Whether [id] is a track a car can start (not a folder).
        fun isTrack(id: String): Boolean = parse(id) is Track
    }
}
