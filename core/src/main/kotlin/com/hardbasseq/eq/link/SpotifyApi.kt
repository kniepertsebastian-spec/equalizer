package com.hardbasseq.eq.link

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

// The pure parts of signing in to Spotify and reading a playlist through its Web API: the
// PKCE sign-in (no client secret, the app only knows the user's own client id), the redirect
// the browser comes back with, and the JSON answers. Network and storage live in :player.
// Android-free so it can be unit-tested.
object SpotifyAuth {
    // Registered in the Spotify developer dashboard; HardBass EQ's login activity owns it.
    const val REDIRECT_URI = "hardbasseq://spotify-callback"

    // Reading the user's own (and collaborative) playlists, nothing else.
    const val SCOPES = "playlist-read-private playlist-read-collaborative"

    private const val VERIFIER_LENGTH = 64
    private const val STATE_LENGTH = 24
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun newVerifier(random: SecureRandom = SecureRandom()): String = randomString(VERIFIER_LENGTH, random)

    fun newState(random: SecureRandom = SecureRandom()): String = randomString(STATE_LENGTH, random)

    // BASE64URL(SHA-256(verifier)) without padding, as RFC 7636 asks.
    fun challengeFor(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun authorizeUrl(
        clientId: String,
        challenge: String,
        state: String,
    ): String =
        "https://accounts.spotify.com/authorize?" +
            listOf(
                "client_id" to clientId,
                "response_type" to "code",
                "redirect_uri" to REDIRECT_URI,
                "code_challenge_method" to "S256",
                "code_challenge" to challenge,
                "state" to state,
                "scope" to SCOPES,
            ).joinToString("&") { (key, value) -> "$key=${encode(value)}" }

    sealed interface Redirect {
        data class Code(
            val code: String,
        ) : Redirect

        // The user said no (or Spotify refused); [reason] is Spotify's short error text.
        data class Denied(
            val reason: String,
        ) : Redirect

        // Not our redirect, or the state does not match the sign-in that was started.
        data object Invalid : Redirect
    }

    fun parseRedirect(
        uri: String,
        expectedState: String,
    ): Redirect {
        if (!uri.startsWith(REDIRECT_URI)) return Redirect.Invalid
        val params =
            uri
                .substringAfter('?', missingDelimiterValue = "")
                .substringBefore('#')
                .split('&')
                .filter { it.contains('=') }
                .associate { part -> decode(part.substringBefore('=')) to decode(part.substringAfter('=')) }
        if (expectedState.isEmpty() || params["state"] != expectedState) return Redirect.Invalid
        params["error"]?.let { return Redirect.Denied(it) }
        val code = params["code"]?.takeIf { it.isNotEmpty() } ?: return Redirect.Invalid
        return Redirect.Code(code)
    }

    private fun randomString(
        length: Int,
        random: SecureRandom,
    ): String = buildString { repeat(length) { append(UNRESERVED[random.nextInt(UNRESERVED.length)]) } }

    private fun base64Url(bytes: ByteArray): String {
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            out.append(BASE64_URL[b0 shr 2])
            out.append(BASE64_URL[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            if (b1 >= 0) out.append(BASE64_URL[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)])
            if (b2 >= 0) out.append(BASE64_URL[b2 and 0x3F])
            i += 3
        }
        return out.toString()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}

data class SpotifyTokens(
    val accessToken: String,
    // Spotify may leave it out when refreshing: the old one stays valid then.
    val refreshToken: String?,
    val expiresAtMs: Long,
) {
    // A minute early, so a request does not start with a token that dies on the way.
    fun isExpired(nowMs: Long): Boolean = nowMs >= expiresAtMs - EXPIRY_MARGIN_MS

    private companion object {
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}

// One page of a playlist's songs as the Web API returns it.
data class SpotifyItemsPage(
    val tracks: List<SpotifyTrack>,
    // Address of the next page, null on the last one.
    val next: String?,
    val total: Int,
)

object SpotifyApiJson {
    private val json = Json { isLenient = true }

    fun parseTokens(
        raw: String,
        nowMs: Long,
    ): SpotifyTokens? {
        val root = parse(raw) as? JsonObject ?: return null
        val access = text(root["access_token"]) ?: return null
        val seconds = (root["expires_in"] as? JsonPrimitive)?.intOrNull ?: DEFAULT_LIFETIME_SECONDS
        return SpotifyTokens(access, text(root["refresh_token"]), nowMs + seconds * MS_PER_SECOND)
    }

    // The songs of one page of GET /playlists/{id}/items (or the older /tracks): episodes,
    // local files without a title and removed entries are skipped. Null when the answer is not
    // a page at all.
    fun parseItemsPage(raw: String): SpotifyItemsPage? {
        val root = parse(raw) as? JsonObject ?: return null
        // Tolerates both the paging object itself and one that wraps it ("items": {"items": [...]}).
        val page = (root["items"] as? JsonObject) ?: root
        val entries = page["items"] as? JsonArray ?: return null
        val tracks =
            entries.mapNotNull { entry ->
                val holder = entry as? JsonObject ?: return@mapNotNull null
                // The new API calls the song "item", the older one "track".
                val item = (holder["item"] ?: holder["track"]) as? JsonObject ?: return@mapNotNull null
                if (text(item["type"]) == "episode") return@mapNotNull null
                val title = text(item["name"]) ?: return@mapNotNull null
                val artist = firstArtist(item["artists"]) ?: return@mapNotNull null
                SpotifyTrack(title, artist)
            }
        val total = (page["total"] as? JsonPrimitive)?.intOrNull ?: tracks.size
        return SpotifyItemsPage(tracks, text(page["next"]), total)
    }

    fun parsePlaylistName(raw: String): String? = text((parse(raw) as? JsonObject)?.get("name"))

    private fun firstArtist(element: JsonElement?): String? =
        (element as? JsonArray)?.firstNotNullOfOrNull { text((it as? JsonObject)?.get("name")) }

    private fun parse(raw: String): JsonElement? = runCatching { json.parseToJsonElement(raw) }.getOrNull()

    private fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)
            ?.takeIf {
                it.isString
            }?.contentOrNull
            ?.takeIf { it.isNotBlank() }

    private const val DEFAULT_LIFETIME_SECONDS = 3_600
    private const val MS_PER_SECOND = 1_000L
}
