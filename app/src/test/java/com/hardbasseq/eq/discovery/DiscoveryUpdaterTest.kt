package com.hardbasseq.eq.discovery

import com.hardbasseq.eq.integration.LoadResult
import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryUpdaterTest {
    private val day = 86_400_000L

    // 2026-09-28, a Monday.
    private val monday = 20_724L * day
    private val repository = FakeDiscoveryRepository()
    private val source = FakeDiscoverySource()
    private val updater = DiscoveryUpdater(repository, source)

    private fun upload(
        id: Long,
        title: String,
        ageDays: Int = 1,
        durationMs: Long = 200_000L,
        now: Long = monday,
    ) = TrackItem(
        id = id,
        title = title,
        artist = "Uploader",
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        createdAtMs = now - ageDays * day,
    )

    @Test
    fun `builds the playlist from qualifying uploads of all watched artists`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist", "Miss K8")))
            source.results["Angerfist"] =
                LoadResult.Ok(
                    listOf(
                        upload(1, "Angerfist - New Banger"),
                        upload(2, "Angerfist - Old One", ageDays = 90),
                        upload(3, "Angerfist - 2h Set", durationMs = 7_200_000L),
                        upload(4, "Someone Else - Song"),
                    ),
                )
            source.results["Miss K8"] = LoadResult.Ok(listOf(upload(5, "Miss K8 - Bomb")))

            val result = updater.refresh(monday, 0, onlyIfDue = true)

            assertEquals(RefreshResult.Updated(2), result)
            val state = repository.current()
            assertEquals(setOf(1L, 5L), state.playlist.map { it.track.id }.toSet())
            assertEquals(WeekKey.of(monday, 0), state.weekKey)
        }

    @Test
    fun `nothing happens without artists`() =
        runTest {
            assertEquals(RefreshResult.Skipped, updater.refresh(monday, 0, onlyIfDue = false))
            assertTrue(source.requested.isEmpty())
        }

    @Test
    fun `an already built week is skipped when only due work is asked for`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist")))
            source.results["Angerfist"] = LoadResult.Ok(listOf(upload(1, "Angerfist - A")))
            updater.refresh(monday, 0, onlyIfDue = true)
            source.requested.clear()

            assertEquals(RefreshResult.Skipped, updater.refresh(monday + 2 * day, 0, onlyIfDue = true))
            assertTrue(source.requested.isEmpty())

            // A forced refresh in the same week does run.
            assertEquals(RefreshResult.Updated(1), updater.refresh(monday + 2 * day, 0, onlyIfDue = false))
        }

    @Test
    fun `a new week picks new uploads first`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist")))
            val week1 = (1L..20L).map { upload(it, "Angerfist - Song $it", ageDays = it.toInt()) }
            source.results["Angerfist"] = LoadResult.Ok(week1)
            updater.refresh(monday, 0, onlyIfDue = true)

            val nextMonday = monday + 7 * day
            val fresh = (21L..24L).map { upload(it, "Angerfist - Song $it", ageDays = 1, now = nextMonday) }
            source.results["Angerfist"] = LoadResult.Ok(week1.map { it.copy(createdAtMs = nextMonday - 10 * day) } + fresh)
            updater.refresh(nextMonday, 0, onlyIfDue = true)

            val ids = repository.current().playlist.map { it.track.id }
            assertTrue(ids.containsAll(listOf(21L, 22L, 23L, 24L)))
            assertEquals(DiscoveryRotation.MIN_SIZE, ids.size)
        }

    @Test
    fun `when every lookup fails the current list is kept and the failure is reported`() =
        runTest {
            val existing = DiscoveryState(artists = listOf("Angerfist"), weekKey = 1L)
            repository.set(existing)
            source.results["Angerfist"] = LoadResult.Error("offline")

            val result = updater.refresh(monday, 0, onlyIfDue = true)

            assertEquals(RefreshResult.Failed("offline"), result)
            assertEquals(existing, repository.current())
        }

    @Test
    fun `one failing artist does not stop the others`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("Angerfist", "Miss K8")))
            source.results["Angerfist"] = LoadResult.Error("boom")
            source.results["Miss K8"] = LoadResult.Ok(listOf(upload(5, "Miss K8 - Bomb")))

            assertEquals(RefreshResult.Updated(1), updater.refresh(monday, 0, onlyIfDue = true))
        }

    @Test
    fun `a manual genre drops uploads of another genre and counts them`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("MBK")).let { DiscoveryRotation.addGenre(it, "uptempo") })
            source.results["MBK"] =
                LoadResult.Ok(
                    listOf(
                        upload(1, "MBK - Hardcore Bomb").copy(genre = "Uptempo Hardcore"),
                        upload(2, "MBK - Schlager Hit").copy(genre = "Schlager"),
                        upload(3, "MBK - Untagged"),
                    ),
                )

            val result = updater.refresh(monday, 0, onlyIfDue = false)

            assertEquals(RefreshResult.Updated(2, filteredByGenre = 1), result)
            val ids = repository.current().playlist.map { it.track.id }
            assertEquals(listOf(1L, 3L), ids)
        }

    @Test
    fun `auto mode derives genres from the likes`() =
        runTest {
            repository.set(DiscoveryState(artists = listOf("MBK")))
            source.taste = LoadResult.Ok((1L..10L).map { upload(it, "Like $it").copy(genre = "Uptempo Hardcore") })
            source.results["MBK"] =
                LoadResult.Ok(
                    listOf(
                        upload(21, "MBK - A").copy(genre = "Uptempo Hardcore"),
                        upload(22, "MBK - B").copy(genre = "Schlager"),
                    ),
                )

            val result = updater.refresh(monday, 0, onlyIfDue = true)

            assertEquals(RefreshResult.Updated(1, filteredByGenre = 1), result)
            assertEquals(1, source.tasteReads)
            assertTrue(repository.current().autoGenres.isNotEmpty())
        }

    @Test
    fun `a failing taste read keeps the earlier genres`() =
        runTest {
            val before = DiscoveryState(artists = listOf("MBK"), autoGenres = listOf("uptempo"))
            repository.set(before)
            source.taste = LoadResult.Error("offline")
            source.results["MBK"] = LoadResult.Ok(listOf(upload(1, "MBK - A").copy(genre = "Schlager")))

            val result = updater.refresh(monday, 0, onlyIfDue = true)

            assertEquals(RefreshResult.Updated(0, filteredByGenre = 1), result)
            assertEquals(listOf("uptempo"), repository.current().autoGenres)
        }

    private class FakeDiscoveryRepository : DiscoveryRepository {
        private val flow = MutableStateFlow(DiscoveryState())
        override val state: Flow<DiscoveryState> = flow

        fun set(state: DiscoveryState) {
            flow.value = state
        }

        override suspend fun current(): DiscoveryState = flow.first()

        override suspend fun update(transform: (DiscoveryState) -> DiscoveryState) {
            flow.value = transform(flow.value)
        }
    }

    private class FakeDiscoverySource : DiscoverySource {
        val results = mutableMapOf<String, LoadResult<List<TrackItem>>>()
        val requested = mutableListOf<String>()
        var taste: LoadResult<List<TrackItem>> = LoadResult.Error("signed out")
        var tasteReads = 0

        override suspend fun tasteTracks(): LoadResult<List<TrackItem>> {
            tasteReads++
            return taste
        }

        override suspend fun recentUploads(artist: String): LoadResult<List<TrackItem>> {
            requested.add(artist)
            return results[artist] ?: LoadResult.Ok(emptyList())
        }
    }
}
