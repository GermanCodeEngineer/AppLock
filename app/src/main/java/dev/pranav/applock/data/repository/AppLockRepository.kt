package dev.pranav.applock.data.repository

import android.content.Context
import dev.pranav.applock.data.manager.BackendServiceManager
import dev.pranav.applock.features.totp.data.TotpSecretStore
import dev.pranav.applock.features.totp.domain.TotpService
import dev.pranav.applock.features.recovery.domain.RecoveryPassword
import dev.pranav.applock.services.AppLockManager

/**
 * Main repository that coordinates between different specialized repositories and managers.
 * Provides a unified interface for all app lock functionality.
 */
class AppLockRepository(private val context: Context) {

    private val preferencesRepository = PreferencesRepository(context)
    private val totpSecretStore = TotpSecretStore(context)
    private val lockedAppsRepository = LockedAppsRepository(context)
    private val backendServiceManager = BackendServiceManager()

    fun getLockedApps(): Set<String> = lockedAppsRepository.getLockedApps()
    fun addLockedApp(packageName: String) {
        lockedAppsRepository.addLockedApp(packageName)
        AppLockManager.clearAppUnlockState(packageName)
    }

    fun addMultipleLockedApps(packageNames: Set<String>) {
        lockedAppsRepository.addMultipleLockedApps(packageNames)
        packageNames.forEach(AppLockManager::clearAppUnlockState)
    }
    fun removeLockedApp(packageName: String) = lockedAppsRepository.removeLockedApp(packageName)
    fun isAppLocked(packageName: String): Boolean = lockedAppsRepository.isAppLocked(packageName)

    fun getTriggerExcludedApps(): Set<String> = lockedAppsRepository.getTriggerExcludedApps()
    fun addTriggerExcludedApp(packageName: String) =
        lockedAppsRepository.addTriggerExcludedApp(packageName)

    fun removeTriggerExcludedApp(packageName: String) =
        lockedAppsRepository.removeTriggerExcludedApp(packageName)

    fun isAppTriggerExcluded(packageName: String): Boolean =
        lockedAppsRepository.isAppTriggerExcluded(packageName)

    fun getAntiUninstallApps(): Set<String> = lockedAppsRepository.getAntiUninstallApps()
    fun addAntiUninstallApp(packageName: String) =
        lockedAppsRepository.addAntiUninstallApp(packageName)

    fun removeAntiUninstallApp(packageName: String) =
        lockedAppsRepository.removeAntiUninstallApp(packageName)

    fun isAppAntiUninstall(packageName: String): Boolean =
        lockedAppsRepository.isAppAntiUninstall(packageName)

    fun getPassword(): String? = preferencesRepository.getPassword()
    fun setPassword(password: String) = preferencesRepository.setPassword(password)
    fun validatePassword(inputPassword: String): Boolean =
        preferencesRepository.validatePassword(inputPassword)

    // A configured TOTP lock remains configured even if its Keystore key becomes unavailable.
    // In that case the user must recover, rather than being sent to unauthenticated setup.
    fun hasAuthentication(): Boolean = when (getLockType()) {
        PreferencesRepository.LOCK_TYPE_TOTP -> true
        PreferencesRepository.LOCK_TYPE_PATTERN -> getPattern() != null
        else -> getPassword() != null
    }

    fun validateTotp(code: String): Boolean = totpSecretStore.verifyCode(code)

    fun enableTotp(secret: String, code: String): Boolean {
        if (!hasRecoveryPassword() || !TotpService.verify(secret, code)) return false
        if (!totpSecretStore.saveSecret(secret)) return false
        setLockType(PreferencesRepository.LOCK_TYPE_TOTP)
        clearUnlockSessions()
        return true
    }

    fun hasRecoveryPassword(): Boolean = preferencesRepository.hasRecoveryPassword()

    fun setRecoveryPassword(password: String): Boolean =
        preferencesRepository.saveRecoveryPasswordHash(RecoveryPassword.hash(password))

    fun recoverAuthentication(password: String): Boolean {
        val hash = preferencesRepository.getRecoveryPasswordHash() ?: return false
        if (!RecoveryPassword.verify(password, hash)) return false
        resetAuthentication()
        return true
    }

