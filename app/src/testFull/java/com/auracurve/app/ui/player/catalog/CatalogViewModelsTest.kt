package com.auracurve.app.ui.player.catalog

import com.auracurve.app.integration.PlayerController
import com.auracurve.app.player.catalog.AudiusCatalog
import com.auracurve.app.player.catalog.AudiusGenre
import com.auracurve.app.player.catalog.RadioCatalog
import com.auracurve.app.player.catalog.RadioGenre
import com.auracurve.app.player.catalog.RadioStation
import com.auracurve.app.player.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    // Records playQueue calls; nothing else of the controller is used by these view models.
    private val played = mutableListOf<Pair<List<TrackItem>, Int>>()
    private val controller =
        Proxy.newProxyInstance(PlayerController::class.java.classLoader, arrayOf(PlayerController::class.java)) { _, method, args ->
            @Suppress("UNCHECKED_CAST")
            if (method.name == "playQueue") played.add(args[0] as List<TrackItem> to args[1] as Int)
            null
        } as PlayerController

    private fun station(uuid: String) =
        RadioStation(uuid, "Station $uuid", "https://stream.example.com/$uuid", null, listOf("hardstyle"), "DE", "MP3", 192, false, 10)

    private fun track(id: Long) =
        TrackItem(id = id, title = "Track $id", artist = "Artist", artworkUrl = null, streamUrl = "https://x/$id", durationMs = 1000)

    private class FakeRadio : RadioCatalog {
        val asked = mutableListOf<RadioGenre>()
        val clicks = mutableListOf<String>()
        var fail = false
        var answer: List<RadioStation> = emptyList()

        override suspend fun stations(genre: RadioGenre): List<RadioStation> {
            asked.add(genre)
            if (fail) throw IOException("offline")
            return answer
        }

        override suspend fun countClick(uuid: String) {
            clicks.add(uuid)
        }
    }

    private class FakeAudius : AudiusCatalog {
        val trendingAsked = mutableListOf<AudiusGenre>()
        val searches = mutableListOf<Pair<String, AudiusGenre>>()
        var answer: List<TrackItem> = emptyList()

        override suspend fun trending(genre: AudiusGenre): List<TrackItem> {
            trendingAsked.add(genre)
            return answer
        }

        override suspend fun search(
            query: String,
            genre: AudiusGenre,
        ): List<TrackItem> {
            searches.add(query to genre)
            return answer
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `radio loads only when shown and once`() =
        runTest(dispatcher) {
            val radio = FakeRadio().apply { answer = listOf(station("a")) }
            val vm = RadioViewModel(radio, controller)
            advanceUntilIdle()
            assertTrue("nothing is requested before the tab is shown", radio.asked.isEmpty())

            vm.ensureLoaded()
            advanceUntilIdle()
            vm.ensureLoaded()
            advanceUntilIdle()

            assertEquals(listOf(RadioGenre.ALL), radio.asked)
            assertEquals(1, vm.state.value.items.size)
        }

    @Test
    fun `choosing a genre reloads it`() =
        runTest(dispatcher) {
            val radio = FakeRadio()
            val vm = RadioViewModel(radio, controller)
            vm.ensureLoaded()
            advanceUntilIdle()

            vm.selectGenre(RadioGenre.HARDSTYLE)
            advanceUntilIdle()

            assertEquals(listOf(RadioGenre.ALL, RadioGenre.HARDSTYLE), radio.asked)
            assertEquals(RadioGenre.HARDSTYLE, vm.genre.value)
        }

    @Test
    fun `a failed load says why and a retry tries again`() =
        runTest(dispatcher) {
            val radio = FakeRadio().apply { fail = true }
            val vm = RadioViewModel(radio, controller)
            vm.ensureLoaded()
            advanceUntilIdle()
            assertEquals("offline", vm.state.value.error)

            radio.fail = false
            radio.answer = listOf(station("b"))
            vm.reload()
            advanceUntilIdle()

            assertNull(vm.state.value.error)
            assertFalse(vm.state.value.isLoading)
            assertEquals(1, vm.state.value.items.size)
        }

    @Test
    fun `playing a station queues it alone and counts the click`() =
        runTest(dispatcher) {
            val radio = FakeRadio()
            val vm = RadioViewModel(radio, controller)

            vm.play(station("c"))
            advanceUntilIdle()

            assertEquals(1, played.size)
            assertEquals(listOf(station("c").toTrack()), played.single().first)
            assertEquals(listOf("c"), radio.clicks)
        }

    @Test
    fun `audius shows trending, searches within the genre and plays the list from the tapped track`() =
        runTest(dispatcher) {
            val audius = FakeAudius().apply { answer = listOf(track(-1), track(-2), track(-3)) }
            val vm = AudiusViewModel(audius, controller)
            vm.ensureLoaded()
            advanceUntilIdle()
            assertEquals(listOf(AudiusGenre.ALL), audius.trendingAsked)

            vm.selectGenre(AudiusGenre.HARDSTYLE)
            vm.search("  kick  ")
            advanceUntilIdle()
            assertEquals(listOf("kick" to AudiusGenre.HARDSTYLE), audius.searches)

            vm.play(1)
            assertEquals(audius.answer to 1, played.single())

            vm.showTrending()
            advanceUntilIdle()
            assertEquals("", vm.query.value)
            assertEquals(AudiusGenre.HARDSTYLE, audius.trendingAsked.last())
        }
}
