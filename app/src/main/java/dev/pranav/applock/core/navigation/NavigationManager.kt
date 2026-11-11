package dev.pranav.applock.core.navigation

import android.content.Context
import dev.pranav.applock.features.appintro.domain.AppIntroManager
import dev.pranav.applock.data.repository.AppLockRepository

/**
 * Manages navigation logic and routing decisions for the application.
 * Centralizes navigation-related business logic for better maintainability.
 */
class NavigationManager(private val context: Context) {

    /**
     * Determines the appropriate starting destination based on app state.
     */
    fun determineStartDestination(): String {
        return when {
            shouldShowAppIntro() -> Screen.AppIntro.route
            !isPasswordSet() && !AppLockRepository(context).hasRecoveryPassword() -> Screen.RecoverySetup.route
            !isPasswordSet() -> Screen.SetPassword.route
            else -> Screen.PasswordOverlay.route
        }
    }

    /**
     * Checks if password verification should be skipped for the given route.
     */
    fun shouldSkipPasswordCheck(currentRoute: String?): Boolean {
        return currentRoute in ROUTES_THAT_SKIP_PASSWORD_CHECK
    }

    private fun shouldShowAppIntro(): Boolean {
        return AppIntroManager.shouldShowIntro(context)
    }

    private fun isPasswordSet(): Boolean {
        return AppLockRepository(context).hasAuthentication()
    }

    companion object {
        private val ROUTES_THAT_SKIP_PASSWORD_CHECK = setOf(
            Screen.AppIntro.route,
            Screen.SetPassword.route,
            Screen.SetPasswordPattern.route,
            Screen.SetPasswordAlphanumeric.route,
            Screen.TotpSetup.route,
            Screen.RecoverySetup.route,
            Screen.RecoveryUnlock.route,
            Screen.DeviceCredentialRecovery.route
        )
    }
}