    /** Only call from a successful Android device-credential confirmation result. */
    fun recoverLegacyAuthenticationAfterDeviceVerification(): Boolean {
        // Once enrolled, the recovery password must be used instead of this migration fallback.
        if (hasRecoveryPassword()) return false
        resetAuthentication()
        return true
    }

    private fun resetAuthentication() {
        preferencesRepository.clearPrimaryCredentials()
        totpSecretStore.clear()
        clearUnlockSessions()
    }

    private fun clearUnlockSessions() {
        getLockedApps().forEach(AppLockManager::clearAppUnlockState)
        AppLockManager.clearTemporarilyUnlockedApp()
        AppLockManager.appUnlockTimes.clear()
        AppLockManager.reportBiometricAuthFinished()
    }

    fun getPattern(): String? = preferencesRepository.getPattern()
    fun setPattern(pattern: String) = preferencesRepository.setPattern(pattern)
    fun validatePattern(inputPattern: String): Boolean =
        preferencesRepository.validatePattern(inputPattern)

    fun setLockType(lockType: String) = preferencesRepository.setLockType(lockType)
    fun getLockType(): String = preferencesRepository.getLockType()

    fun setBiometricAuthEnabled(enabled: Boolean) =
        preferencesRepository.setBiometricAuthEnabled(enabled)

    fun isBiometricAuthEnabled(): Boolean = preferencesRepository.isBiometricAuthEnabled()

    fun setUseMaxBrightness(enabled: Boolean) = preferencesRepository.setUseMaxBrightness(enabled)
    fun shouldUseMaxBrightness(): Boolean = preferencesRepository.shouldUseMaxBrightness()
    fun setDisableHaptics(enabled: Boolean) = preferencesRepository.setDisableHaptics(enabled)
    fun shouldDisableHaptics(): Boolean = preferencesRepository.shouldDisableHaptics()
    fun setShowSystemApps(enabled: Boolean) = preferencesRepository.setShowSystemApps(enabled)
    fun shouldShowSystemApps(): Boolean = preferencesRepository.shouldShowSystemApps()

    fun setAntiUninstallEnabled(enabled: Boolean) =
        preferencesRepository.setAntiUninstallEnabled(enabled)

    fun isAntiUninstallEnabled(): Boolean = preferencesRepository.isAntiUninstallEnabled()
    fun setProtectEnabled(enabled: Boolean) = preferencesRepository.setProtectEnabled(enabled)
    fun isProtectEnabled(): Boolean = preferencesRepository.isProtectEnabled()

    fun setUnlockTimeDuration(minutes: Int) = preferencesRepository.setUnlockTimeDuration(minutes)
    fun getUnlockTimeDuration(): Int = preferencesRepository.getUnlockTimeDuration()
    fun setAutoUnlockEnabled(enabled: Boolean) = preferencesRepository.setAutoUnlockEnabled(enabled)
    fun isAutoUnlockEnabled(): Boolean = preferencesRepository.isAutoUnlockEnabled()

    fun setBackendImplementation(backend: BackendImplementation) =
        preferencesRepository.setBackendImplementation(backend)

    fun getBackendImplementation(): BackendImplementation =
        preferencesRepository.getBackendImplementation()

    fun isShowCommunityLink(): Boolean = preferencesRepository.isShowCommunityLink()
    fun setCommunityLinkShown(shown: Boolean) = preferencesRepository.setCommunityLinkShown(shown)
    fun isShowDonateLink(): Boolean = preferencesRepository.isShowDonateLink(context)
    fun setShowDonateLink(show: Boolean) = preferencesRepository.setShowDonateLink(context, show)

    fun isLoggingEnabled(): Boolean = preferencesRepository.isLoggingEnabled()
    fun setLoggingEnabled(enabled: Boolean) = preferencesRepository.setLoggingEnabled(enabled)

    fun setActiveBackend(backend: BackendImplementation) =
        backendServiceManager.setActiveBackend(backend)

    companion object {
        private const val TAG = "AppLockRepository"

        fun shouldStartService(repository: AppLockRepository, serviceClass: Class<*>): Boolean {
            return repository.backendServiceManager.shouldStartService(
                serviceClass,
                repository.getBackendImplementation()
            )
        }
    }
}

enum class BackendImplementation {
    ACCESSIBILITY,
    USAGE_STATS,
    SHIZUKU
}
