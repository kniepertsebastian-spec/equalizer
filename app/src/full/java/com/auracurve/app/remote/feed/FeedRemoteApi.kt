package com.auracurve.app.remote.feed

import com.auracurve.app.discovery.DiscoveryRepository
import com.auracurve.app.discovery.DiscoveryState
import com.auracurve.app.discovery.DiscoveryTrack
import com.auracurve.app.discovery.GenreMode
import com.auracurve.app.discovery.RefreshResult
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.remote.ApiError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The feed ("Interesting new uploads") of the remote-control API (Pixel-Car module 04d). It reads
 * the same DiscoveryRepository as the player screen and pulls with the same DiscoveryUpdater, so
 * the app and the web remote never see two different lists.
 */
class FeedRemoteApi(
    private val repository: DiscoveryRepository,
    /** What the app's "Refresh now" does (DiscoveryUpdater.refresh with onlyIfDue = false). */
    private val refresh: suspend () -> RefreshResult,
    /** False without a usable internet connection: pull answers 409 OFFLINE then (V-04). */
    private val online: () -> Boolean,
    private val playQueue: (List<TrackItem>) -> Unit,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = PULL_TIMEOUT_MS,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val pulling = AtomicBoolean(false)
    private val _updates = MutableSharedFlow<JsonObject>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** feed.updated, once per pull: `{added, filteredByGenre}` and, if it failed, `error`. */
    val updates: SharedFlow<JsonObject> = _updates.asSharedFlow()

    suspend fun feed(): JsonObject = toJson(repository.current())

    /** Starts a pull and returns at once (202); the result comes as feed.updated. */
    fun pull() {
        if (!online()) throw ApiError(HttpStatusCode.Conflict, "OFFLINE", "Keine Internetverbindung")
        if (!pulling.compareAndSet(false, true)) throw ApiError(HttpStatusCode.Conflict, "BUSY", "Der Feed wird schon abgerufen")
        scope.launch {
            val event =
                try {
                    when (val result = withTimeout(timeoutMs) { refresh() }) {
                        is RefreshResult.Updated -> result(result.count, result.filteredByGenre)
                        RefreshResult.Skipped -> result(0, 0)
                        is RefreshResult.Failed -> result(0, 0, result.message ?: "Keine Antwort")
                    }
                } catch (e: TimeoutCancellationException) {
                    result(0, 0, "Zeitüberschreitung")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    result(0, 0, "Abruf fehlgeschlagen")
                } finally {
                    pulling.set(false)
                }
            _updates.tryEmit(event)
        }
    }

    /** Plays the whole feed in the built-in player; returns how many tracks. */
    suspend fun playAll(): Int {
        val tracks = repository.current().playlist.map { it.toTrackItem() }
        if (tracks.isEmpty()) throw ApiError(HttpStatusCode.NotFound, "TRACK_NOT_FOUND", "Der Feed ist leer")
        playQueue(tracks)
        return tracks.size
    }

    fun toJson(state: DiscoveryState): JsonObject =
        buildJsonObject {
            putJsonObject("status") {
                put("trackCount", state.playlist.size)
                put("genreMode", state.genreMode.name)
                putJsonArray("genres") {
                    val genres = if (state.genreMode == GenreMode.AUTO) state.autoGenres else state.genres
                    genres.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
                put("lastPullAt", state.updatedAtMs.takeIf { it > 0 }?.let { ISO.format(Instant.ofEpochMilli(it).atZone(zone)) })
                // The weekly job checks every few hours; there is no fixed next time to report.
                put("nextPullAt", null as String?)
            }
            putJsonArray("tracks") {
                state.playlist.forEach { track ->
                    addJsonObject {
                        // The SoundCloud track id: stable, not a list position (Q-S07).
                        put("trackId", track.track.id.toString())
                        put("title", track.track.title)
                        put("artist", track.track.artist)
                        put("durationMs", track.track.durationMs)
                        if (track.genre.isNotEmpty()) put("genre", track.genre)
                    }
                }
            }
            putJsonArray("artists") {
                state.artists.forEach { name ->
                    addJsonObject {
                        // Artists are kept by name only; the name is their id.
                        put("artistId", name)
                        put("name", name)
                    }
                }
            }
        }

    private fun result(
        added: Int,
        filtered: Int,
        error: String? = null,
    ) = buildJsonObject {
        put("added", added)
        put("filteredByGenre", filtered)
        error?.let { put("error", it) }
    }

    companion object {
        const val PULL_TIMEOUT_MS = 15_000L
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        // As DiscoveryViewModel: no stream URL, the player resolves a fresh one by id.
        fun DiscoveryTrack.toTrackItem() =
            TrackItem(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                streamUrl = null,
                durationMs = track.durationMs,
                createdAtMs = uploadedAtMs,
            )
    }
}
