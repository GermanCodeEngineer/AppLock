package dev.pranav.applock.core.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import dev.pranav.applock.data.repository.LockedAppsRepository
import dev.pranav.applock.data.repository.PreferencesRepository

enum class ShortcutLaunchResult { OPENED, UNAVAILABLE, LOCKED }

/** Activity hosts can preserve their authentication screen while the shortcut is in front. */
interface AuthenticationShortcutHost {
    fun openAuthenticationShortcut(): ShortcutLaunchResult
}

object AuthenticationShortcut {
    fun launch(activity: Activity): ShortcutLaunchResult {
        val packageName = PreferencesRepository(activity).getAuthenticationShortcutPackage()
            ?: return ShortcutLaunchResult.UNAVAILABLE
        if (packageName == activity.packageName) return ShortcutLaunchResult.UNAVAILABLE
        // A shortcut must never provide an unauthenticated route into a protected app.
        if (LockedAppsRepository(activity).isAppLocked(packageName)) return ShortcutLaunchResult.LOCKED
        return try {
            val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
                ?: return ShortcutLaunchResult.UNAVAILABLE
            // PackageManager's launcher intent normally contains NEW_TASK. Start from this
            // activity instead so compatible target activities sit above the authentication UI.
            intent.flags = 0
            activity.startActivity(intent)
            ShortcutLaunchResult.OPENED
        } catch (_: ActivityNotFoundException) {
            ShortcutLaunchResult.UNAVAILABLE
        } catch (_: SecurityException) {
            ShortcutLaunchResult.UNAVAILABLE
        }
    }
}
