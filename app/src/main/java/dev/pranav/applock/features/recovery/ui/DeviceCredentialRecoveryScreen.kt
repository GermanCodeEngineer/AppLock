package dev.pranav.applock.features.recovery.ui

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pranav.applock.ui.components.AuthenticationShortcutButton
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.appLockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Migration recovery for installations that predate recovery passwords; never used in overlays. */
@Suppress("DEPRECATION") // Keyguard confirmation supports device credentials on all supported APIs (26+).
@Composable
fun DeviceCredentialRecoveryScreen(onCompleted: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = context.appLockRepository()
    val keyguard = remember(context) { context.getSystemService(KeyguardManager::class.java) }
    val scope = rememberCoroutineScope()
    var challengePending by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val requested = challengePending
        challengePending = false
        if (requested && result.resultCode == Activity.RESULT_OK) {
            busy = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching { repository.recoverLegacyAuthenticationAfterDeviceVerification() }.getOrDefault(false)
                }
                busy = false
                if (success) onCompleted() else error = R.string.recovery_device_reset_error
            }
        } else {
            error = R.string.recovery_device_cancelled
        }
    }
    BackHandler { if (!busy && !challengePending) onBack() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthenticationShortcutButton(enabled = !busy)
            Text(stringResource(R.string.recovery_unlock_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.recovery_legacy_description))
            if (keyguard?.isDeviceSecure != true) {
                Text(stringResource(R.string.recovery_device_unavailable), color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (busy) CircularProgressIndicator()
            Button(
                enabled = !busy && !challengePending && keyguard?.isDeviceSecure == true && !repository.hasRecoveryPassword(),
                onClick = {
                    val intent = keyguard?.createConfirmDeviceCredentialIntent(
                        context.getString(R.string.recovery_unlock_title),
                        context.getString(R.string.recovery_device_prompt)
                    )
                    if (intent == null) {
                        error = R.string.recovery_device_unavailable
                    } else {
                        challengePending = true
                        error = null
                        try {
                            launcher.launch(intent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            challengePending = false
                            error = R.string.recovery_device_unavailable
                        }
                    }
                }
            ) { Text(stringResource(R.string.recovery_device_reset_button)) }
            TextButton(enabled = !busy && !challengePending, onClick = onBack) { Text(stringResource(R.string.cancel_button)) }
        }
    }
}
