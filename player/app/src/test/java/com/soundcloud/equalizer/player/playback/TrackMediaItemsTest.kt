package com.soundcloud.equalizer.player.playback

import com.hardbasseq.eq.playlist.LocalTrackId
import com.hardbasseq.eq.playlist.QueueMediaId
import com.soundcloud.equalizer.player.model.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrackMediaItemsTest {
    private fun track(
        id: Long,
        streamUrl: String?,
    ) = TrackItem(id = id, title = "T", artist = "A", artworkUrl = null, streamUrl = streamUrl, durationMs = 1000)

    @Test
    fun soundCloudTrack_getsThePlaceholderAddressForLookup() {
        val item = TrackMediaItems.toMediaItem(track(42, streamUrl = "https://cf-media.sndcdn.com/expiring.mp3"))

        assertEquals(QueueMediaId.streamUri(42), item.localConfiguration?.uri.toString())
        assertEquals(42L, TrackMediaItems.trackId(item))
    }

    @Test
    fun localFile_playsFromItsOwnAddressWithoutAPlaceholder() {
        val uri = "content://com.android.providers.media.documents/document/audio%3A7"
        val id = LocalTrackId.of(uri)

        val item = TrackMediaItems.toMediaItem(track(id, streamUrl = uri))

        assertEquals(uri, item.localConfiguration?.uri.toString())
        assertNull("a real address is not ours to resolve", QueueMediaId.trackIdFromStreamUri(item.localConfiguration?.uri.toString()))
        assertEquals(id, TrackMediaItems.trackId(item))
    }
}
