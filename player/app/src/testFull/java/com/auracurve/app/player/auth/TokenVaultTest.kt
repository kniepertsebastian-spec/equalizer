package com.auracurve.app.player.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TokenVaultTest {
    // Stands in for the Keystore, which Robolectric does not have.
    private class FakeCrypto : TokenCrypto {
        var broken = false

        override fun encrypt(plain: String): String {
            check(!broken)
            return plain.reversed()
        }

        override fun decrypt(sealed: String): String {
            check(!broken)
            return sealed.reversed()
        }
    }

    private val prefs =
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("vault_test", Context.MODE_PRIVATE).also {
            it.edit().clear().commit()
        }
    private val crypto = FakeCrypto()
    private val vault = TokenVault(prefs, crypto)

    @Test
    fun `a stored token is never plain text and reads back`() {
        vault.put("refresh_token", "secret-123")
        val raw = prefs.getString("refresh_token", null)!!
        assertTrue(raw.startsWith(TokenVault.PREFIX))
        assertFalse(raw.contains("secret-123"))
        assertEquals("secret-123", vault.get("refresh_token"))
    }

    @Test
    fun `a plain token from an older version is taken over and encrypted`() {
        prefs.edit().putString("oauth_token", "old-plain").commit()
        assertEquals("old-plain", vault.get("oauth_token"))
        assertTrue(prefs.getString("oauth_token", null)!!.startsWith(TokenVault.PREFIX))
        assertEquals("old-plain", vault.get("oauth_token"))
    }

    @Test
    fun `an unreadable token (key gone after a restore) means signed out`() {
        vault.put("access_token", "abc")
        crypto.broken = true
        assertNull(vault.get("access_token"))
    }

    @Test
    fun `when the keystore refuses, nothing is stored in plain text`() {
        crypto.broken = true
        vault.put("access_token", "abc")
        assertNull(prefs.getString("access_token", null))
    }

    @Test
    fun `null removes the token`() {
        vault.put("oauth_token", "x")
        vault.put("oauth_token", null)
        assertNull(prefs.getString("oauth_token", null))
    }
}
