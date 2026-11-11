package dev.pranav.applock.features.totp.domain

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238: SHA-1, six digits, 30-second steps, with one step of clock tolerance. */
object TotpService {
    const val DIGITS = 6
    const val PERIOD_SECONDS = 30L
    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generateSecret(): String {
        val bytes = ByteArray(20).also { SecureRandom().nextBytes(it) }
        return buildString {
            var buffer = 0
            var bits = 0
            for (byte in bytes) {
                buffer = (buffer shl 8) or (byte.toInt() and 0xff)
                bits += 8
                while (bits >= 5) {
                    bits -= 5
                    append(BASE32[(buffer ushr bits) and 31])
                }
            }
        }
    }

    fun buildOtpauthUri(secret: String): String =
        "otpauth://totp/AppLock:AppLock?secret=$secret&issuer=AppLock&algorithm=SHA1&digits=6&period=30"

    fun verify(secret: String, code: String, timeSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        return matchingTimeStep(secret, code, timeSeconds) != null
    }

    fun matchingTimeStep(
        secret: String,
        code: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000,
        afterStep: Long = -1
    ): Long? {
        if (code.length != DIGITS || code.any { it !in '0'..'9' } || timeSeconds < 0) return null
        return try {
            (-1L..1L).map { timeSeconds / PERIOD_SECONDS + it }.firstOrNull { step ->
                step > afterStep && MessageDigest.isEqual(
                    code.toByteArray(Charsets.US_ASCII),
                    codeAtStep(secret, step).toByteArray(Charsets.US_ASCII)
                )
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    internal fun codeAtStep(secret: String, step: Long): String {
        require(secret.isNotEmpty() && secret.length % 8 == 0)
        val key = ByteArray(secret.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        secret.forEach { char ->
            val value = BASE32.indexOf(char)
            require(value >= 0)
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                key[index++] = (buffer ushr bits).toByte()
            }
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val number = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
        return (number % 1_000_000).toString().padStart(DIGITS, '0')
    }
}
