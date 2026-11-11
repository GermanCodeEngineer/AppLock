package dev.pranav.applock

import android.app.KeyguardManager
import android.app.UiAutomation
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.data.repository.PreferencesRepository
import dev.pranav.applock.features.totp.domain.TotpService
import dev.pranav.applock.features.appintro.domain.AppIntroManager
import dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity
import dev.pranav.applock.services.AppLockManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticationDeviceTest {
    @Test fun encryptedEnrollmentReplayProtectionAndRecovery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // This destructive lifecycle test runs only in an isolated temporary test package.
        assumeTrue(context.packageName.endsWith(".authtest"))
        val repository = AppLockRepository(context)
        val recovery = "device test recovery passphrase"
        val secret = TotpService.generateSecret()
        val code = currentCode(secret)

        assertTrue(repository.setRecoveryPassword(recovery))
        repository.setPassword("123456")
        repository.setPattern("01234")
        repository.setBiometricAuthEnabled(true)
        repository.addLockedApp("example.protected")
        assertTrue(repository.enableTotp(secret, code))
        assertEquals(PreferencesRepository.LOCK_TYPE_TOTP, repository.getLockType())

        val stored = context.getSharedPreferences("totp_secure_storage", 0).all
        assertFalse(stored.values.contains(secret))
        assertTrue(stored.containsKey("secret_ciphertext"))
        assertTrue(stored.containsKey("secret_iv"))
        assertTrue(repository.validateTotp(code))
        assertFalse(repository.validateTotp(code))
        assertFalse(AppLockRepository(context).validateTotp(code))
        assertFalse(repository.recoverAuthentication("incorrect recovery password"))
        assertTrue(repository.hasAuthentication())

        assertTrue(repository.recoverAuthentication(recovery))
        assertNull(repository.getPassword())
        assertNull(repository.getPattern())
        assertFalse(repository.isBiometricAuthEnabled())
        assertFalse(repository.hasAuthentication())
        assertFalse(repository.validateTotp(code))
        assertTrue(repository.isAppLocked("example.protected"))
        assertTrue(repository.hasRecoveryPassword())
        assertTrue(context.getSharedPreferences("totp_secure_storage", 0).all.isEmpty())
    }

    @Test fun recoveryIsOnlyInTheAppAndResetRequiresSetupAgain() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".authtest"))
        assumeTrue(!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val repository = AppLockRepository(context)
        val recovery = "temporary UI recovery passphrase"
        repository.setRecoveryPassword(recovery)
        repository.recoverAuthentication(recovery)
        AppIntroManager.markIntroAsCompleted(context)
        val ui = AuthenticationUi()

        var activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            ui.click(context.getString(R.string.totp_use_button))
            val secret = ui.awaitNode { it.text?.toString()?.matches(Regex("[A-Z2-7]{4}( [A-Z2-7]{4}){7}")) == true }
                .text.toString().replace(" ", "")
            ui.enter(currentCode(secret))
            ui.click(context.getString(R.string.totp_enable_button))
            for (attempt in 0 until 100) {
                if (repository.getLockType() == PreferencesRepository.LOCK_TYPE_TOTP) break
                Thread.sleep(50)
            }
            assertEquals(PreferencesRepository.LOCK_TYPE_TOTP, repository.getLockType())
            instrumentation.runOnMainSync { activity.finish() }
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ui.awaitNode { it.text?.toString() == context.getString(R.string.recovery_use_button) }

            val overlay = instrumentation.startActivitySync(Intent(context, PasswordOverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("locked_package", context.packageName))
            try {
                ui.awaitNode { it.text?.toString() == context.getString(R.string.totp_unlock_title) }
                assertFalse(ui.nodes().any { it.text?.toString() == context.getString(R.string.recovery_use_button) })
            } finally { instrumentation.runOnMainSync { overlay.finish() } }

            ui.click(context.getString(R.string.recovery_use_button))
            ui.enter(recovery, password = true)
            ui.click(context.getString(R.string.recovery_reset_button))
            ui.awaitNode { it.text?.toString() == context.getString(R.string.totp_use_button) }
            assertFalse(repository.hasAuthentication())
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun legacyInstallationRequiresDeviceVerificationAndCancellationPreservesCredentials() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".authtest"))
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assumeTrue(!keyguard.isKeyguardLocked)
        val repository = AppLockRepository(context)
        val recovery = "temporary legacy test recovery"
        repository.setRecoveryPassword(recovery)
        assertTrue(repository.recoverAuthentication(recovery))
        // Simulate an installation created before recovery-password enrollment existed.
        context.getSharedPreferences("app_lock_prefs", 0).edit().remove("recovery_password_hash").commit()
        repository.setPassword("987654")
        repository.setLockType(PreferencesRepository.LOCK_TYPE_PIN)
        AppIntroManager.markIntroAsCompleted(context)
        val originalPassword = repository.getPassword()
        val ui = AuthenticationUi()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            ui.click(context.getString(R.string.recovery_forgot_password_button))
            ui.awaitNode { it.text?.toString() == context.getString(R.string.recovery_legacy_description) }
            assertEquals(originalPassword, repository.getPassword())
            if (keyguard.isDeviceSecure) {
                ui.click(context.getString(R.string.recovery_device_reset_button))
                ui.awaitNode { it.packageName?.toString() in setOf("com.android.settings", "com.android.systemui") }
                assertEquals(originalPassword, repository.getPassword())
                assertTrue(ui.automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                ui.awaitNode { it.text?.toString() == context.getString(R.string.recovery_device_cancelled) }
            } else {
                ui.awaitNode { it.text?.toString() == context.getString(R.string.recovery_device_unavailable) }
            }
            assertEquals(originalPassword, repository.getPassword())
            assertTrue(repository.hasAuthentication())
            assertFalse(repository.hasRecoveryPassword())
            ui.click(context.getString(R.string.cancel_button))
            ui.awaitNode { it.text?.toString() == context.getString(R.string.recovery_forgot_password_button) }
            if (keyguard.isDeviceSecure) {
                // Exercise the success callback without entering or accessing the owner's device PIN.
                val confirmationAction = keyguard.createConfirmDeviceCredentialIntent("Test recovery", null)?.action
                assertNotNull(confirmationAction)
                val monitor = object : android.app.Instrumentation.ActivityMonitor() {
                    override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? =
                        if (intent.action == confirmationAction) {
                            android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, null)
                        } else null
                }
                repository.addLockedApp("example.legacy.protected")
                instrumentation.addMonitor(monitor)
                try {
                    ui.click(context.getString(R.string.recovery_forgot_password_button))
                    ui.click(context.getString(R.string.recovery_device_reset_button))
                    ui.awaitNode { it.text?.toString() == context.getString(R.string.recovery_setup_title) }
                    assertFalse(repository.hasAuthentication())
                    assertFalse(repository.hasRecoveryPassword())
                    assertTrue(repository.isAppLocked("example.legacy.protected"))
                } finally { instrumentation.removeMonitor(monitor) }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun shortcutReturnsToEachUnlockMethodWithoutUnlockingProtectedApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".authtest"))
        assumeTrue(!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val target = instrumentation.context.packageName
        assertNotNull(context.packageManager.getLaunchIntentForPackage(target))
        val preferences = PreferencesRepository(context)
        val repository = AppLockRepository(context)
        val protectedPackage = "example.shortcut.protected"
        repository.setPassword("123456")
        repository.setPattern("01234")
        repository.setRecoveryPassword("shortcut test recovery passphrase")
        repository.setBiometricAuthEnabled(false)
        repository.removeLockedApp(target)
        repository.addLockedApp(protectedPackage)
        AppIntroManager.markIntroAsCompleted(context)
        preferences.setAuthenticationShortcutPackage(target)
        val ui = AuthenticationUi()
        val label = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(target, 0)).toString()
        val description = context.getString(R.string.authentication_shortcut_open, label)
        try {
            for (lockType in listOf(PreferencesRepository.LOCK_TYPE_PIN, PreferencesRepository.LOCK_TYPE_PASSWORD,
                PreferencesRepository.LOCK_TYPE_PATTERN, PreferencesRepository.LOCK_TYPE_TOTP)) {
                // TOTP stays configured without a secret; these tests never submit a credential.
                repository.setLockType(lockType)
                for (overlay in listOf(false, true)) {
                    val activityClass = if (overlay) PasswordOverlayActivity::class.java else MainActivity::class.java
                    val activity = instrumentation.startActivitySync(Intent(context, activityClass)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("locked_package", protectedPackage))
                    try {
                        ui.clickDescription(description)
                        ui.awaitNode { it.packageName?.toString() == target }
                        ui.automation.waitForIdle(500, 5000)
                        instrumentation.runOnMainSync { assertFalse("Authentication activity was destroyed for $lockType", activity.isFinishing || activity.isDestroyed) }
                        assertTrue(ui.automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                        ui.awaitNode { it.contentDescription?.toString() == description }
                        assertTrue(repository.isAppLocked(protectedPackage))
                        assertFalse(AppLockManager.isAppTemporarilyUnlocked(protectedPackage))
                        assertFalse(AppLockManager.appUnlockTimes.containsKey(protectedPackage))
                    } catch (error: AssertionError) {
                        throw AssertionError("Shortcut round trip failed for $lockType (overlay=$overlay, destroyed=${activity.isDestroyed}, foreground=${ui.nodes().firstOrNull()?.packageName})", error)
                    } finally { instrumentation.runOnMainSync { activity.finish() } }
                }
            }
        } finally {
            preferences.setAuthenticationShortcutPackage(null)
            repository.removeLockedApp(protectedPackage)
        }
    }

    private class AuthenticationUi {
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

        fun nodes(): List<AccessibilityNodeInfo> = buildList {
            fun visit(node: AccessibilityNodeInfo) {
                add(node)
                for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it) }
            }
            automation.rootInActiveWindow?.let { visit(it) }
        }
        fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
            repeat(100) {
                nodes().firstOrNull(predicate)?.let { return it }
                Thread.sleep(100)
            }
            throw AssertionError("Expected authentication control did not appear")
        }
        fun click(text: String) {
            repeat(100) {
                var node = nodes().firstOrNull { it.text?.toString() == text }
                while (node != null && !node.isClickable && node.parent != null) node = node.parent
                if (node != null && node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                Thread.sleep(100)
            }
            throw AssertionError("Authentication button did not become clickable: $text")
        }
        fun clickDescription(description: String) {
            repeat(100) {
                var node = nodes().firstOrNull { it.contentDescription?.toString() == description }
                while (node != null && !node.isClickable && node.parent != null) node = node.parent
                if (node != null && node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                Thread.sleep(100)
            }
            throw AssertionError("Authentication shortcut did not become clickable: $description")
        }
        fun enter(value: String, password: Boolean = false) {
            val field = awaitNode { it.isEditable && it.isEnabled && it.isPassword == password }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }))
        }

    }

    // Independent TOTP reference calculation, using the 160-bit key generated for this test.
    private fun currentCode(secret: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val bits = secret.map { alphabet.indexOf(it).toString(2).padStart(5, '0') }.joinToString("")
        val key = bits.chunked(8).map { it.toInt(2).toByte() }.toByteArray()
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(System.currentTimeMillis() / 30000).array())
        val offset = hash.last().toInt() and 15
        return ((java.nio.ByteBuffer.wrap(hash, offset, 4).int and Int.MAX_VALUE) % 1000000).toString().padStart(6, '0')
    }
}
