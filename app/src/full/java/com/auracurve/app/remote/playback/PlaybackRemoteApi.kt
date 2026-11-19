package com.auracurve.app.remote.playback

import com.auracurve.app.remote.ApiError
import com.auracurve.app.remote.EqRemoteApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val settleMs: Long = SETTLE_MS,
    private val settlePollMs: Long = SETTLE_POLL_MS,
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

    suspend fun playPause(): JsonObject = command(settle = true) { it.playPause() }

    suspend fun next(): JsonObject = command(settle = true) { it.next() }

    suspend fun previous(): JsonObject = command(settle = true) { it.previous() }

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

    /** Module 11, F1 (very hot): stops whatever plays, on both sources. */
    suspend fun pauseAll() =
        onMain {
            listOf(player, spotify).forEach { if (it.unavailableReason() == null && it.isPlaying()) it.pause() }
        }

    /** For the feed's play-all: the built-in player becomes the active source, Spotify is paused. */
    suspend fun usePlayer() =
        onMain {
            if (spotify.unavailableReason() == null && spotify.isPlaying()) spotify.pause()
            _activeSource.value = RemoteSource.PLAYER
        }

    /** For the Spotify library: Spotify (user 0) becomes the active source, the built-in player is paused. */
    suspend fun useSpotify() =
        onMain {
            if (player.unavailableReason() == null && player.isPlaying()) player.pause()
            _activeSource.value = RemoteSource.SPOTIFY_MAIN
        }

    /**
     * The now-playing state whenever the track or play/pause changes (playback.changed): checked
     * at once on a backend's change signal (session callbacks) and otherwise every [pollMs], which
     * covers a source without callbacks (e.g. notification access granted later). Also moves the
     * active source to the one that started playing.
     */
    fun changes(): Flow<JsonObject> =
        channelFlow {
            val wake = Channel<Unit>(Channel.CONFLATED)
            launch { merge(player.changeSignals, spotify.changeSignals).collect { wake.trySend(Unit) } }
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
                if (result != null) send(result)
                withTimeoutOrNull(pollMs) { wake.receive() }
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

    /**
     * Runs [action] on the active source. With [settle], answers with the state after the command:
     * Spotify (and the player) apply it a moment later, so the state read right away would still
     * be the old one and the remote would show it until the next playback.changed. Waits at most
     * [settleMs]; a command that changes nothing (next on the last song) is answered then.
     */
    private suspend fun command(
        settle: Boolean = false,
        action: (PlaybackBackend) -> Unit,
    ): JsonObject {
        val (target, before) =
            onMain {
                val target = usable(backend())
                val before = current(target).changeKey
                action(target)
                target to before
            }
        var waited = 0L
        while (true) {
            val now = onMain { current(target) }
            if (!settle || now.changeKey != before || waited >= settleMs) return now.toJson()
            delay(settlePollMs)
            waited += settlePollMs
        }
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

        /** How long play/pause, next and previous wait for the source to report the new state. */
        const val SETTLE_MS = 600L
        const val SETTLE_POLL_MS = 50L

        fun sourceUnavailable(message: String) = ApiError(HttpStatusCode.Conflict, "SOURCE_UNAVAILABLE", message)
    }
}
