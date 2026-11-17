package com.auracurve.app.remote.library

import com.auracurve.app.link.LibraryTrack
import com.auracurve.app.link.PlaylistInfo
import com.auracurve.app.link.SpotifyLibrary
import com.auracurve.app.remote.ApiError
import com.auracurve.app.remote.RemoteApiConfig
import com.auracurve.app.remote.SystemStatus
import com.auracurve.app.remote.remoteApi
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyLibraryRemoteApiTest {
    private val own = "37i9dQZF1DXcBWIGoYBM5M"
    private val foreign = "1111111111111111111111"
    private val songA = "4uLU6hMCjMI75M1A2tKUQC"
    private val songB = "7ouMYWpwJ422jRcDASZB7P"

    private class FakeReader(
        var signedIn: Boolean = true,
        var failWith: String? = null,
        val songs: Map<String, List<LibraryTrack>?> = emptyMap(),
        val infos: List<PlaylistInfo> = emptyList(),
    ) : SpotifyLibraryReader {
        override fun isSignedIn() = signedIn

        override suspend fun playlists(): List<PlaylistInfo> {
            failWith?.let { throw IOException(it) }
            return infos
        }

        override suspend fun tracks(playlistId: String) = songs[playlistId]
    }

    private class FakeStore(
        var stored: SpotifyLibrary? = null,
    ) : SpotifyLibraryStore {
        override suspend fun load() = stored

        override suspend fun save(library: SpotifyLibrary) {
            stored = library
        }
    }

    private class FakeStarter(
        var reason: String? = null,
        // How many playUri calls find no session before Spotify is up (-1: never).
        var sessionAfter: Int = 0,
    ) : SpotifyStarter {
        val played = mutableListOf<String>()
        var wakes = 0
        private var attempts = 0

        override fun unavailableReason() = reason

        override fun playUri(uri: String): Boolean {
            val ok = sessionAfter >= 0 && attempts++ >= sessionAfter
            if (ok) played += uri
            return ok
        }

        override fun wake() {
            wakes++
        }
    }

    private fun reader() =
        FakeReader(
            infos =
                listOf(
                    PlaylistInfo(own, "Auto", "Basti", 2),
                    PlaylistInfo(foreign, "Hits", "Spotify", 50),
                    PlaylistInfo(own, "Auto", "Basti", 2),
                ),
            songs =
                mapOf(
                    own to listOf(LibraryTrack(songA, "Eins", listOf("A", "B"), "LP", 1_000), LibraryTrack(songB, "Zwei", listOf("C"))),
                    foreign to null,
                ),
        )

    private fun TestScope.api(
        reader: FakeReader = reader(),
        store: FakeStore = FakeStore(),
        starter: FakeStarter = FakeStarter(),
        online: Boolean = true,
    ) = SpotifyLibraryRemoteApi(
        reader = reader,
        store = store,
        online = { online },
        starter = starter,
        mainDispatcher = StandardTestDispatcher(testScheduler),
        scope = this,
        clock = { 1_700_000_000_000 },
        zone = ZoneOffset.UTC,
        wakeWaitMs = 1_000,
        wakePollMs = 100,
    )

    private suspend fun errorCode(block: suspend () -> Unit): String =
        try {
            block()
            fail("expected an error")
            ""
        } catch (e: ApiError) {
            e.code
        }

    private fun body(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun JsonObject.text(name: String) = this[name]!!.jsonPrimitive.content

    @Test
    fun `without a copy the index is empty and says whether a sign-in exists`() =
        runTest {
            val index = api(reader = FakeReader(signedIn = false)).index()
            val status = index["status"]!!.jsonObject
            assertEquals(false, status["signedIn"]!!.jsonPrimitive.booleanOrNull)
            assertEquals("0", status.text("trackCount"))
            assertTrue(status["syncedAt"] is kotlinx.serialization.json.JsonNull)
            assertEquals(0, index["playlists"]!!.jsonArray.size)
        }

    @Test
    fun `a sync stores playlists in order, songs once and marks foreign playlists unavailable`() =
        runTest {
            val store = FakeStore()
            val api = api(store = store)
            val event = async { api.updates.first() }
            runCurrent()

            api.sync()
            advanceUntilIdle()

            val status = event.await()
            assertEquals("2023-11-14T22:13:20", status.text("syncedAt"))
            assertEquals("2", status.text("playlistCount"))
            assertEquals("2", status.text("trackCount"))
            assertNull(status["error"])
            assertEquals(2, store.stored!!.playlists.size)

            val playlists = api.index()["playlists"]!!.jsonArray.map { it.jsonObject }
            assertEquals(listOf(own, foreign), playlists.map { it.text("playlistId") })
            assertEquals(listOf(songA, songB), playlists[0]["trackIds"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals("true", playlists[0].text("available"))
            assertEquals("false", playlists[1].text("available"))
            assertEquals("50", playlists[1].text("trackCount"))
            assertNull(playlists[1]["trackIds"])

            val tracks = api.tracks()["tracks"]!!.jsonArray.map { it.jsonObject }
            assertEquals("A, B", tracks[0].text("artist"))
            assertEquals(listOf("A", "B"), tracks[0]["artists"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals("LP", tracks[0].text("album"))
        }

    @Test
    fun `sync is refused without sign-in, offline and while one runs`() =
        runTest {
            assertEquals("NOT_SIGNED_IN", errorCode { api(reader = FakeReader(signedIn = false)).sync() })
            assertEquals("OFFLINE", errorCode { api(online = false).sync() })
            val api = api()
            api.sync()
            assertEquals("BUSY", errorCode { api.sync() })
            advanceUntilIdle()
        }

    @Test
    fun `a failed sync keeps the old copy and reports the error`() =
        runTest {
            val old =
                SpotifyLibrary(
                    5,
                    listOf(
                        com.auracurve.app.link
                            .LibraryPlaylist(own, "Alt", "", 0, emptyList()),
                    ),
                    emptyMap(),
                )
            val api = api(reader = FakeReader(failWith = "Spotify antwortet nicht"), store = FakeStore(old))
            val event = async { api.updates.first() }
            runCurrent()

            api.sync()
            advanceUntilIdle()

            assertEquals("Spotify antwortet nicht", event.await().text("error"))
            assertEquals(
                "Alt",
                api
                    .index()["playlists"]!!
                    .jsonArray[0]
                    .jsonObject
                    .text("name"),
            )
        }

    @Test
    fun `automatic sync only when online, signed in and the copy is old`() =
        runTest {
            val fresh = SpotifyLibrary(1_700_000_000_000 - 1_000, emptyList(), emptyMap())
            assertFalse(api(store = FakeStore(fresh)).syncIfDue())
            assertFalse(api(online = false).syncIfDue())
            assertFalse(api(reader = FakeReader(signedIn = false)).syncIfDue())
            val store = FakeStore()
            assertTrue(api(store = store).syncIfDue())
            assertEquals(2, store.stored!!.playlists.size)
        }

    private suspend fun synced(
        scope: TestScope,
        starter: FakeStarter,
    ): SpotifyLibraryRemoteApi {
        val api = scope.api(starter = starter)
        api.syncIfDue()
        return api
    }

    @Test
    fun `play turns only ids from the copy into spotify addresses`() =
        runTest {
            val starter = FakeStarter()
            val api = synced(this, starter)

            api.play(body("""{"playlistId":"$own","trackId":"$songB"}"""))
            api.play(body("""{"playlistId":"$own"}"""))

            assertEquals(listOf("spotify:track:$songB", "spotify:playlist:$own"), starter.played)
            assertEquals("BAD_REQUEST", errorCode { api.play(body("{}")) })
            assertEquals("BAD_REQUEST", errorCode { api.play(body("""{"playlistId":"spotify:playlist:x"}""")) })
            assertEquals("BAD_REQUEST", errorCode { api.play(body("""{"playlistId":"$own","trackId":"../../x"}""")) })
            assertEquals("PLAYLIST_NOT_FOUND", errorCode { api.play(body("""{"playlistId":"2222222222222222222222"}""")) })
            assertEquals("TRACK_NOT_FOUND", errorCode { api.play(body("""{"playlistId":"$foreign","trackId":"$songA"}""")) })
        }

    @Test
    fun `without a spotify session the media button wakes it and the address follows`() =
        runTest {
            val starter = FakeStarter(sessionAfter = 3)
            val api = synced(this, starter)

            api.play(body("""{"playlistId":"$own","trackId":"$songA"}"""))

            assertEquals(1, starter.wakes)
            assertEquals(listOf("spotify:track:$songA"), starter.played)
        }

    @Test
    fun `spotify that never comes up or no notification access is a conflict`() =
        runTest {
            val never = synced(this, FakeStarter(sessionAfter = -1))
            assertEquals("SOURCE_UNAVAILABLE", errorCode { never.play(body("""{"playlistId":"$own"}""")) })
            val noAccess = synced(this, FakeStarter(reason = "Benachrichtigungszugriff fehlt"))
            assertEquals("SOURCE_UNAVAILABLE", errorCode { noAccess.play(body("""{"playlistId":"$own"}""")) })
        }

    @Test
    fun `the routes need the token and answer in the contract's shape`() =
        testApplication {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val api =
                SpotifyLibraryRemoteApi(
                    reader = reader(),
                    store = FakeStore(),
                    online = { false },
                    starter = FakeStarter(),
                    mainDispatcher = Dispatchers.Unconfined,
                    scope = scope,
                )
            application {
                remoteApi(
                    RemoteApiConfig(
                        appVersion = "t",
                        token = { "tok" },
                        systemStatus = { SystemStatus(1, false, "NONE", "SPEAKER", "S") },
                        events = emptyFlow(),
                        webRoot = { null },
                        addressAllowed = { _, _ -> true },
                        library = api,
                    ),
                )
            }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v0/library/spotify").status)
            val index = client.get("/api/v0/library/spotify") { header(HttpHeaders.Authorization, "Bearer tok") }
            assertEquals(HttpStatusCode.OK, index.status)
            assertTrue(Json.parseToJsonElement(index.bodyAsText()).jsonObject.containsKey("playlists"))
            val tracks = client.get("/api/v0/library/spotify/tracks") { header(HttpHeaders.Authorization, "Bearer tok") }
            assertEquals(HttpStatusCode.OK, tracks.status)
            val sync = client.post("/api/v0/library/spotify/sync") { header(HttpHeaders.Authorization, "Bearer tok") }
            assertEquals(HttpStatusCode.Conflict, sync.status)
            assertTrue(sync.bodyAsText().contains("OFFLINE"))
            val play =
                client.post("/api/v0/library/spotify/play") {
                    header(HttpHeaders.Authorization, "Bearer tok")
                    setBody("""{"playlistId":"$own"}""")
                }
            assertEquals(HttpStatusCode.NotFound, play.status)
            assertTrue(play.bodyAsText().contains("PLAYLIST_NOT_FOUND"))
        }
}
