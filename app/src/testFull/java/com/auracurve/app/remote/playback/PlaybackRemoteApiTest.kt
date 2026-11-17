package com.auracurve.app.remote.playback

import com.auracurve.app.remote.ApiError
import com.auracurve.app.remote.RemoteApiConfig
import com.auracurve.app.remote.SystemStatus
import com.auracurve.app.remote.remoteApi
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlaybackRemoteApiTest {
    private class FakeBackend(
        override val source: RemoteSource,
        var reason: String? = null,
        var playing: Boolean = false,
        var title: String = "Track",
        val supportsModes: Boolean = true,
    ) : PlaybackBackend {
        val calls = mutableListOf<String>()

        override fun unavailableReason() = reason

        override fun snapshot(volume: VolumeLevel) =
            NowPlayingSnapshot(
                source,
                title,
                "Artist",
                playing,
                1_000,
                200_000,
                if (supportsModes) false else null,
                if (supportsModes) "OFF" else null,
                volume,
            )

        override fun isPlaying() = playing

        override fun playPause() {
            calls += "playpause"
            playing = !playing
        }

        override fun pause() {
            calls += "pause"
            playing = false
        }

        override fun next() {
            calls += "next"
        }

        override fun previous() {
            calls += "prev"
        }

        override fun setShuffle(enabled: Boolean): Boolean = supportsModes.also { if (it) calls += "shuffle=$enabled" }

        override fun setRepeat(mode: String): Boolean = supportsModes.also { if (it) calls += "repeat=$mode" }
    }

    private class FakeVolume(
        var level: Int = 8,
        val max: Int = 15,
    ) : VolumeBackend {
        override fun current() = VolumeLevel(level, max)

        override fun set(level: Int) {
            this.level = level
        }

        override val changes: Flow<VolumeLevel> = MutableStateFlow(VolumeLevel(level, max))
    }

    private val player = FakeBackend(RemoteSource.PLAYER)
    private val spotify = FakeBackend(RemoteSource.SPOTIFY_MAIN, supportsModes = false)
    private val volume = FakeVolume()
    private val api = PlaybackRemoteApi(player, spotify, volume, Dispatchers.Unconfined, pollMs = 1)

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun expectError(
        code: String,
        block: suspend () -> Unit,
    ) = runBlocking {
        try {
            block()
            fail("expected $code")
        } catch (e: ApiError) {
            assertEquals(code, e.code)
        }
    }

    @Test
    fun `now playing in the contract's shape, without shuffle and repeat where unknown`() {
        val withModes = player.snapshot(VolumeLevel(3, 15)).toJson()
        assertEquals(
            setOf("source", "title", "artist", "isPlaying", "positionMs", "durationMs", "shuffle", "repeat", "volume"),
            withModes.keys,
        )
        val without = spotify.snapshot(VolumeLevel(3, 15)).toJson()
        assertFalse(without.containsKey("shuffle"))
        assertFalse(without.containsKey("repeat"))
        assertEquals("SPOTIFY_MAIN", without["source"]!!.jsonPrimitive.content)
    }

    @Test
    fun `session position runs on while playing and stays within the track`() {
        assertEquals(10_000L, NowPlayingSnapshot.sessionPosition(10_000, 1_000, 1f, playing = false, nowMs = 5_000, durationMs = 60_000))
        assertEquals(14_000L, NowPlayingSnapshot.sessionPosition(10_000, 1_000, 1f, playing = true, nowMs = 5_000, durationMs = 60_000))
        assertEquals(60_000L, NowPlayingSnapshot.sessionPosition(59_000, 1_000, 1f, playing = true, nowMs = 9_000, durationMs = 60_000))
        assertEquals(0L, NowPlayingSnapshot.sessionPosition(-1, 0, 1f, playing = true, nowMs = 9_000, durationMs = 60_000))
        assertTrue(NowPlayingSnapshot.isPlayingState(NowPlayingSnapshot.STATE_BUFFERING))
        assertFalse(NowPlayingSnapshot.isPlayingState(2))
    }

    @Test
    fun `volume levels and steps are clamped to the device range`() =
        runBlocking {
            assertEquals(
                9,
                api
                    .setVolume(json("""{"level":9}"""))["volume"]!!
                    .jsonObject["level"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
            assertEquals(
                15,
                api
                    .setVolume(json("""{"level":99}"""))["volume"]!!
                    .jsonObject["level"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
            assertEquals(
                13,
                api
                    .setVolume(json("""{"step":-2}"""))["volume"]!!
                    .jsonObject["level"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
            api.setVolume(json("""{"level":-4}"""))
            assertEquals(0, volume.level)
            expectError("BAD_REQUEST") { api.setVolume(json("{}")) }
            expectError("BAD_REQUEST") { api.setVolume(json("""{"level":"9"}""")) }
        }

    @Test
    fun `commands act on the active source`() =
        runBlocking {
            api.playPause()
            api.next()
            api.previous()
            api.setShuffle(json("""{"enabled":true}"""))
            api.setRepeat(json("""{"mode":"ONE"}"""))
            assertEquals(listOf("playpause", "next", "prev", "shuffle=true", "repeat=ONE"), player.calls)
            assertTrue(spotify.calls.isEmpty())
            expectError("UNKNOWN_VALUE") { api.setRepeat(json("""{"mode":"TWICE"}""")) }
            expectError("BAD_REQUEST") { api.setShuffle(json("{}")) }
        }

    @Test
    fun `switching the source pauses the other one`() =
        runBlocking {
            player.playing = true
            api.setSource(json("""{"source":"SPOTIFY_MAIN"}"""))
            assertEquals(RemoteSource.SPOTIFY_MAIN, api.activeSource.value)
            assertEquals(listOf("pause"), player.calls)

            api.next()
            assertEquals(listOf("next"), spotify.calls)
            expectError("SOURCE_UNAVAILABLE") { api.setShuffle(json("""{"enabled":true}""")) }

            expectError("SOURCE_UNAVAILABLE") { api.setSource(json("""{"source":"SPOTIFY_SECOND"}""")) }
            expectError("UNKNOWN_VALUE") { api.setSource(json("""{"source":"RADIO"}""")) }
        }

    @Test
    fun `without notification access spotify answers 409`() =
        runBlocking {
            spotify.reason = "Benachrichtigungszugriff fehlt"
            expectError("SOURCE_UNAVAILABLE") { api.setSource(json("""{"source":"SPOTIFY_MAIN"}""")) }
            assertEquals(RemoteSource.PLAYER, api.activeSource.value)
        }

    @Test
    fun `the source that starts playing becomes the active one`() =
        runBlocking {
            assertEquals(
                "PLAYER",
                api
                    .changes()
                    .first()["source"]!!
                    .jsonPrimitive.content,
            )
            spotify.playing = true
            val events = api.changes().take(1).toList()
            assertEquals(RemoteSource.SPOTIFY_MAIN, api.activeSource.value)
            assertEquals("SPOTIFY_MAIN", events.single()["source"]!!.jsonPrimitive.content)
        }

    @Test
    fun `system source answers 202 and a missing access 409 over http`() =
        testApplication {
            application {
                remoteApi(
                    RemoteApiConfig(
                        appVersion = "t",
                        token = { "tok" },
                        systemStatus = { SystemStatus(1, false, "NONE", "SPEAKER", "S") },
                        events = emptyFlow(),
                        webRoot = { null },
                        addressAllowed = { _, _ -> true },
                        playback = api,
                    ),
                )
            }
            val accepted =
                client.post("/api/v0/system/source") {
                    header(HttpHeaders.Authorization, "Bearer tok")
                    setBody("""{"source":"SPOTIFY_MAIN"}""")
                }
            assertEquals(HttpStatusCode.Accepted, accepted.status)
            assertEquals("true", json(accepted.bodyAsText())["accepted"]!!.jsonPrimitive.content)

            spotify.reason = "Benachrichtigungszugriff fehlt"
            val refused = client.post("/api/v0/playback/playpause") { header(HttpHeaders.Authorization, "Bearer tok") }
            assertEquals(HttpStatusCode.Conflict, refused.status)
            val error: JsonObject = json(refused.bodyAsText())["error"]!!.jsonObject
            assertEquals("SOURCE_UNAVAILABLE", error["code"]!!.jsonPrimitive.content)
            assertEquals("Benachrichtigungszugriff fehlt", error["message"]!!.jsonPrimitive.content)
        }
}
