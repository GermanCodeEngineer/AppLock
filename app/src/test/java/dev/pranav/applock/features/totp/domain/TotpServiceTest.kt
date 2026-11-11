package dev.pranav.applock.features.totp.domain

import org.junit.Assert.*
import org.junit.Test

class TotpServiceTest {
    private val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test fun matchesRfc4226Counters() {
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { index, code -> assertEquals(code, TotpService.codeAtStep(secret, index.toLong())) }
    }

    @Test fun matchesRfc6238Sha1VectorsWithSixDigits() {
        val vectors = listOf(59L to "287082", 1111111109L to "081804", 1111111111L to "050471",
            1234567890L to "005924", 2000000000L to "279037", 20000000000L to "353130")
        vectors.forEach { (seconds, expected) ->
            assertEquals(expected, TotpService.codeAtStep(secret, seconds / 30))
            assertTrue(TotpService.verify(secret, expected, seconds))
        }
    }

    @Test fun allowsOnlyOneAdjacentTimeStep() {
        assertTrue(TotpService.verify(secret, "287082", 60))
        assertTrue(TotpService.verify(secret, "287082", 29))
        assertFalse(TotpService.verify(secret, "287082", 90))
        assertFalse(TotpService.verify(secret, "287082", -1))
    }

    @Test fun requiresExactlySixAsciiDigits() {
        listOf("28708", "0287082", "287 082", "287082x", "２８７０８２", "").forEach {
            assertFalse(TotpService.verify(secret, it, 59))
        }
        assertFalse(TotpService.verify("invalid secret", "287082", 59))
    }

    @Test fun rejectsAlreadyAcceptedAndOlderSteps() {
        assertEquals(1L, TotpService.matchingTimeStep(secret, "287082", 59, afterStep = 0))
        assertNull(TotpService.matchingTimeStep(secret, "287082", 59, afterStep = 1))
        assertNull(TotpService.matchingTimeStep(secret, "287082", 59, afterStep = 2))
    }

    @Test fun generatesIndependent160BitBase32SecretsAndStandardEnrollment() {
        val first = TotpService.generateSecret()
        assertEquals(32, first.length)
        assertTrue(first.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" })
        assertNotEquals(first, TotpService.generateSecret())
        val uri = TotpService.buildOtpauthUri(first)
        assertTrue(uri.startsWith("otpauth://totp/AppLock:AppLock?secret=$first"))
        assertTrue(uri.endsWith("algorithm=SHA1&digits=6&period=30"))
    }
}
