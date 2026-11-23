package com.auracurve.app.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSelectionTest {
    private fun option(
        protocol: String,
        snipped: Boolean,
        preset: String = "",
        mimeType: String = "",
    ) = StreamOption(protocol, snipped, "https://x/$protocol/$snipped/$preset", preset, mimeType)

    private val mp3Progressive = option("progressive", false, "mp3_1_0", "audio/mpeg")
    private val mp3Hls = option("hls", false, "mp3_1_0", "audio/mpeg")
    private val aac160 = option("hls", false, "aac_160k", "audio/mp4; codecs=\"mp4a.40.2\"")
    private val aac256 = option("hls", false, "aac_256k", "audio/mp4; codecs=\"mp4a.40.2\"")
    private val aac96 = option("hls", false, "aac_96k", "audio/mp4; codecs=\"mp4a.40.2\"")
    private val opus = option("hls", false, "opus_0_0", "audio/ogg; codecs=\"opus\"")

    @Test
    fun `quality order is aac 256, aac 160, mp3 128`() {
        assertEquals(aac256, StreamSelection.pick(listOf(mp3Progressive, aac160, aac256, mp3Hls)))
        assertEquals(aac160, StreamSelection.pick(listOf(mp3Progressive, mp3Hls, aac160)))
        assertEquals(mp3Progressive, StreamSelection.pick(listOf(mp3Hls, mp3Progressive)))
    }

    @Test
    fun `order of the offered list does not matter`() {
        val all = listOf(mp3Progressive, mp3Hls, aac160, aac256, opus)
        assertEquals(aac256, StreamSelection.pick(all))
        assertEquals(aac256, StreamSelection.pick(all.reversed()))
    }

    @Test
    fun `mp3 128 beats a low aac step`() {
        assertEquals(mp3Progressive, StreamSelection.pick(listOf(aac96, mp3Progressive)))
        assertEquals(aac96, StreamSelection.pick(listOf(aac96)))
    }

    @Test
    fun `opus is never chosen`() {
        assertEquals(mp3Progressive, StreamSelection.pick(listOf(opus, mp3Progressive)))
        assertNull(StreamSelection.pick(listOf(opus)))
    }

    @Test
    fun `codec is recognised from the mime type when the preset is missing`() {
        val aacNoPreset = option("hls", false, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"")
        assertEquals(StreamCodec.AAC, StreamSelection.codec(aacNoPreset))
        assertEquals(aacNoPreset, StreamSelection.pick(listOf(mp3Progressive, aacNoPreset)))
    }

    @Test
    fun `streams without codec information fall back to the old behaviour`() {
        // progressive without any hint is MP3, so a bare progressive beats a bare hls
        val progressive = option("progressive", false)
        val hls = option("hls", false)
        assertEquals(StreamCodec.MP3, StreamSelection.codec(progressive))
        assertEquals(StreamCodec.UNKNOWN, StreamSelection.codec(hls))
        assertEquals(progressive, StreamSelection.pick(listOf(hls, progressive)))
    }

    @Test
    fun `full length progressive is preferred over hls of the same quality`() {
        val picked = StreamSelection.pick(listOf(option("hls", false), option("progressive", true), option("progressive", false)))
        assertEquals(option("progressive", false), picked)
    }

    @Test
    fun `full length hls beats a progressive preview`() {
        val picked = StreamSelection.pick(listOf(option("progressive", true), option("hls", false)))
        assertEquals(option("hls", false), picked)
    }

    @Test
    fun `a full length mp3 beats an aac preview`() {
        val preview = option("hls", true, "aac_160k", "audio/mp4; codecs=\"mp4a.40.2\"")
        assertEquals(mp3Progressive, StreamSelection.pick(listOf(preview, mp3Progressive)))
    }

    @Test
    fun `a preview is chosen only when nothing full length exists - the best one`() {
        val mp3Preview = option("progressive", true, "mp3_1_0", "audio/mpeg")
        val aacPreview = option("hls", true, "aac_160k", "audio/mp4; codecs=\"mp4a.40.2\"")
        assertEquals(aacPreview, StreamSelection.pick(listOf(mp3Preview, aacPreview)))
    }

    @Test
    fun `unknown protocols are ignored and an empty list yields nothing`() {
        assertNull(StreamSelection.pick(listOf(option("rtmp", false))))
        assertNull(StreamSelection.pick(emptyList()))
    }

    // --- What SoundCloud sends today (roadmap 10, P10-4) ---------------------------------------

    private val aacHlsMime = "audio/mp4; codecs=\"mp4a.40.2\""
    private val abr = option("hls", false, "abr_sq", aacHlsMime)

    @Test
    fun `the current list - aac 160, aac 96 and an adaptive playlist - plays aac 160`() {
        val offered = listOf(option("hls", false, "aac_160k", aacHlsMime), option("hls", false, "aac_96k", aacHlsMime), abr)
        assertEquals(aac160.copy(mimeType = aacHlsMime), StreamSelection.pick(offered))
        assertEquals(aac160.copy(mimeType = aacHlsMime), StreamSelection.pick(offered.reversed()))
    }

    @Test
    fun `go plus gets aac 256 over everything else`() {
        val offered = listOf(abr, aac96, aac160, aac256, mp3Hls)
        assertEquals(aac256, StreamSelection.pick(offered))
    }

    @Test
    fun `without progressive mp3 the old hls mp3 still beats aac 96`() {
        // Since August 2026 there is no progressive MP3 any more; not yet migrated uploads keep
        // their MP3 over HLS for a while.
        assertEquals(mp3Hls, StreamSelection.pick(listOf(aac96, mp3Hls, abr)))
        assertEquals(aac160, StreamSelection.pick(listOf(mp3Hls, aac160)))
    }

    @Test
    fun `the adaptive playlist is only the last resort`() {
        assertEquals(aac96, StreamSelection.pick(listOf(abr, aac96)))
        assertEquals(abr, StreamSelection.pick(listOf(abr, opus)))
        val abrNoMime = option("hls", false, "abr_sq", "audio/mpegurl")
        assertEquals(abrNoMime, StreamSelection.pick(listOf(abrNoMime)))
        assertEquals(abrNoMime, StreamSelection.pick(listOf(option("hls", false), abrNoMime)))
    }

    @Test
    fun `a playlist mime type is not taken for mp3`() {
        // "audio/mpegurl" starts with "audio/mpeg" but says nothing about the codec inside.
        val playlist = option("hls", false, mimeType = "audio/mpegurl")
        assertEquals(StreamCodec.UNKNOWN, StreamSelection.codec(playlist))
        assertEquals(StreamCodec.UNKNOWN, StreamSelection.codec(option("hls", false, mimeType = "application/vnd.apple.mpegurl")))
        assertEquals(StreamCodec.MP3, StreamSelection.codec(option("hls", false, mimeType = "audio/mpeg")))
        assertEquals(StreamCodec.MP3, StreamSelection.codec(option("hls", false, mimeType = "audio/mpeg; charset=binary")))
        assertEquals(mp3Hls, StreamSelection.pick(listOf(playlist, mp3Hls)))
    }

    @Test
    fun `encrypted streams are never chosen`() {
        // SoundCloud's DRM variants for some Go+ tracks: ExoPlayer has no key for them.
        val encrypted =
            listOf("encrypted-hls", "ctr-encrypted-hls", "cbc-encrypted-hls").map { option(it, false, "aac_256k", aacHlsMime) }
        assertEquals(aac160, StreamSelection.pick(encrypted + aac160))
        assertNull(StreamSelection.pick(encrypted))
    }

    @Test
    fun `the log line names protocol, preset, codec and bitrate`() {
        assertEquals("hls aac_160k (AAC ~160 kbit/s)", StreamSelection.describe(aac160))
        assertEquals("hls aac_256k (AAC ~256 kbit/s)", StreamSelection.describe(aac256))
        assertEquals("hls mp3_1_0 (MP3 ~128 kbit/s)", StreamSelection.describe(mp3Hls))
        assertEquals("hls abr_sq (AAC adaptive)", StreamSelection.describe(abr))
        assertEquals("hls aac_160k (AAC ~160 kbit/s, preview)", StreamSelection.describe(aac160.copy(snipped = true)))
        assertEquals("hls - (UNKNOWN ?)", StreamSelection.describe(option("hls", false)))
    }
}
