package com.hardbasseq.eq.auto

import com.hardbasseq.eq.R
import com.hardbasseq.eq.discovery.DiscoveryRepository
import com.hardbasseq.eq.discovery.DiscoveryState
import com.hardbasseq.eq.discovery.DiscoveryTrack
import com.hardbasseq.eq.playlist.PlaylistRepository
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.hardbasseq.eq.playlist.SavedTrack
import com.hardbasseq.eq.text.FakeTextProvider
import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAutoCatalogTest {
    private val playlists = FakePlaylists()
    private val discovery = FakeDiscovery()
    private var likes: List<TrackItem> = emptyList()
    private var likeLoads = 0
    private var searchResult: List<TrackItem> = emptyList()
    private val searches = mutableListOf<String>()
    private val texts = FakeTextProvider()
    private val catalog =
        AppAutoCatalog(
            playlists,
            discovery,
            {
                likeLoads++
                likes
            },
            { query ->
                searches.add(query)
                searchResult
            },
            texts,
        )

    private fun saved(id: Long) = SavedTrack(id, "Track $id", "Artist")

    private fun item(id: Long) = TrackItem(id, "Like $id", "Artist", null, null, 1_000L)

    private fun trackIdOf(node: com.soundcloud.equalizer.player.playback.AutoNode) =
        (AutoMediaId.parse(node.id) as AutoMediaId.Track).trackId

    @Test
    fun `the root has the three folders`() =
        runTest {
            val root = catalog.children(AutoMediaId.ROOT)

            assertEquals(
                listOf(
                    texts.get(R.string.auto_my_playlists),
                    texts.get(R.string.discovery_title),
                    texts.get(R.string.library_likes_title),
                ),
                root.map { it.title },
            )
            assertTrue(root.none { it.playable })
        }

    @Test
    fun `playlists are folders and their tracks are playable`() =
        runTest {
            playlists.flow.value =
                listOf(SavedPlaylist("https://sc/a?x=1", "Mix", tracks = listOf(saved(1), saved(2)), createdAtMs = 5L))

            val folders = catalog.children(AutoMediaId.PLAYLISTS)
            assertEquals(listOf("Mix"), folders.map { it.title })
            assertFalse(folders.single().playable)

            val tracks = catalog.children(folders.single().id)
            assertEquals(listOf(1L, 2L), tracks.map { trackIdOf(it) })
            assertTrue(tracks.all { it.playable })
        }

    @Test
    fun `starting a playlist track queues the whole playlist from that track`() =
        runTest {
            playlists.flow.value = listOf(SavedPlaylist("p", "Mix", tracks = listOf(saved(1), saved(2), saved(3))))

            val queue = catalog.queueFor(AutoMediaId.playlistTrack("p", 2L))!!

            assertEquals(listOf(1L, 2L, 3L), queue.tracks.map { it.id })
            assertEquals(1, queue.startIndex)
        }

    @Test
    fun `discovery tracks are listed and queued`() =
        runTest {
            discovery.flow.value =
                DiscoveryState(playlist = listOf(DiscoveryTrack(saved(9), "Angerfist", 1L), DiscoveryTrack(saved(10), "MBK", 2L)))

            assertEquals(2, catalog.children(AutoMediaId.DISCOVERY).size)
            val queue = catalog.queueFor(AutoMediaId.discoveryTrack(10L))!!
            assertEquals(1, queue.startIndex)
        }

    @Test
    fun `likes are loaded once for browsing and reused for starting a track`() =
        runTest {
            likes = listOf(item(4), item(5))

            assertEquals(2, catalog.children(AutoMediaId.LIKES).size)
            val queue = catalog.queueFor(AutoMediaId.likedTrack(5L))!!

            assertEquals(1, queue.startIndex)
            assertEquals(1, likeLoads)
        }

    @Test
    fun `unknown ids give nothing`() =
        runTest {
            assertTrue(catalog.children("garbage").isEmpty())
            assertTrue(catalog.children(AutoMediaId.playlistFolder("missing")).isEmpty())
            assertNull(catalog.queueFor("garbage"))
            assertNull(catalog.queueFor(AutoMediaId.playlistTrack("missing", 1L)))
            assertNull(catalog.queueFor(AutoMediaId.discoveryTrack(1L)))
        }

    @Test
    fun `a spoken playlist name starts that playlist`() =
        runTest {
            playlists.flow.value =
                listOf(
                    SavedPlaylist("a", "Auto Mix", tracks = listOf(saved(1), saved(2))),
                    SavedPlaylist("b", "Sport", tracks = listOf(saved(3))),
                )

            val queue = catalog.queueForSearch("auto mix")!!

            assertEquals(listOf(1L, 2L), queue.tracks.map { it.id })
            assertEquals(0, queue.startIndex)
            assertTrue(searches.isEmpty())
        }

    @Test
    fun `something in no playlist is searched on soundcloud`() =
        runTest {
            searchResult = listOf(item(7), item(8))

            val queue = catalog.queueForSearch("Angerfist")!!

            assertEquals(listOf("Angerfist"), searches)
            assertEquals(listOf(7L, 8L), queue.tracks.map { it.id })

            searchResult = emptyList()
            assertNull(catalog.queueForSearch("nothing"))
        }

    @Test
    fun `saying nothing plays the newest playlist or else the likes`() =
        runTest {
            likes = listOf(item(4))
            assertEquals(listOf(4L), catalog.queueForSearch("")!!.tracks.map { it.id })

            playlists.flow.value =
                listOf(
                    SavedPlaylist("old", "Old", tracks = listOf(saved(1)), createdAtMs = 1L),
                    SavedPlaylist("new", "New", tracks = listOf(saved(2)), createdAtMs = 9L),
                )
            assertEquals(listOf(2L), catalog.queueForSearch("  ")!!.tracks.map { it.id })
        }

    private class FakePlaylists : PlaylistRepository {
        val flow = MutableStateFlow<List<SavedPlaylist>>(emptyList())
        override val playlists: Flow<List<SavedPlaylist>> = flow

        override suspend fun save(playlist: SavedPlaylist) {
            flow.value = flow.value.filterNot { it.id == playlist.id } + playlist
        }

        override suspend fun delete(id: String) {
            flow.value = flow.value.filterNot { it.id == id }
        }
    }

    private class FakeDiscovery : DiscoveryRepository {
        val flow = MutableStateFlow(DiscoveryState())
        override val state: Flow<DiscoveryState> = flow

        override suspend fun current(): DiscoveryState = flow.value

        override suspend fun update(transform: (DiscoveryState) -> DiscoveryState) {
            flow.value = transform(flow.value)
        }
    }
}
