package com.auracurve.app.remote

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The bearer token the web remote sends with every API call (Pixel-Car module 04a): 32 random
 * bytes, Base64url without padding. Never logged; kept in the app's no-backup storage only.
 */
object RemoteToken {
    private const val TOKEN_BYTES = 32
    private val random = SecureRandom()

    fun generate(): String = randomUrlSafe(TOKEN_BYTES)

    /** [bytes] random bytes, Base64url without padding (also used for the WebSocket tickets). */
    fun randomUrlSafe(bytes: Int): String {
        val raw = ByteArray(bytes).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    /** Compares in constant time, so the answer time tells nothing about how much of a guess was right. */
    fun matches(
        expected: String?,
        candidate: String?,
    ): Boolean {
        if (expected.isNullOrEmpty() || candidate == null) return false
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), candidate.toByteArray(Charsets.UTF_8))
    }

    /** The token from an `Authorization: Bearer <token>` header, or null. */
    fun fromAuthorizationHeader(header: String?): String? {
        if (header == null) return null
        val prefix = "Bearer "
        return if (header.length > prefix.length && header.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
            header.substring(prefix.length).trim()
        } else {
            null
        }
    }
}
