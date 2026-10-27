package org.skinsewa.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the Sanctum token with a non-exportable Android Keystore key. */
class SecureTokenStore(private val preferences: android.content.SharedPreferences) {
    fun save(token: String, expiresInSeconds: Long = DEFAULT_EXPIRY_SECONDS): Boolean {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return preferences.edit()
            .putString(TOKEN, Base64.encodeToString(cipher.doFinal(token.toByteArray()), Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putLong(EXPIRES_AT, System.currentTimeMillis() + expiresInSeconds.coerceAtLeast(1) * 1000)
            .commit()
    }

    fun read(): String? = runCatching {
        val expiresAt = preferences.getLong(EXPIRES_AT, 0L)
        if (expiresAt <= System.currentTimeMillis()) {
            clear()
            return null
        }
        val encrypted = preferences.getString(TOKEN, null) ?: return null
        val iv = preferences.getString(IV, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)))
    }.getOrNull()

    fun expiresAtMillis(): Long? = preferences.getLong(EXPIRES_AT, 0L).takeIf { it > 0L }

    fun clear() {
        preferences.edit().remove(TOKEN).remove(IV).remove(EXPIRES_AT).commit()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "skinsewa_api_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TOKEN = "encrypted_bearer_token"
        const val IV = "token_iv"
        const val EXPIRES_AT = "token_expires_at"
        const val DEFAULT_EXPIRY_SECONDS = 60 * 60L
    }
}
