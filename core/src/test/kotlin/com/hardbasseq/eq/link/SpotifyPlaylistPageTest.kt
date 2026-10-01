package com.hardbasseq.eq.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyPlaylistPageTest {
    private fun page(data: String) = """<html><body><script id="__NEXT_DATA__" type="application/json">$data</script></body></html>"""

    @Test
    fun `reads title and tracks wherever the track list sits`() {
        val html =
            page(
                """{"props":{"pageProps":{"state":{"data":{"entity":{"name":"Hardcore Mix","trackList":[
                {"title":"Drum Go Bang","subtitle":"Angerfist","uri":"spotify:track:1"},
                {"title":"Bomb","subtitle":"Miss K8,${' '}MBK"}]}}}}}}""",
            )

        val playlist = SpotifyPlaylistPage.parse(html)!!

        assertEquals("Hardcore Mix", playlist.title)
        assertEquals(
            listOf(SpotifyTrack("Drum Go Bang", "Angerfist"), SpotifyTrack("Bomb", "Miss K8, MBK")),
            playlist.tracks,
        )
    }

    @Test
    fun `falls back to the artists list and a default title`() {
        val html = page("""{"a":[{"trackList":[{"title":"X","artists":[{"name":"DJ A"}]}]}]}""")

        val playlist = SpotifyPlaylistPage.parse(html)!!

        assertEquals(SpotifyPlaylistPage.DEFAULT_TITLE, playlist.title)
        assertEquals(listOf(SpotifyTrack("X", "DJ A")), playlist.tracks)
    }

    @Test
    fun `entries without a title or artist are skipped`() {
        val html = page("""{"trackList":[{"subtitle":"nobody"},{"title":"No Artist"},{"title":"Ok","subtitle":"A"}]}""")

        assertEquals(listOf(SpotifyTrack("Ok", "A")), SpotifyPlaylistPage.parse(html)!!.tracks)
    }

    @Test
    fun `a page without readable tracks gives null`() {
        assertNull(SpotifyPlaylistPage.parse("<html>nothing</html>"))
        assertNull(SpotifyPlaylistPage.parse(page("{not json")))
        assertNull(SpotifyPlaylistPage.parse(page("""{"trackList":[]}""")))
        assertNull(SpotifyPlaylistPage.parse(page("""{"other":1}""")))
    }
}
