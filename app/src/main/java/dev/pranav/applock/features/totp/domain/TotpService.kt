package dev.pranav.applock.features.totp.domain

import dev.samstevens.totp.code.CodeGenerator
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.code.HashingAlgorithm
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TotpEnrollment(
    val secret: String,
    val otpauthUri: String
)

object TotpService {
    private const val ISSUER = "AppLock"
    private const val LABEL = "AppLock"
    private const val DIGITS = 6
    private const val PERIOD_SECONDS = 100

    fun createEnrollment(): TotpEnrollment {
        val secret = DefaultSecretGenerator(32).generate()

        val qrData = QrData.Builder()
            .label(LABEL)
            .secret(secret)
            .issuer(ISSUER)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(DIGITS)
            .period(PERIOD_SECONDS)
            .build()

        return TotpEnrollment(
            secret = secret,
            otpauthUri = qrData.uri
        )
    }

    fun verify(secret: String, code: String): Boolean {
        val normalizedCode = code.filter(Char::isDigit)
        if (normalizedCode.length != DIGITS) return false

        val generator: CodeGenerator =
            DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS)

        val verifier = DefaultCodeVerifier(
            generator,
            SystemTimeProvider()
        )

        verifier.setTimePeriod(PERIOD_SECONDS)
        // Allow +/- one 100-second time step for normal device-clock drift.
        verifier.setAllowedTimePeriodDiscrepancy(1)

        return verifier.isValidCode(secret, normalizedCode)
    }

    fun maskSecret(secret: String): String {
        if (secret.length <= 8) return "••••••••"
        return "${secret.take(4)}••••••••${secret.takeLast(4)}"
    }

    fun formatSecret(secret: String): String =
        secret.chunked(4).joinToString(" ")

    /**
     * Standard otpauth URI. Kept here as a small fallback/helper so the URI
     * format remains explicit and easy to inspect in tests.
     */
    fun buildOtpauthUri(secret: String): String {
        val label = URLEncoder.encode(LABEL, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val issuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8.name())
            .replace("+", "%20")

        return "otpauth://totp/$label" +
            "?secret=${URLEncoder.encode(secret, StandardCharsets.UTF_8.name())}" +
            "&issuer=$issuer" +
            "&algorithm=SHA1" +
            "&digits=$DIGITS" +
            "&period=$PERIOD_SECONDS"
    }
}
