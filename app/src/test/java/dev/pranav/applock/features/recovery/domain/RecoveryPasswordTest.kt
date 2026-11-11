package dev.pranav.applock.features.recovery.domain

import org.junit.Assert.*
import org.junit.Test

class RecoveryPasswordTest {
    @Test fun verifiesIndependentPbkdf2Sha256Vector() {
        // Produced independently with Python hashlib.pbkdf2_hmac, salt bytes 0..15.
        val hash = "210000:AAECAwQFBgcICQoLDA0ODw==:U3knZtiXSrk5N58NJnbPD3YEyhSCzrmoPwBBxH/q2eI="
        assertTrue(RecoveryPassword.verify("recovery test passphrase", hash))
    }

    @Test fun saltsEveryPasswordAndVerifiesOnlyTheCorrectValue() {
        val password = "a long recovery password"
        val hash = RecoveryPassword.hash(password)
        assertNotEquals(hash, RecoveryPassword.hash(password))
        assertFalse(hash.contains(password))
        assertTrue(RecoveryPassword.verify(password, hash))
        assertFalse(RecoveryPassword.verify("a wrong recovery password", hash))
    }

    @Test fun keepsCharactersBeyondThePrimaryPasswordLimit() {
        val password = "a".repeat(64) + "first ending"
        val hash = RecoveryPassword.hash(password)
        assertTrue(RecoveryPassword.verify(password, hash))
        assertFalse(RecoveryPassword.verify("a".repeat(64) + "other ending", hash))
    }

    @Test fun preservesSpacesAndCase() {
        val password = "  Recovery Passphrase  "
        val hash = RecoveryPassword.hash(password)
        assertTrue(RecoveryPassword.verify(password, hash))
        assertFalse(RecoveryPassword.verify(password.trim(), hash))
        assertFalse(RecoveryPassword.verify(password.lowercase(), hash))
    }

    @Test fun malformedOrEmptyStorageCannotAuthorizeRecovery() {
        listOf("", "plain password", "1:AAAA:AAAA", "210000:??:??", "210000:AA==:AA==").forEach {
            assertFalse(RecoveryPassword.verify("a recovery password", it))
        }
    }

    @Test fun rejectsWeakAndOversizedSetupValues() {
        listOf("short", " ".repeat(12), "a".repeat(129)).forEach {
            assertThrows(IllegalArgumentException::class.java) { RecoveryPassword.hash(it) }
        }
    }
}
