package com.hardbasseq.eq.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyTrackPageTest {
    private fun page(data: String) = """<script id="__NEXT_DATA__" type="application/json">$data</script>"""

    @Test
    fun `reads title and artist from the artists list`() {
        val html =
            page("""{"props":{"pageProps":{"state":{"data":{"entity":{"name":"Drum Go Bang","artists":[{"name":"Angerfist"}]}}}}}}""")

        assertEquals(SpotifyTrack("Drum Go Bang", "Angerfist"), SpotifyTrackPage.parse(html))
    }

    @Test
    fun `falls back to the subtitle`() {
        val html = page("""{"a":[{"title":"Bomb","subtitle":"Miss K8"}]}""")

        assertEquals(SpotifyTrack("Bomb", "Miss K8"), SpotifyTrackPage.parse(html))
    }

    @Test
    fun `a page without an artist gives null`() {
        assertNull(SpotifyTrackPage.parse(page("""{"entity":{"name":"Only a title"}}""")))
        assertNull(SpotifyTrackPage.parse("<html>nothing</html>"))
        assertNull(SpotifyTrackPage.parse(page("{broken")))
    }
}
