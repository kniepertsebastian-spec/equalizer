package com.auracurve.app.player.auth

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts one value; throws when it cannot. */
interface TokenCrypto {
    fun encrypt(plain: String): String

    fun decrypt(sealed: String): String
}

/**
 * Sign-in tokens in SharedPreferences, encrypted with a key that never leaves the Android Keystore
 * (AuraCurve roadmap 03, 2.1). Values written before that are plain text: the first read takes
 * them over and replaces them with the encrypted form.
 *
 * The key is not part of any backup, so a value restored on another phone cannot be read: the
 * read returns null and the user signs in again (the token files are left out of the backup
 * anyway, 2.2).
 */
class TokenVault(
    private val prefs: SharedPreferences,
    private val crypto: TokenCrypto,
) {
    fun get(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        if (!raw.startsWith(PREFIX)) {
            // Plain text from an older version: move it over now.
            prefs.edit().apply { putSealed(key, raw) }.apply()
            return raw
        }
        return runCatching { crypto.decrypt(raw.removePrefix(PREFIX)) }
            .onFailure { Log.w(TAG, "Token $key cannot be read any more; sign in again") }
            .getOrNull()
    }

    fun put(
        key: String,
        value: String?,
    ) {
        prefs.edit().apply { putSealed(key, value) }.apply()
    }

    /** For an editor that writes more than the token: never stores plain text, drops the key instead. */
    fun SharedPreferences.Editor.putSealed(
        key: String,
        value: String?,
    ) {
        val sealed = value?.let { v -> runCatching { PREFIX + crypto.encrypt(v) }.getOrNull() }
        if (value != null && sealed == null) Log.w(TAG, "Keystore refused to encrypt $key; not stored")
        if (sealed == null) remove(key) else putString(key, sealed)
    }

    companion object {
        /** Marks an encrypted value (version 1: AES-GCM, IV in front). */
        const val PREFIX = "v1:"
        private const val TAG = "TokenVault"
    }
}

/** AES-256-GCM with a key in the AndroidKeyStore; the output is Base64 of IV + ciphertext. */
class KeystoreTokenCrypto(
    private val alias: String = DEFAULT_ALIAS,
) : TokenCrypto {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }

    override fun decrypt(sealed: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "auracurve_sign_in_tokens"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BITS = 256
    }
}
