package com.auracurve.app.remote.feed

import com.auracurve.app.discovery.DiscoveryRepository
import com.auracurve.app.discovery.DiscoveryState
import com.auracurve.app.discovery.DiscoveryTrack
import com.auracurve.app.discovery.GenreMode
import com.auracurve.app.discovery.RefreshResult
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.remote.ApiError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FeedRemoteApiTest {
    private class FakeRepository(
        initial: DiscoveryState,
    ) : DiscoveryRepository {
        private val flow = MutableStateFlow(initial)
        override val state: Flow<DiscoveryState> = flow

        override suspend fun current() = flow.value

        override suspend fun update(transform: (DiscoveryState) -> DiscoveryState) {
            flow.value = transform(flow.value)
        }
    }

    private fun track(
        id: Long,
        genre: String = "",
    ) = DiscoveryTrack(SavedTrack(id, "Title $id", "Artist", durationMs = 180_000), "Artist", 1_000, genre)

    private val state =
        DiscoveryState(
            artists = listOf("Artist", "Other"),
            playlist = listOf(track(42, "uptempo"), track(7)),
            genreMode = GenreMode.MANUAL,
            genres = listOf("uptempo"),
            updatedAtMs = 1_700_000_000_000,
        )

    private fun errorCode(block: () -> Unit): String =
        try {
            block()
            fail("expected an error")
            ""
        } catch (e: ApiError) {
            e.code
        }

    @Test
    fun `the feed in the contract's shape with stable track ids`() =
        runTest {
            val api = FeedRemoteApi(FakeRepository(state), { RefreshResult.Skipped }, { true }, {}, this, zone = ZoneOffset.UTC)
            val feed = api.feed()
            val status = feed["status"]!!.jsonObject
            assertEquals(2, status["trackCount"]!!.jsonPrimitive.content.toInt())
            assertEquals("MANUAL", status["genreMode"]!!.jsonPrimitive.content)
            assertEquals(
                "uptempo",
                status["genres"]!!
                    .jsonArray
                    .single()
                    .jsonPrimitive.content,
            )
            assertEquals("2023-11-14T22:13:20", status["lastPullAt"]!!.jsonPrimitive.content)
            val tracks = feed["tracks"]!!.jsonArray.map { it.jsonObject }
            assertEquals(listOf("42", "7"), tracks.map { it["trackId"]!!.jsonPrimitive.content })
            assertEquals("uptempo", tracks[0]["genre"]!!.jsonPrimitive.content)
            assertFalse(tracks[1].containsKey("genre"))
            assertEquals(listOf("Artist", "Other"), feed["artists"]!!.jsonArray.map { it.jsonObject["artistId"]!!.jsonPrimitive.content })
        }

    @Test
    fun `a pull reports its result as feed updated`() =
        runTest {
            val api = FeedRemoteApi(FakeRepository(state), { RefreshResult.Updated(5, 2) }, { true }, {}, this)
            val event = CompletableDeferred<kotlinx.serialization.json.JsonObject>()
            val collector = launch { event.complete(api.updates.first()) }
            runCurrent()
            api.pull()
            val result = event.await()
            assertEquals(5, result["added"]!!.jsonPrimitive.content.toInt())
            assertEquals(2, result["filteredByGenre"]!!.jsonPrimitive.content.toInt())
            assertNull(result["error"])
            collector.cancel()
        }

    @Test
    fun `the heat protection refuses a pull`() =
        runTest {
            assertEquals(
                "HEAT_PROTECTION",
                errorCode { FeedRemoteApi(FakeRepository(state), { RefreshResult.Skipped }, { true }, {}, this, paused = { true }).pull() },
            )
        }

    @Test
    fun `offline and a running pull are refused, a hanging pull times out`() =
        runTest {
            assertEquals(
                "OFFLINE",
                errorCode { FeedRemoteApi(FakeRepository(state), { RefreshResult.Skipped }, { false }, {}, this).pull() },
            )

            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val api = FeedRemoteApi(FakeRepository(state), { awaitCancellation() }, { true }, {}, scope)
            val event = CompletableDeferred<kotlinx.serialization.json.JsonObject>()
            scope.launch { event.complete(api.updates.first()) }
            runCurrent()
            api.pull()
            assertEquals("BUSY", errorCode { api.pull() })

            advanceTimeBy(FeedRemoteApi.PULL_TIMEOUT_MS + 1)
            runCurrent()
            assertEquals("Zeitüberschreitung", event.await()["error"]!!.jsonPrimitive.content)
            api.pull() // free again after the timeout
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        }

    @Test
    fun `play all plays the feed from the start and refuses an empty one`() =
        runTest {
            var played: List<TrackItem> = emptyList()
            val api = FeedRemoteApi(FakeRepository(state), { RefreshResult.Skipped }, { true }, { played = it }, this)
            assertEquals(2, api.playAll())
            assertEquals(listOf(42L, 7L), played.map { it.id })
            assertNull(played.first().streamUrl)

            val empty = FeedRemoteApi(FakeRepository(DiscoveryState()), { RefreshResult.Skipped }, { true }, {}, this)
            try {
                empty.playAll()
                fail()
            } catch (e: ApiError) {
                assertEquals("TRACK_NOT_FOUND", e.code)
            }
        }
}
