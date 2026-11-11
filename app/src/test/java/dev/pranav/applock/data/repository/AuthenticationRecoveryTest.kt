package dev.pranav.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import dev.pranav.applock.core.navigation.NavigationManager
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.services.AppLockManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class AuthenticationRecoveryTest {
    private val data = mutableMapOf<String, MutableMap<String, Any>>()
    private val context = mock(Context::class.java).also { context ->
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenAnswer { call ->
            val values = data.getOrPut(call.getArgument(0)) { mutableMapOf() }
            preferences(values)
        }
    }
    private val repository = AppLockRepository(context)
    private val recovery = "my separate recovery passphrase"

    private fun seedAuthentication() {
        data.getOrPut("app_lock_prefs") { mutableMapOf() }.putAll(mapOf("password" to "1234", "pattern" to "0123"))
        repository.setLockType(PreferencesRepository.LOCK_TYPE_TOTP)
        repository.setBiometricAuthEnabled(true)
        data.getOrPut("totp_secure_storage") { mutableMapOf() }.putAll(mapOf("secret_ciphertext" to "encrypted", "secret_iv" to "iv"))
        data.getOrPut("app_prefs") { mutableMapOf() }["intro_shown"] = true
    }

    @Test fun recoveryClearsAllCredentialsAndUnlockSessionsButKeepsProtection() {
        seedAuthentication()
        assertTrue(repository.setRecoveryPassword(recovery))
        repository.addLockedApp("protected.app")
        repository.setUnlockTimeDuration(5)
        repository.setAntiUninstallEnabled(true)
        AppLockManager.unlockApp("protected.app")
        assertTrue(repository.recoverAuthentication(recovery))
        assertNull(repository.getPassword())
        assertNull(repository.getPattern())
        assertFalse(repository.isBiometricAuthEnabled())
        assertFalse(repository.hasAuthentication())
        assertFalse(repository.validateTotp("123456"))
        assertTrue(data.getValue("totp_secure_storage").isEmpty())
        assertTrue(repository.hasRecoveryPassword())
        assertTrue(repository.isAppLocked("protected.app"))
        assertTrue(repository.isProtectEnabled())
        assertTrue(repository.isAntiUninstallEnabled())
        assertEquals(5, repository.getUnlockTimeDuration())
        assertTrue(AppLockManager.appUnlockTimes.isEmpty())
        assertEquals("", AppLockManager.temporarilyUnlockedApp)
        assertEquals(Screen.SetPassword.route, NavigationManager(context).determineStartDestination())
        // Relaunching after recovery must still require enrollment.
        assertFalse(AppLockRepository(context).hasAuthentication())
    }

    @Test fun wrongOrMissingRecoveryCannotChangeAuthentication() {
        seedAuthentication()
        assertFalse(repository.recoverAuthentication(recovery))
        repository.setRecoveryPassword(recovery)
        val before = data.mapValues { it.value.toMap() }
        assertFalse(repository.recoverAuthentication("the wrong recovery passphrase"))
        assertEquals(before, data)
        assertTrue(repository.isBiometricAuthEnabled())
    }

    @Test fun totpOnlyAndUnavailableKeystoreStillRequireAuthentication() {
        seedAuthentication()
        data.getValue("app_lock_prefs").clear()
        assertTrue(repository.hasAuthentication())
        assertFalse(repository.validateTotp("123456"))
        assertEquals(Screen.PasswordOverlay.route, NavigationManager(context).determineStartDestination())
    }

    @Test fun newInstallRequiresRecoveryBeforePrimarySetup() {
        data.getOrPut("app_prefs") { mutableMapOf() }["intro_shown"] = true
        assertEquals(Screen.RecoverySetup.route, NavigationManager(context).determineStartDestination())
        repository.setRecoveryPassword(recovery)
        assertEquals(Screen.SetPassword.route, NavigationManager(context).determineStartDestination())
    }

    @Test fun existingInstallMustAuthenticateBeforeRecoveryEnrollment() {
        seedAuthentication()
        repository.setLockType(PreferencesRepository.LOCK_TYPE_PIN)
        assertFalse(repository.hasRecoveryPassword())
        assertEquals(Screen.PasswordOverlay.route, NavigationManager(context).determineStartDestination())
    }

    @Test fun verifiedLegacyRecoveryRequiresRecoveryEnrollmentAndPreservesProtectedApps() {
        seedAuthentication()
        repository.addLockedApp("protected.app")
        AppLockManager.unlockApp("protected.app")
        assertTrue(repository.recoverLegacyAuthenticationAfterDeviceVerification())
        assertNull(repository.getPassword())
        assertNull(repository.getPattern())
        assertFalse(repository.hasAuthentication())
        assertFalse(repository.isBiometricAuthEnabled())
        assertTrue(data.getValue("totp_secure_storage").isEmpty())
        assertTrue(repository.isAppLocked("protected.app"))
        assertTrue(AppLockManager.appUnlockTimes.isEmpty())
        assertEquals("", AppLockManager.temporarilyUnlockedApp)
        assertEquals(Screen.RecoverySetup.route, NavigationManager(context).determineStartDestination())
    }

    @Test fun deviceCredentialFallbackCannotReplaceAnEnrolledRecoveryPassword() {
        seedAuthentication()
        repository.setRecoveryPassword(recovery)
        val before = data.mapValues { it.value.toMap() }
        assertFalse(repository.recoverLegacyAuthenticationAfterDeviceVerification())
        assertEquals(before, data)
    }

    @Test fun returningFromDeviceVerificationKeepsTheRecoveryRoute() {
        assertTrue(NavigationManager(context).shouldSkipPasswordCheck(Screen.DeviceCredentialRecovery.route))
        assertFalse(NavigationManager(context).shouldSkipPasswordCheck(Screen.Main.route))
    }

    // An in-memory SharedPreferences boundary keeps these tests independent of a device and Keystore.
    private fun preferences(values: MutableMap<String, Any>): SharedPreferences {
        val editor = mock(SharedPreferences.Editor::class.java) { call ->
            when {
                call.method.name.startsWith("put") -> {
                    values[call.getArgument(0)] = call.getArgument(1)
                    call.mock
                }
                call.method.name == "remove" -> { values.remove(call.getArgument<String>(0)); call.mock }
                call.method.name == "commit" -> true
                else -> null
            }
        }
        return mock(SharedPreferences::class.java) { call ->
            when (call.method.name) {
                "edit" -> editor
                "contains" -> values.containsKey(call.getArgument<String>(0))
                "getAll" -> values.toMap()
                else -> if (call.method.name.startsWith("get")) values[call.getArgument<String>(0)] ?: call.getArgument(1) else null
            }
        }
    }
}
