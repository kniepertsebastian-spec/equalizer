package com.auracurve.app.remote.library

import com.auracurve.app.link.LibraryPlaylist
import com.auracurve.app.link.LibraryTrack
import com.auracurve.app.link.PlaylistInfo
import com.auracurve.app.link.SpotifyLibrary
import com.auracurve.app.link.SpotifyLibraryJson
import com.auracurve.app.remote.ApiError
import com.auracurve.app.remote.EqRemoteApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/** Reads the signed-in user's playlists from Spotify's Web API (needs internet). */
interface SpotifyLibraryReader {
    fun isSignedIn(): Boolean

    suspend fun playlists(): List<PlaylistInfo>

    /** Null when Spotify does not hand out this playlist's songs (not the user's own). */
    suspend fun tracks(playlistId: String): List<LibraryTrack>?
}

/** Where the offline copy lives between app starts. */
interface SpotifyLibraryStore {
    suspend fun load(): SpotifyLibrary?

    suspend fun save(library: SpotifyLibrary)
}

/** Hands a spotify: address to the Spotify app in user 0. Called on the main thread. */
interface SpotifyStarter {
    /** Null when Spotify can be reached at all, else why not (German). */
    fun unavailableReason(): String?

    /** True when Spotify's session took the address, false when there is no session yet. */
    fun playUri(uri: String): Boolean

    /** Wakes Spotify without a session (a media button press), so a session comes up. */
    fun wake()
}

/**
 * The Spotify library of the remote-control API (Pixel-Car, way B): a copy of the user's
 * playlists and songs, synced through the Web API while the phone is online and served from
 * the copy when it is not. Playing hands spotify:track:/spotify:playlist: addresses to the
 * Spotify app, which plays its downloads offline.
 *
 * Endpoints: `GET library/spotify` (status and playlists with their song ids), `GET
 * library/spotify/tracks` (every song once), `POST library/spotify/sync` (202, result as
 * `library.updated`), `POST library/spotify/play` `{playlistId, trackId?}`.
 */
