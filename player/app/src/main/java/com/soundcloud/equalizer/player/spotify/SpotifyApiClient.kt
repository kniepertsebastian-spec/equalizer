package com.soundcloud.equalizer.player.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hardbasseq.eq.link.SpotifyApiJson
import com.hardbasseq.eq.link.SpotifyAuth
import com.hardbasseq.eq.link.SpotifyPlaylist
import com.hardbasseq.eq.link.SpotifyPlaylistPage
import com.hardbasseq.eq.link.SpotifyTokens
import com.hardbasseq.eq.link.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

// Reads a playlist through Spotify's Web API with the user's own Spotify sign-in (PKCE, no
// client secret): the way to get all songs of a long playlist, which the public page cuts at
// about 100. Spotify only hands out the songs of playlists that belong to the user or that
// they collaborate on. Keeps OkHttp inside :player, like LinkResolver.
class SpotifyApiClient private constructor(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    private val refreshLock = Mutex()

    val clientId: String get() = prefs.getString(KEY_CLIENT_ID, "").orEmpty()

    fun saveClientId(id: String) {
        prefs.edit().putString(KEY_CLIENT_ID, id.trim()).apply()
    }

    fun isSignedIn(): Boolean = !prefs.getString(KEY_REFRESH, null).isNullOrEmpty()

    // The browser page to sign in; null while no client id is saved. Remembers the PKCE
    // verifier and state for the redirect that comes back to SpotifyLoginActivity.
    fun loginIntent(): Intent? {
        val id = clientId
        if (id.isBlank()) return null
        val verifier = SpotifyAuth.newVerifier()
        val state = SpotifyAuth.newState()
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()
        val url = SpotifyAuth.authorizeUrl(id, SpotifyAuth.challengeFor(verifier), state)
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // Finishes the sign-in with the address the browser was sent back to. Returns an error
    // message, or null when signed in.
    suspend fun completeLogin(redirectUri: String): String? =
        withContext(Dispatchers.IO) {
            val state = prefs.getString(KEY_STATE, "").orEmpty()
            val verifier = prefs.getString(KEY_VERIFIER, "").orEmpty()
            prefs.edit().remove(KEY_STATE).remove(KEY_VERIFIER).apply()
            when (val redirect = SpotifyAuth.parseRedirect(redirectUri, state)) {
                SpotifyAuth.Redirect.Invalid -> "Die Anmeldung passt nicht zu der, die gestartet wurde. Bitte nochmal anmelden."
                is SpotifyAuth.Redirect.Denied -> "Spotify: Anmeldung abgebrochen (${redirect.reason})"
                is SpotifyAuth.Redirect.Code ->
                    try {
                        val body =
                            FormBody
                                .Builder()
                                .add("grant_type", "authorization_code")
                                .add("code", redirect.code)
                                .add("redirect_uri", SpotifyAuth.REDIRECT_URI)
                                .add("client_id", clientId)
                                .add("code_verifier", verifier)
                                .build()
                        if (requestTokens(body) != null) null else LOGIN_REFUSED
                    } catch (e: IOException) {
                        "Keine Verbindung zu Spotify: ${e.message}"
                    }
            }
        }

    fun signOut() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES).apply()
    }

    // All songs of the playlist, or an IOException whose message can be shown as is.
    suspend fun readPlaylist(playlistId: String): SpotifyPlaylist =
        withContext(Dispatchers.IO) {
            val tracks = mutableListOf<SpotifyTrack>()
            var url: String? = "$API/playlists/$playlistId/items?limit=$PAGE_SIZE&offset=0"
            while (url != null && tracks.size < MAX_TRACKS) {
                val page = SpotifyApiJson.parseItemsPage(getJson(url)) ?: throw IOException("Spotify hat die Titel in einem unbekannten Format geliefert")
                tracks.addAll(page.tracks)
                url = page.next
            }
            if (tracks.isEmpty()) throw IOException("In dieser Playlist wurden keine Titel gefunden")
            // The name is a nicety: without it the playlist just gets a general title.
            val name = runCatching { SpotifyApiJson.parsePlaylistName(getJson("$API/playlists/$playlistId?fields=name")) }.getOrNull()
            SpotifyPlaylist(name ?: SpotifyPlaylistPage.DEFAULT_TITLE, tracks.take(MAX_TRACKS))
        }

    // One GET with the sign-in: renews the access token when needed, once more after a 401,
    // and waits out Spotify's rate limit a few times.
    private suspend fun getJson(url: String): String {
        var token = validAccessToken() ?: throw IOException(SIGN_IN_AGAIN)
        var refreshed = false
        var waits = 0
        while (true) {
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            http.newCall(request).execute().use { response ->
                val code = response.code
                when {
                    response.isSuccessful -> return response.body?.string().orEmpty()
                    code == 401 && !refreshed -> {
                        refreshed = true
                        token = refreshTokens() ?: throw IOException(SIGN_IN_AGAIN)
                    }

                    code == 401 -> throw IOException(SIGN_IN_AGAIN)
                    code == 403 -> throw IOException(NOT_YOURS)
                    code == 404 -> throw IOException("Spotify kennt diese Playlist nicht (oder sie ist privat)")
                    code == 429 && waits < MAX_RATE_LIMIT_WAITS -> {
                        waits++
                        val seconds = response.header("Retry-After")?.toLongOrNull() ?: 1L
                        delay(seconds.coerceIn(1L, MAX_RETRY_AFTER_SECONDS) * 1_000L)
                    }

                    else -> throw IOException("Spotify antwortet nicht (HTTP $code)")
                }
            }
        }
    }

    private suspend fun validAccessToken(): String? {
        val current = storedTokens() ?: return null
        if (!current.isExpired(System.currentTimeMillis())) return current.accessToken
        return refreshTokens()
    }

    private suspend fun refreshTokens(): String? =
        refreshLock.withLock {
            val refresh = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotEmpty() } ?: return@withLock null
            val body =
                FormBody
                    .Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refresh)
                    .add("client_id", clientId)
                    .build()
            val tokens = requestTokens(body)
            if (tokens == null) signOut()
            tokens?.accessToken
        }

    // POST to Spotify's token address; stores and returns the tokens, null when refused.
    private fun requestTokens(body: FormBody): SpotifyTokens? {
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val tokens = SpotifyApiJson.parseTokens(response.body?.string().orEmpty(), System.currentTimeMillis()) ?: return null
            // Renewing may not return a new refresh token: the old one keeps working then.
            val refresh = tokens.refreshToken ?: prefs.getString(KEY_REFRESH, null)
            prefs
                .edit()
                .putString(KEY_ACCESS, tokens.accessToken)
                .putString(KEY_REFRESH, refresh)
                .putLong(KEY_EXPIRES, tokens.expiresAtMs)
                .apply()
            return tokens
        }
    }

    private fun storedTokens(): SpotifyTokens? {
        val access = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotEmpty() } ?: return null
        return SpotifyTokens(access, prefs.getString(KEY_REFRESH, null), prefs.getLong(KEY_EXPIRES, 0L))
    }

    companion object {
        private const val PREFS = "spotify_auth"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_VERIFIER = "pkce_verifier"
        private const val KEY_STATE = "pkce_state"
        private const val API = "https://api.spotify.com/v1"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val PAGE_SIZE = 50
        private const val MAX_TRACKS = 1_000
        private const val MAX_RATE_LIMIT_WAITS = 3
        private const val MAX_RETRY_AFTER_SECONDS = 10L
        private const val SIGN_IN_AGAIN = "Bitte bei Spotify neu anmelden"
        private const val NOT_YOURS =
            "Spotify gibt die Titel nur für Playlists heraus, die dir gehören oder an denen du mitarbeitest"
        private const val LOGIN_REFUSED =
            "Spotify hat die Anmeldung abgelehnt. Stimmen die Client-ID und die Redirect-URI hardbasseq://spotify-callback?"

        @Volatile
        private var instance: SpotifyApiClient? = null

        fun get(context: Context): SpotifyApiClient =
            instance ?: synchronized(this) { instance ?: SpotifyApiClient(context).also { instance = it } }
    }
}
