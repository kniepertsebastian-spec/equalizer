package com.auracurve.app.remote.playback

import com.auracurve.app.remote.ApiError
import com.auracurve.app.remote.EqRemoteApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Playback, volume and source of the remote-control API (Pixel-Car module 04c).
 *
 * The active source decides what playpause/next/prev/shuffle/repeat act on. It is set with
 * `system/source` and also follows the phone by itself: when the other source starts playing, the
 * last active one wins (proposal in the M04 handoff, kept). Switching by API pauses the other source.
 */
class PlaybackRemoteApi(
    private val player: PlaybackBackend,
    private val spotify: PlaybackBackend,
    private val volume: VolumeBackend,
    private val mainDispatcher: CoroutineDispatcher,
    private val pollMs: Long = POLL_MS,
) {
    private val _activeSource = MutableStateFlow(RemoteSource.PLAYER)
    val activeSource: StateFlow<RemoteSource> = _activeSource.asStateFlow()

    private fun backend(source: RemoteSource = _activeSource.value): PlaybackBackend =
        when (source) {
            RemoteSource.PLAYER -> player
            RemoteSource.SPOTIFY_MAIN -> spotify
            RemoteSource.SPOTIFY_SECOND -> throw sourceUnavailable("Spotify im zweiten Benutzer gibt es in v0 nicht")
        }

    suspend fun nowPlaying(): JsonObject = onMain { usable(backend()).let { current(it).toJson() } }

    suspend fun playPause(): JsonObject = command { it.playPause() }

    suspend fun next(): JsonObject = command { it.next() }

    suspend fun previous(): JsonObject = command { it.previous() }

    suspend fun setShuffle(body: JsonObject): JsonObject {
        val enabled =
            (body["enabled"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: throw EqRemoteApi.badRequest("enabled fehlt")
        return command { if (!it.setShuffle(enabled)) throw sourceUnavailable("Diese Quelle kann Zufall nicht umschalten") }
    }

    suspend fun setRepeat(body: JsonObject): JsonObject {
        val mode =
            (body["mode"] as? JsonPrimitive)?.contentOrNull?.takeIf { it in REPEAT_MODES } ?: throw EqRemoteApi.unknown("Unbekannter Modus")
        return command { if (!it.setRepeat(mode)) throw sourceUnavailable("Diese Quelle kann Wiederholen nicht umschalten") }
    }

    /** `{"level":n}` (absolute) or `{"step":n}`; clamped to 0 … max. */
    suspend fun setVolume(body: JsonObject): JsonObject {
        val level = body.int("level")
        val step = body.int("step")
        if (level == null && step == null) throw EqRemoteApi.badRequest("level oder step fehlt")
        return onMain {
            val now = volume.current()
            volume.set(now.target(level, step))
            buildJsonObject { put("volume", volume.current().toJson()) }
        }
    }

    /**
     * `system/source`: PLAYER or SPOTIFY_MAIN; the other source is paused. Answered with 202 by the
     * route; the result comes as source.changed.
     */
    suspend fun setSource(body: JsonObject) {
        val source =
            RemoteSource.entries.firstOrNull { it.name == (body["source"] as? JsonPrimitive)?.contentOrNull }
                ?: throw EqRemoteApi.unknown("Unbekannte Quelle")
        onMain {
            val target = usable(backend(source))
            val other = if (source == RemoteSource.PLAYER) spotify else player
            if (other.unavailableReason() == null && other.isPlaying()) other.pause()
            _activeSource.value = target.source
        }
    }

    /**
     * The now-playing state whenever the track or play/pause changes (playback.changed), checked
     * every [pollMs]. Also moves the active source to the one that started playing.
     */
    fun changes(): Flow<JsonObject> =
        flow {
            var wasPlaying = mapOf(RemoteSource.PLAYER to false, RemoteSource.SPOTIFY_MAIN to false)
            while (true) {
                val result =
                    onMain {
                        val playing = mapOf(RemoteSource.PLAYER to player.playingNow(), RemoteSource.SPOTIFY_MAIN to spotify.playingNow())
                        val started = playing.entries.firstOrNull { it.value && wasPlaying[it.key] == false }?.key
                        if (started != null && started != _activeSource.value) _activeSource.value = started
                        wasPlaying = playing
                        val active = backend()
                        if (active.unavailableReason() == null) current(active) else null
                    }
                if (result != null) emit(result)
                delay(pollMs)
            }
        }.distinctUntilChanged { a, b -> a.changeKey == b.changeKey }
            .map { it.toJson() }

    /** volume.changed. */
    fun volumeChanges(): Flow<JsonObject> = volume.changes.distinctUntilChanged().map { it.toJson() }

    private fun PlaybackBackend.playingNow(): Boolean = unavailableReason() == null && isPlaying()

    private fun current(backend: PlaybackBackend): NowPlayingSnapshot {
        val vol = volume.current()
        return backend.snapshot(vol) ?: NowPlayingSnapshot(backend.source, "", "", false, 0, 0, null, null, vol)
    }

    private suspend fun command(action: (PlaybackBackend) -> Unit): JsonObject =
        onMain {
            val target = usable(backend())
            action(target)
            current(target).toJson()
        }

    private fun usable(backend: PlaybackBackend): PlaybackBackend {
        backend.unavailableReason()?.let { throw sourceUnavailable(it) }
        return backend
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(mainDispatcher) { block() }

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    companion object {
        val REPEAT_MODES = setOf("OFF", "ALL", "ONE")
        const val POLL_MS = 1_000L

        fun sourceUnavailable(message: String) = ApiError(HttpStatusCode.Conflict, "SOURCE_UNAVAILABLE", message)
    }
}
