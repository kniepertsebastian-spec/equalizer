package com.hardbasseq.eq.link

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
}
