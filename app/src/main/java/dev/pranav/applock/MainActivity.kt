package dev.pranav.applock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.compose.rememberNavController
import dev.pranav.applock.core.navigation.AppNavHost
import dev.pranav.applock.core.navigation.NavigationManager
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.core.utils.AuthenticationShortcut
import dev.pranav.applock.core.utils.AuthenticationShortcutHost
import dev.pranav.applock.core.utils.ShortcutLaunchResult
import dev.pranav.applock.ui.theme.AppLockTheme

class MainActivity : FragmentActivity(), AuthenticationShortcutHost {

    private lateinit var navigationManager: NavigationManager
    private var returningFromAuthenticationShortcut = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        returningFromAuthenticationShortcut = savedInstanceState?.getBoolean("authentication_shortcut_pending") ?: false
        enableEdgeToEdge()

        navigationManager = NavigationManager(this)

        setContent {
            AppLockTheme {
                val navController = rememberNavController()
                val startDestination = navigationManager.determineStartDestination()

                AppNavHost(
                    navController = navController,
                    startDestination = startDestination
                )

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    handleOnResume(navController)
                }
            }
        }
    }

    private fun handleOnResume(navController: androidx.navigation.NavHostController) {
        if (returningFromAuthenticationShortcut) {
            // The shortcut is available only inside authentication UI. Resume that same
            // challenge (including setup/change drafts and admin dialogs) without rerouting.
            returningFromAuthenticationShortcut = false
            return
        }
        val currentRoute = navController.currentDestination?.route

        if (navigationManager.shouldSkipPasswordCheck(currentRoute)) {
            return
        }

        if (currentRoute != Screen.PasswordOverlay.route && currentRoute != Screen.SetPassword.route && currentRoute != Screen.SetPasswordPattern.route && currentRoute != Screen.SetPasswordAlphanumeric.route) {
            navController.navigate(Screen.PasswordOverlay.route)
        }
    }

    override fun openAuthenticationShortcut(): ShortcutLaunchResult {
        returningFromAuthenticationShortcut = true
        return AuthenticationShortcut.launch(this).also {
            if (it != ShortcutLaunchResult.OPENED) returningFromAuthenticationShortcut = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("authentication_shortcut_pending", returningFromAuthenticationShortcut)
        super.onSaveInstanceState(outState)
    }
}


