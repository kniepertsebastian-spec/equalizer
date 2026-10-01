package com.hardbasseq.eq.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyApiTest {
    @Test
    fun `the challenge matches the example of rfc 7636`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SpotifyAuth.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `verifier and state only use allowed characters and differ each time`() {
        val verifier = SpotifyAuth.newVerifier()
        assertEquals(64, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
        assertFalse(verifier == SpotifyAuth.newVerifier())
        assertEquals(24, SpotifyAuth.newState().length)
    }

    @Test
    fun `the authorize address carries everything spotify needs`() {
        val url = SpotifyAuth.authorizeUrl("client123", "chal", "st")

        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("client_id=client123"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("redirect_uri=hardbasseq%3A%2F%2Fspotify-callback"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=chal"))
        assertTrue(url.contains("state=st"))
        assertTrue(url.contains("scope=playlist-read-private%20playlist-read-collaborative"))
    }

    @Test
    fun `the redirect gives the code only for the matching state`() {
        val base = "hardbasseq://spotify-callback"

        assertEquals(SpotifyAuth.Redirect.Code("abc"), SpotifyAuth.parseRedirect("$base?code=abc&state=st", "st"))
        assertEquals(SpotifyAuth.Redirect.Invalid, SpotifyAuth.parseRedirect("$base?code=abc&state=other", "st"))
        assertEquals(SpotifyAuth.Redirect.Invalid, SpotifyAuth.parseRedirect("$base?code=abc", "st"))
        assertEquals(SpotifyAuth.Redirect.Invalid, SpotifyAuth.parseRedirect("$base?code=abc&state=st", ""))
        assertEquals(SpotifyAuth.Redirect.Denied("access_denied"), SpotifyAuth.parseRedirect("$base?error=access_denied&state=st", "st"))
        assertEquals(SpotifyAuth.Redirect.Invalid, SpotifyAuth.parseRedirect("https://evil.example/?code=abc&state=st", "st"))
        assertEquals(SpotifyAuth.Redirect.Invalid, SpotifyAuth.parseRedirect("$base?state=st", "st"))
    }

    @Test
    fun `tokens are read with their expiry`() {
        val tokens = SpotifyApiJson.parseTokens("""{"access_token":"a","refresh_token":"r","expires_in":3600}""", nowMs = 1_000L)!!

        assertEquals("a", tokens.accessToken)
        assertEquals("r", tokens.refreshToken)
        assertEquals(3_601_000L, tokens.expiresAtMs)
        assertFalse(tokens.isExpired(1_000L))
        assertTrue(tokens.isExpired(3_601_000L - 59_000L))
        assertNull(SpotifyApiJson.parseTokens("""{"refresh_token":"r"}""", 0L))
        assertNull(SpotifyApiJson.parseTokens("not json", 0L))
        assertNull(SpotifyApiJson.parseTokens("""{"access_token":"a"}""", 0L)!!.refreshToken)
    }

    @Test
    fun `a page of the new items answer`() {
        val raw =
            """{"total":230,"next":"https://api.spotify.com/v1/playlists/x/items?offset=50","items":[
            {"item":{"type":"track","name":"Drum Go Bang","artists":[{"name":"Angerfist"},{"name":"Other"}]}},
            {"item":{"type":"episode","name":"A podcast","artists":[]}},
            {"item":null},
            {"item":{"type":"track","name":"Bomb","artists":[{"name":"Miss K8"}]}}]}"""

        val page = SpotifyApiJson.parseItemsPage(raw)!!

        assertEquals(listOf(SpotifyTrack("Drum Go Bang", "Angerfist"), SpotifyTrack("Bomb", "Miss K8")), page.tracks)
        assertEquals("https://api.spotify.com/v1/playlists/x/items?offset=50", page.next)
        assertEquals(230, page.total)
    }

    @Test
    fun `the older track field and a wrapped page work too and the last page has no next`() {
        val old = """{"total":1,"next":null,"items":[{"track":{"name":"Old","artists":[{"name":"A"}]}}]}"""
        val wrapped = """{"items":{"total":1,"next":null,"items":[{"item":{"name":"New","artists":[{"name":"B"}]}}]}}"""

        assertEquals(listOf(SpotifyTrack("Old", "A")), SpotifyApiJson.parseItemsPage(old)!!.tracks)
        assertNull(SpotifyApiJson.parseItemsPage(old)!!.next)
        assertEquals(listOf(SpotifyTrack("New", "B")), SpotifyApiJson.parseItemsPage(wrapped)!!.tracks)
    }

    @Test
    fun `answers that are not a page give null`() {
        assertNull(SpotifyApiJson.parseItemsPage("not json"))
        assertNull(SpotifyApiJson.parseItemsPage("""{"error":{"status":403}}"""))
    }

    @Test
    fun `the playlist name`() {
        assertEquals("Mein Mix", SpotifyApiJson.parsePlaylistName("""{"name":"Mein Mix","id":"x"}"""))
        assertNull(SpotifyApiJson.parsePlaylistName("""{"id":"x"}"""))
    }
}