class SpotifyLibraryRemoteApi(
    private val reader: SpotifyLibraryReader,
    private val store: SpotifyLibraryStore,
    private val online: () -> Boolean,
    private val starter: SpotifyStarter,
    private val mainDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val syncTimeoutMs: Long = SYNC_TIMEOUT_MS,
    private val wakeWaitMs: Long = WAKE_WAIT_MS,
    private val wakePollMs: Long = WAKE_POLL_MS,
) {
    private val loadLock = Mutex()
    private var loaded = false

    @Volatile
    private var library: SpotifyLibrary? = null

    @Volatile
    private var lastError: String? = null
    private val syncing = AtomicBoolean(false)
    private val _updates = MutableSharedFlow<JsonObject>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** library.updated after every sync: the status block, with `error` when it failed. */
    val updates: SharedFlow<JsonObject> = _updates.asSharedFlow()

    suspend fun index(): JsonObject {
        val current = current()
        return buildJsonObject {
            put("status", status(current))
            putJsonArray("playlists") {
                current?.playlists?.forEach { playlist -> addJsonObject { putPlaylist(playlist) } }
            }
        }
    }

    suspend fun tracks(): JsonObject {
        val current = current()
        return buildJsonObject {
            putJsonArray("tracks") {
                current?.tracks?.values?.forEach { track -> addJsonObject { putTrack(track) } }
            }
        }
    }

    /** Starts a sync and returns at once (202); the result comes as library.updated. */
    fun sync() {
        if (!reader.isSignedIn()) throw notSignedIn()
        if (!online()) throw ApiError(HttpStatusCode.Conflict, "OFFLINE", "Keine Internetverbindung")
        if (!syncing.compareAndSet(false, true)) throw ApiError(HttpStatusCode.Conflict, "BUSY", "Die Bibliothek wird schon abgeglichen")
        scope.launch { runSync() }
    }

    /**
     * The automatic sync of the server: when online, signed in and the copy is older than
     * [maxAgeMs] (or missing). Returns whether one was started.
     */
    suspend fun syncIfDue(maxAgeMs: Long = AUTO_SYNC_AGE_MS): Boolean {
        if (!reader.isSignedIn() || !online()) return false
        val age = current()?.let { clock() - it.syncedAtMs } ?: Long.MAX_VALUE
        if (age < maxAgeMs) return false
        if (!syncing.compareAndSet(false, true)) return false
        runSync()
        return true
    }

    /**
     * Plays a song of a playlist from the copy, or the whole playlist without `trackId`. Only ids
     * from the copy are ever turned into a spotify: address.
     */
    suspend fun play(body: JsonObject) {
        val playlistId = body.text("playlistId") ?: throw EqRemoteApi.badRequest("playlistId fehlt")
        val trackId = body.text("trackId")
        val playlist =
            current()?.playlist(playlistId)
                ?: throw ApiError(HttpStatusCode.NotFound, "PLAYLIST_NOT_FOUND", "Playlist nicht in der Bibliothek")
        if (trackId != null && playlist.trackIds?.contains(trackId) != true) {
            throw ApiError(HttpStatusCode.NotFound, "TRACK_NOT_FOUND", "Titel nicht in dieser Playlist")
        }
        val uri = if (trackId != null) "spotify:track:$trackId" else "spotify:playlist:${playlist.id}"
        val started =
            withContext(mainDispatcher) {
                starter.unavailableReason()?.let { throw sourceUnavailable(it) }
                if (starter.playUri(uri)) {
                    true
                } else {
                    starter.wake()
                    false
                }
            }
        if (started) return
        // Spotify had no session: the media button starts it; hand over the address once it is up.
        var waited = 0L
        while (waited < wakeWaitMs) {
            delay(wakePollMs)
            waited += wakePollMs
            if (withContext(mainDispatcher) { starter.playUri(uri) }) return
        }
        throw sourceUnavailable("Spotify startet nicht")
    }

    private suspend fun runSync() {
        val event =
            try {
                val fresh = withTimeout(syncTimeoutMs) { download() }
                store.save(fresh)
                library = fresh
                lastError = null
                status(fresh)
            } catch (e: TimeoutCancellationException) {
                failed("Zeitüberschreitung")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e.message?.takeIf { it.isNotBlank() } ?: "Abgleich fehlgeschlagen")
            } finally {
                syncing.set(false)
            }
        _updates.tryEmit(event)
    }

    private suspend fun failed(message: String): JsonObject {
        lastError = message
        return status(current())
    }

    private suspend fun download(): SpotifyLibrary {
        val tracks = LinkedHashMap<String, LibraryTrack>()
        val playlists =
            reader.playlists().distinctBy { it.id }.map { info ->
                val songs = reader.tracks(info.id)
                songs?.forEach { tracks.putIfAbsent(it.id, it) }
                LibraryPlaylist(info.id, info.name, info.owner, info.trackCount, songs?.map { it.id })
            }
        return SpotifyLibrary(clock(), playlists, tracks)
    }

    private suspend fun current(): SpotifyLibrary? {
        if (!loaded) {
            loadLock.withLock {
                if (!loaded) {
                    if (library == null) library = runCatching { store.load() }.getOrNull()
                    loaded = true
                }
            }
        }
        return library
    }

    private fun status(current: SpotifyLibrary?): JsonObject =
        buildJsonObject {
            put("signedIn", reader.isSignedIn())
            put("syncing", syncing.get())
            put("online", online())
            put("syncedAt", current?.syncedAtMs?.takeIf { it > 0 }?.let { ISO.format(Instant.ofEpochMilli(it).atZone(zone).withNano(0)) })
            put("playlistCount", current?.playlists?.size ?: 0)
            put("trackCount", current?.tracks?.size ?: 0)
            lastError?.let { put("error", it) }
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putPlaylist(playlist: LibraryPlaylist) {
        put("playlistId", playlist.id)
        put("name", playlist.name)
        put("owner", playlist.owner)
        put("trackCount", playlist.trackIds?.size ?: playlist.trackCount)
        put("available", playlist.trackIds != null)
        playlist.trackIds?.let { ids -> putJsonArray("trackIds") { ids.forEach { add(JsonPrimitive(it)) } } }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putTrack(track: LibraryTrack) {
        put("trackId", track.id)
        put("title", track.title)
        put("artist", track.artists.joinToString(", "))
        putJsonArray("artists") { track.artists.forEach { add(JsonPrimitive(it)) } }
        put("album", track.album)
        put("durationMs", track.durationMs)
    }

    private fun JsonObject.text(name: String): String? {
        val value = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null
        if (!SpotifyLibraryJson.isSpotifyId(value)) throw EqRemoteApi.badRequest("$name ist keine Spotify-ID")
        return value
    }

    companion object {
        const val SYNC_TIMEOUT_MS = 5 * 60_000L
        const val WAKE_WAIT_MS = 6_000L
        const val WAKE_POLL_MS = 300L

        /** The server syncs by itself when online and the copy is older than this. */
        const val AUTO_SYNC_AGE_MS = 6 * 60 * 60_000L
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun notSignedIn() =
            ApiError(HttpStatusCode.Conflict, "NOT_SIGNED_IN", "Auf dem Pixel in AuraCurve bei Spotify anmelden (Player → Linked services)")

        fun sourceUnavailable(message: String) = ApiError(HttpStatusCode.Conflict, "SOURCE_UNAVAILABLE", message)
    }
}
