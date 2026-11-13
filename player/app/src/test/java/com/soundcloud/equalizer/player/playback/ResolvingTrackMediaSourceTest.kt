package com.soundcloud.equalizer.player.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.ExoPlayerTestRunner
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.run
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardbasseq.eq.playlist.QueueMediaId
import com.soundcloud.equalizer.player.model.TrackItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Collections

// The play queue with a real ExoPlayer (fake renderers and clock): stream addresses are looked
// up only when ExoPlayer loads an entry - the playing track and, while it plays, the next one.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ResolvingTrackMediaSourceTest {
    private val lookups: MutableList<Long> = Collections.synchronizedList(mutableListOf())
    private val failingIds = HashSet<Long>()
    private val hlsIds = HashSet<Long>()
    private val streams = RecordingStreamFactory()
    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        val factory =
            ResolvingTrackMediaSourceFactory(
                delegate = streams,
                resolver = { trackId ->
                    lookups.add(trackId)
                    if (trackId in failingIds) throw IOException("no stream for $trackId")
                    ResolvedStream(url = streamUrl(trackId), isHls = trackId in hlsIds, isPreview = false)
                },
                onResolved = { _, _ -> },
            )
        player =
            TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
                .setMediaSourceFactory(factory)
                // As AudioPlayerService has it (ExoPlayer's default; the test builder's is off).
                .setUseLazyPreparation(true)
                .build()
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun aQueuePlaysThrough_eachTrackLookedUpOnce_inOrder() {
        player.setMediaItems(queue(1, 2, 3))
        player.prepare()
        player.play()

        run(player).untilState(Player.STATE_ENDED)

        assertEquals(listOf(1L, 2L, 3L), lookups.toList())
        assertEquals(listOf(streamUrl(1), streamUrl(2), streamUrl(3)), streams.createdUris())
    }

    @Test
    fun aLongQueue_isNotLookedUpAtOnce_onlyAShortWayAhead() {
        player.setMediaItems(queue(*(1L..20L).toList().toLongArray()))
        player.prepare()
        player.play()

        // ExoPlayer sets up the next entry once the playing one is completely buffered. The fake
        // stream is that at once (and loads a whole track in one go); a real one only when its
        // file is downloaded - the default buffer keeps about 50 s ahead. So the fake runs a
        // little further ahead than a real stream, but never through the queue.
        run(player).untilPositionAtLeast(0, 1_000)
        assertEquals(listOf(1L, 2L), lookups.toList())

        run(player).untilPositionAtLeast(0, TRACK_MS - 5_000)
        assertLookedUpInQueueOrder(atMost = 3)

        run(player).untilPositionAtLeast(1, 1_000)
        assertLookedUpInQueueOrder(atMost = 4)
        assertEquals(lookups.size, streams.createdUris().size)
    }

    @Test
    fun theNextTrackFollowsWithoutBuffering_becauseItWasLoadedAhead() {
        val states = Collections.synchronizedList(mutableListOf<Int>())
        val transitions = Collections.synchronizedList(mutableListOf<Int>())
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    states.add(playbackState)
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    transitions.add(reason)
                }
            },
        )
        player.setMediaItems(queue(1, 2, 3))
        player.prepare()
        player.play()

        run(player).untilState(Player.STATE_ENDED)

        // Buffering once at the start, then ready through both track changes until the end.
        assertEquals(listOf(Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED), states.toList())
        // The queue was set, then two automatic changes to the next track.
        assertEquals(
            listOf(
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            ),
            transitions.toList(),
        )
    }

    @Test
    fun hlsStreams_getTheHlsSource_plainFilesTheDefault() {
        hlsIds.add(2L)
        player.setMediaItems(queue(1, 2))
        player.prepare()
        player.play()

        run(player).untilState(Player.STATE_ENDED)

        assertNull(streams.created[0].localConfiguration?.mimeType)
        assertEquals(MimeTypes.APPLICATION_M3U8, streams.created[1].localConfiguration?.mimeType)
    }

    @Test
    fun aFailedLookupWhilePreloading_doesNotStopTheCurrentTrack_andSurfacesWhenTheTrackIsDue() {
        failingIds.add(2L)
        player.setMediaItems(queue(1, 2, 3))
        player.prepare()
        player.play()

        val error = run(player).untilPlayerError()

        // The first track played to its end; the error belongs to the second.
        assertEquals(1, player.currentMediaItemIndex)
        assertTrue(error.cause is IOException)
        assertEquals(listOf(1L, 2L), lookups.toList())
    }

    @Test
    fun preparingAgainAfterAnError_looksTheStreamUpAgain() {
        failingIds.add(1L)
        player.setMediaItems(queue(1, 2))
        player.prepare()
        player.play()
        run(player).untilPlayerError()

        failingIds.clear()
        player.prepare()
        run(player).untilState(Player.STATE_ENDED)

        assertEquals(listOf(1L, 1L, 2L), lookups.toList())
    }

    @Test
    fun thePlayerShowsTheQueuedEntry_notTheShortLivedStreamAddress() {
        player.setMediaItems(queue(7))
        player.prepare()

        run(player).untilState(Player.STATE_READY)

        val current = player.currentMediaItem
        assertEquals(QueueMediaId.of(7), current?.mediaId)
        assertEquals(QueueMediaId.streamUri(7), current?.localConfiguration?.uri.toString())
        assertEquals("Track 7", current?.mediaMetadata?.title.toString())
    }

    // Each track once, in queue order, and no further than [atMost] tracks into the queue.
    private fun assertLookedUpInQueueOrder(atMost: Int) {
        val looked = lookups.toList()
        assertEquals((1L..looked.size.toLong()).toList(), looked)
        assertTrue("looked up ${looked.size} tracks", looked.size <= atMost)
    }

    private fun queue(vararg ids: Long): List<MediaItem> =
        ids.map { id ->
            TrackMediaItems.toMediaItem(
                TrackItem(id = id, title = "Track $id", artist = "Artist", artworkUrl = null, streamUrl = null, durationMs = TRACK_MS),
            )
        }

    private fun streamUrl(trackId: Long) = "https://streams.example/$trackId.mp3"

    // Stands in for the real progressive / HLS sources: a fake audio stream of TRACK_MS.
    private class RecordingStreamFactory : MediaSource.Factory {
        val created: MutableList<MediaItem> = Collections.synchronizedList(mutableListOf())

        fun createdUris(): List<String> = created.map { it.localConfiguration?.uri.toString() }

        override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory = this

        override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory = this

        override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            created.add(mediaItem)
            val window =
                FakeTimeline.TimelineWindowDefinition
                    .Builder()
                    .setMediaItem(mediaItem)
                    .setDurationUs(TRACK_MS * 1_000)
                    .build()
            return FakeMediaSource(FakeTimeline(window), ExoPlayerTestRunner.AUDIO_FORMAT)
        }
    }

    private companion object {
        const val TRACK_MS = 120_000L
    }
}
