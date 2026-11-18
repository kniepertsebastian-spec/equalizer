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
import java.util.concurrent.atomic.AtomicInteger

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

/** What Spotify's media session reports right now. [mediaId] is a spotify: address when Spotify sets one. */
data class SpotifyNow(
    val mediaId: String?,
    val title: String,
    val playing: Boolean,
)

/** Hands a spotify: address to the Spotify app in user 0. Called on the main thread. */
interface SpotifyStarter {
    /** Null when Spotify can be reached at all, else why not (German). */
    fun unavailableReason(): String?

    /**
     * Sends the address to Spotify's session. True when there was a session to send it to,
     * false when there is no session yet. Spotify may still ignore it: [play] checks [nowPlaying].
     */
    fun playUri(uri: String): Boolean

    /** Wakes Spotify without a session (a media button press), so a session comes up. */
    fun wake()

    /** Spotify's current song, null without a session. */
    fun nowPlaying(): SpotifyNow?

    /**
     * Opens the address in the Spotify app itself; false when Android did not take the request.
     * Android may still block the start without telling (background activity start): see
     * [mayOpenFromBackground].
     */
    fun openUri(uri: String): Boolean

    /**
     * Whether Android lets the app open Spotify while it is in the background ("Display over other
     * apps" granted). Without it [openUri] only works while AuraCurve is on screen.
     */
    fun mayOpenFromBackground(): Boolean = true

