package com.auracurve.app.player.playback

import androidx.media3.common.MimeTypes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.DirectStream
import com.auracurve.app.playlist.LocalTrackId
import com.auracurve.app.playlist.QueueMediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrackMediaItemsTest {
    private fun track(
        id: Long,
        streamUrl: String?,
        isHls: Boolean = false,
    ) = TrackItem(id = id, title = "T", artist = "A", artworkUrl = null, streamUrl = streamUrl, durationMs = 1000, isHlsStream = isHls)

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

    @Test
    fun catalogTrack_playsItsHttpsAddressAndMarksHls() {
        val id = DirectStream.idOf("radio", "station-1")
        val url = "https://stream.example.com/live.m3u8"

        val item = TrackMediaItems.toMediaItem(track(id, streamUrl = url, isHls = true))

        assertEquals(url, item.localConfiguration?.uri.toString())
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration?.mimeType)
    }

    @Test
    fun catalogTrack_withPlainHttp_isNotPlayedDirectly() {
        val id = DirectStream.idOf("radio", "station-2")

        val item = TrackMediaItems.toMediaItem(track(id, streamUrl = "http://stream.example.com/live"))

        assertEquals(QueueMediaId.streamUri(id), item.localConfiguration?.uri.toString())
    }
}
