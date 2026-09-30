package com.hardbasseq.eq.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSelectionTest {
    private fun option(
        protocol: String,
        snipped: Boolean,
    ) = StreamOption(protocol, snipped, "https://x/$protocol/$snipped")

    @Test
    fun `full length progressive is preferred`() {
        val picked =
            StreamSelection.pick(
                listOf(option("hls", false), option("progressive", true), option("progressive", false)),
            )
        assertEquals(option("progressive", false), picked)
    }

    @Test
    fun `full length hls beats a progressive preview`() {
        val picked = StreamSelection.pick(listOf(option("progressive", true), option("hls", false)))
        assertEquals(option("hls", false), picked)
    }

    @Test
    fun `a preview is chosen only when nothing full length exists`() {
        val picked = StreamSelection.pick(listOf(option("hls", true), option("progressive", true)))
        assertEquals(option("progressive", true), picked)
    }

    @Test
    fun `unknown protocols are ignored and an empty list yields nothing`() {
        assertNull(StreamSelection.pick(listOf(option("rtmp", false))))
        assertNull(StreamSelection.pick(emptyList()))
    }
}
