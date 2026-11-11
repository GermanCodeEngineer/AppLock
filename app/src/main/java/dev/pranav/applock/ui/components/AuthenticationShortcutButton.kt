package dev.pranav.applock.ui.components

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.AuthenticationShortcut
import dev.pranav.applock.core.utils.AuthenticationShortcutHost
import dev.pranav.applock.core.utils.ShortcutLaunchResult
import dev.pranav.applock.data.repository.PreferencesRepository

/** Accessibility windows supply a handoff to a real authentication activity. */
val LocalAuthenticationShortcutLauncher = staticCompositionLocalOf<(() -> ShortcutLaunchResult)?> { null }

@Composable
fun AuthenticationShortcutButton(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val overlayLauncher = LocalAuthenticationShortcutLauncher.current
    val packageName = remember(context) { PreferencesRepository(context).getAuthenticationShortcutPackage() }
        ?: return
    val label = remember(context, packageName) {
        runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
    IconButton(modifier = modifier, enabled = enabled, onClick = {
        val result = when {
            overlayLauncher != null -> overlayLauncher()
            activity is AuthenticationShortcutHost -> activity.openAuthenticationShortcut()
            activity != null -> AuthenticationShortcut.launch(activity)
            else -> ShortcutLaunchResult.UNAVAILABLE
        }
        if (result != ShortcutLaunchResult.OPENED) {
            Toast.makeText(context, if (result == ShortcutLaunchResult.LOCKED) R.string.authentication_shortcut_locked
                else R.string.authentication_shortcut_unavailable, Toast.LENGTH_LONG).show()
        }
    }) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.authentication_shortcut_open, label))
    }
}
