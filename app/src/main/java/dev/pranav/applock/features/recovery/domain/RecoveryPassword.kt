package dev.pranav.applock.features.recovery.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Recovery passwords are independent of the primary credential and are never stored in plaintext. */
object RecoveryPassword {
    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 128
    private const val ITERATIONS = 210_000

    fun hash(password: String): String {
        require(password.length in MIN_LENGTH..MAX_LENGTH && password.isNotBlank())
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        return "$ITERATIONS:${encoder.encodeToString(salt)}:${encoder.encodeToString(derive(password, salt, ITERATIONS))}"
    }

    fun verify(password: String, stored: String): Boolean {
        if (password.length !in MIN_LENGTH..MAX_LENGTH) return false
        return try {
            val parts = stored.split(':')
            if (parts.size != 3) return false
            val iterations = parts[0].toInt()
            if (iterations != ITERATIONS) return false
            val salt = Base64.getDecoder().decode(parts[1])
            val expected = Base64.getDecoder().decode(parts[2])
            salt.size == 16 && expected.size == 32 &&
                MessageDigest.isEqual(expected, derive(password, salt, iterations))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
