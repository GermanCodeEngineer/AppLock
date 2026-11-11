package dev.pranav.applock.features.totp.data

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import dev.pranav.applock.features.totp.domain.TotpService
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the TOTP secret encrypted with an AES-GCM key held by Android Keystore.
 *
 * Only the ciphertext and IV are persisted in SharedPreferences.
 * The plaintext TOTP secret is never persisted there.
 */
class TotpSecretStore(context: Context) {

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "AppLock.TotpSecretKey"
        private const val PREFS = "totp_secure_storage"
        private const val SECRET_CIPHERTEXT = "secret_ciphertext"
        private const val SECRET_IV = "secret_iv"
        private const val LAST_ACCEPTED_STEP = "last_accepted_step"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private val verificationLock = Any()
        private var failedAttempts = 0
        private var retryAfter = 0L
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply {
            load(null)
        }

        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) {
            return existing
        }

        val generator = KeyGenerator.getInstance(
            "AES",
            KEYSTORE
        )

        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setKeySize(256)
                .build()
        )

        return generator.generateKey()
    }

    fun saveSecret(secret: String): Boolean = synchronized(verificationLock) {
        require(secret.isNotBlank()) { "TOTP secret must not be blank" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val ciphertext = cipher.doFinal(
            secret.toByteArray(StandardCharsets.UTF_8)
        )

        failedAttempts = 0
        retryAfter = 0L
        return prefs.edit()
            .remove(LAST_ACCEPTED_STEP)
            .putString(
                SECRET_CIPHERTEXT,
                Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            )
            .putString(
                SECRET_IV,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
            .commit()
    }

    private fun getSecret(): String? {
        val ciphertext = prefs.getString(SECRET_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(SECRET_IV, null) ?: return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(
                    GCM_TAG_BITS,
                    Base64.decode(iv, Base64.NO_WRAP)
                )
            )

            String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                StandardCharsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Consume a code once across every unlock surface; persist replay protection across restarts. */
    fun verifyCode(code: String): Boolean = synchronized(verificationLock) {
        val secret = getSecret() ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now < retryAfter) return false
        val step = TotpService.matchingTimeStep(secret, code, afterStep = prefs.getLong(LAST_ACCEPTED_STEP, -1))
        if (step == null) {
            failedAttempts++
            if (failedAttempts >= 5) {
                failedAttempts = 0
                retryAfter = now + 30_000
            }
            return false
        }
        val saved = prefs.edit().putLong(LAST_ACCEPTED_STEP, step).commit()
        if (saved) { failedAttempts = 0; retryAfter = 0L }
        saved
    }

    fun clear() = synchronized(verificationLock) {
        failedAttempts = 0
        retryAfter = 0L
        prefs.edit()
            .remove(SECRET_CIPHERTEXT)
            .remove(SECRET_IV)
            .remove(LAST_ACCEPTED_STEP)
            .commit()
    }
}
