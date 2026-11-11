package dev.pranav.applock.core.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

class AuthenticationShortcutTest {
    private val activity = mock(Activity::class.java)
    private val preferences = mock(SharedPreferences::class.java)
    private val packageManager = mock(PackageManager::class.java)
    private val intent = mock(Intent::class.java)
    private val target = "example.authenticator"

    init {
        `when`(activity.packageName).thenReturn("dev.pranav.applock")
        `when`(activity.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences)
        `when`(preferences.getString("authentication_shortcut_package", null)).thenReturn(target)
        `when`(preferences.getStringSet("locked_apps", emptySet())).thenReturn(emptySet())
        `when`(activity.packageManager).thenReturn(packageManager)
        `when`(packageManager.getLaunchIntentForPackage(target)).thenReturn(intent)
    }

    @Test fun launchesFromCallingActivityWithoutLauncherTaskFlags() {
        assertEquals(ShortcutLaunchResult.OPENED, AuthenticationShortcut.launch(activity))
        val order = inOrder(intent, activity)
        order.verify(intent).setFlags(0)
        order.verify(activity).startActivity(intent)
        verify(preferences, never()).edit()
    }

    @Test fun targetProtectedAfterSelectionCannotBeOpened() {
        `when`(preferences.getStringSet("locked_apps", emptySet())).thenReturn(setOf(target))
        assertEquals(ShortcutLaunchResult.LOCKED, AuthenticationShortcut.launch(activity))
        verifyNoInteractions(packageManager)
        verify(activity, never()).startActivity(any(Intent::class.java))
        verify(preferences, never()).edit()
    }

    @Test fun missingSelectionDoesNotLaunchAnything() {
        `when`(preferences.getString("authentication_shortcut_package", null)).thenReturn(null)
        assertEquals(ShortcutLaunchResult.UNAVAILABLE, AuthenticationShortcut.launch(activity))
        verifyNoInteractions(packageManager)
        verify(activity, never()).startActivity(any(Intent::class.java))
    }

    @Test fun appCannotLaunchItselfAsShortcut() {
        `when`(preferences.getString("authentication_shortcut_package", null)).thenReturn("dev.pranav.applock")
        assertEquals(ShortcutLaunchResult.UNAVAILABLE, AuthenticationShortcut.launch(activity))
        verifyNoInteractions(packageManager)
    }

    @Test fun uninstalledOrNonLaunchableTargetIsUnavailable() {
        `when`(packageManager.getLaunchIntentForPackage(target)).thenReturn(null)
        assertEquals(ShortcutLaunchResult.UNAVAILABLE, AuthenticationShortcut.launch(activity))
        verify(activity, never()).startActivity(any(Intent::class.java))
    }

    @Test fun targetRemovedBetweenResolutionAndLaunchIsHandled() {
        doThrow(mock(ActivityNotFoundException::class.java)).`when`(activity).startActivity(intent)
        assertEquals(ShortcutLaunchResult.UNAVAILABLE, AuthenticationShortcut.launch(activity))
    }

    @Test fun deniedLaunchIsHandled() {
        doThrow(SecurityException("Disabled activity")).`when`(activity).startActivity(intent)
        assertEquals(ShortcutLaunchResult.UNAVAILABLE, AuthenticationShortcut.launch(activity))
    }
}
