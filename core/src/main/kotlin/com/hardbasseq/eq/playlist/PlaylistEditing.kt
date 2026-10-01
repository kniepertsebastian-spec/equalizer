package com.hardbasseq.eq.playlist

// Playlists the user makes on the device (as opposed to imported share links, which
// have a sourceUrl and are refreshed by importing the link again). Pure functions so
// the edge cases are unit-testable.
object PlaylistEditing {
    const val MAX_TITLE_LENGTH = 60

    fun isLocal(playlist: SavedPlaylist): Boolean = playlist.sourceUrl == null

    // Blank names are refused (null); the id is unique among the existing playlists.
    fun create(
        title: String,
        existing: List<SavedPlaylist>,
        nowMs: Long,
        firstTrack: SavedTrack? = null,
    ): SavedPlaylist? {
        val clean = title.trim().take(MAX_TITLE_LENGTH)
        if (clean.isEmpty()) return null
        var id = "local-$nowMs"
        var suffix = 1
        while (existing.any { it.id == id }) id = "local-$nowMs-${suffix++}"
        return SavedPlaylist(id = id, title = clean, tracks = listOfNotNull(firstTrack), createdAtMs = nowMs)
    }

    // A track is in a playlist at most once; adding it again changes nothing.
    fun addTrack(
        playlist: SavedPlaylist,
        track: SavedTrack,
    ): SavedPlaylist = if (playlist.tracks.any { it.id == track.id }) playlist else playlist.copy(tracks = playlist.tracks + track)

    fun removeTrack(
        playlist: SavedPlaylist,
        trackId: Long,
    ): SavedPlaylist = playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })

    fun contains(
        playlist: SavedPlaylist,
        trackId: Long,
    ): Boolean = playlist.tracks.any { it.id == trackId }

    // One new playlist holding the tracks of all [sources] in order, each track once.
    // The sources stay as they are. Null for a blank name or nothing to merge.
    fun merge(
        title: String,
        sources: List<SavedPlaylist>,
        existing: List<SavedPlaylist>,
        nowMs: Long,
    ): SavedPlaylist? {
        if (sources.isEmpty()) return null
        val tracks = sources.flatMap { it.tracks }.distinctBy { it.id }
        return create(title, existing, nowMs)?.copy(tracks = tracks)
    }
}