    /**
     * Spotify's session as one log line for the device test (state, position, actions, media id,
     * title). Never contains tokens. Only compared for changes, not parsed.
     */
    fun describe(): String = nowPlaying()?.toString() ?: "no session"
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
    private val confirmWaitMs: Long = CONFIRM_WAIT_MS,
    private val openWaitMs: Long = OPEN_WAIT_MS,
    private val playBudgetMs: Long = PLAY_BUDGET_MS,
    /** Monotonic milliseconds for the play deadline and the log; tests pass virtual time. */
    private val elapsedClock: () -> Long = { System.nanoTime() / 1_000_000 },
    /** One line per step of a play attempt (device test). The full build logs it to logcat. */
    private val playLog: (String) -> Unit = {},
) {
    private val attempts = AtomicInteger()
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
        val wanted = (if (trackId != null) listOf(trackId) else playlist.trackIds.orEmpty()).toSet()
        val songs = current()?.tracks.orEmpty()
        val titles = wanted.mapNotNull { id -> songs[id]?.let { normalizeTitle(it.title) } }.toSet()
        val attempt = Attempt(attempts.incrementAndGet(), elapsedClock())
        val offline = !online()
        val mayOpen = withContext(mainDispatcher) { starter.mayOpenFromBackground() }
        attempt.log(
            "start uri=$uri extras=none songs=${wanted.size} online=${!offline} mayOpen=$mayOpen spotify=${describeOnMain()}",
        )
        try {
            // For a playlist, a song of it that was already playing does not show that Spotify followed.
            val before = if (trackId == null) withContext(mainDispatcher) { starter.nowPlaying() }?.takeIf { it.playing } else null
            val handedOver = handOver(uri, attempt)
            // Spotify can take the address without playing it (it ignores commands from some apps):
            // only a song from the request counts. Otherwise open it in the Spotify app itself.
            if (handedOver) {
                val wait = minOf(confirmWaitMs, attempt.remaining())
                if (confirmed(wanted, titles, before, wait, attempt, "session")) return attempt.done("ok via playFromUri")
            }
            val opened = withContext(mainDispatcher) { starter.openUri(uri) }
            attempt.log("openUri accepted=$opened")
            // The open step gets what is left of the budget, so the answer stays inside the remote's limit.
            if (opened && confirmed(wanted, titles, before, minOf(openWaitMs, attempt.remaining()), attempt, "open")) {
                return attempt.done("ok via openUri")
            }
            val now = withContext(mainDispatcher) { starter.nowPlaying() }
            val error =
                when {
                    // Android dropped the open request without an error: name the setting, not Spotify.
                    !mayOpen -> openNotAllowed()
                    handedOver || opened -> notConfirmed(now, offline)
                    else -> sourceUnavailable("Spotify startet nicht")
                }
            attempt.done("failed ${error.code}: handedOver=$handedOver opened=$opened spotify=${describeOnMain()}")
            throw error
        } catch (e: ApiError) {
            if (!attempt.finished) attempt.done("failed ${e.code}: ${e.message}")
            throw e
        }
    }

    /** One play request in the log: number, start time and whether its last line was written. */
    private inner class Attempt(
        val number: Int,
        val startMs: Long,
    ) {
        var finished = false

        fun elapsed() = elapsedClock() - startMs

        fun remaining() = (playBudgetMs - elapsed()).coerceAtLeast(0)

        fun log(text: String) = playLog("#$number +${elapsed()}ms $text")

        fun done(text: String) {
            finished = true
            log("$text total=${elapsed()}ms")
        }
    }

    private suspend fun describeOnMain(): String = withContext(mainDispatcher) { describeNow() }

    /** On the main thread: the log line must never break a play request. */
    private fun describeNow(): String = runCatching { starter.describe() }.getOrDefault("?")

    /**
     * Spotify had the command but no song of the request came up. Says only what the app knows:
     * what Spotify reports now and whether the phone is offline; the cause itself is unknown.
     */
    private fun notConfirmed(
        now: SpotifyNow?,
        offline: Boolean,
    ): ApiError {
        val facts =
            listOfNotNull(
                when {
                    now == null -> "keine Spotify-Sitzung"
                    !now.playing -> "Spotify spielt nichts"
                    now.title.isNotBlank() -> "Spotify meldet „${now.title}“"
                    else -> "Spotify meldet einen anderen Titel"
                },
                "Pixel ohne Internet".takeIf { offline },
            )
        return ApiError(
            HttpStatusCode.Conflict,
            "PLAY_NOT_CONFIRMED",
            "Spotify hat den Befehl nicht ausgeführt (${facts.joinToString("; ")})",
        )
    }

    private suspend fun confirmed(
        wanted: Set<String>,
        titles: Set<String>,
        before: SpotifyNow?,
        waitMs: Long,
        attempt: Attempt,
        phase: String,
    ): Boolean {
        val until = attempt.elapsed() + waitMs
        var last: String? = null
        while (true) {
            val (now, line) = withContext(mainDispatcher) { starter.nowPlaying() to describeNow() }
            if (line != last) {
                attempt.log("$phase spotify=$line")
                last = line
            }
            if (now != null && now.playing && now != before && matches(now, wanted, titles)) return true
            if (attempt.elapsed() >= until) {
                attempt.log("$phase no requested song after ${waitMs}ms")
                return false
            }
            delay(wakePollMs)
        }
    }

    private fun matches(
        now: SpotifyNow,
        wanted: Set<String>,
        titles: Set<String>,
    ): Boolean {
        val id = now.mediaId?.substringAfterLast(':')
        if (id != null && id in wanted) return true
        return normalizeTitle(now.title).let { it.isNotEmpty() && it in titles }
    }

    /** Sends the address to Spotify's session, waking Spotify first if needed. False when no session came up. */
    private suspend fun handOver(
        uri: String,
        attempt: Attempt,
    ): Boolean {
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
        if (started) {
            attempt.log("playFromUri sent to the running session")
            return true
        }
        // Spotify had no session: the media button starts it; hand over the address once it is up.
        attempt.log("no session, media button sent")
        val until = attempt.elapsed() + minOf(wakeWaitMs, attempt.remaining())
        while (attempt.elapsed() < until) {
            delay(wakePollMs)
            if (withContext(mainDispatcher) { starter.playUri(uri) }) {
                attempt.log("session up, playFromUri sent")
                return true
            }
        }
        attempt.log("still no session after waking")
        return false
    }

    private fun normalizeTitle(title: String): String = title.trim().lowercase()

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

        /**
         * How long Spotify gets to report a requested song, after the session command and after
         * opening it. Spotify ignores the session command from other apps on the device test
         * (playFromUri, 18 Nov 2026), so it gets only a short look before opening.
         */
        const val CONFIRM_WAIT_MS = 1_200L
        const val OPEN_WAIT_MS = 4_000L

        /**
         * The most a play request may take (wake, session command, opening): the open step gets
         * only what is left. The remote waits 18 s (LIBRARY_PLAY_TIMEOUT_MS), so 12 s leave room
         * for the network and a busy main thread.
         */
        const val PLAY_BUDGET_MS = 12_000L

        /** The server syncs by itself when online and the copy is older than this. */
        const val AUTO_SYNC_AGE_MS = 6 * 60 * 60_000L
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun notSignedIn() =
            ApiError(HttpStatusCode.Conflict, "NOT_SIGNED_IN", "Auf dem Pixel in AuraCurve bei Spotify anmelden (Player → Linked services)")

        fun openNotAllowed() =
            ApiError(
                HttpStatusCode.Conflict,
                "OPEN_NOT_ALLOWED",
                "Android lässt AuraCurve Spotify nicht öffnen. Auf dem Pixel: AuraCurve → Player → Linked services → " +
                    "Remote control → „Allow opening Spotify“",
            )

        fun sourceUnavailable(message: String) = ApiError(HttpStatusCode.Conflict, "SOURCE_UNAVAILABLE", message)
    }
}
