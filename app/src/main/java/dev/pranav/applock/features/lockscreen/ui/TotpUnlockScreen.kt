package dev.pranav.applock.features.lockscreen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.ui.icons.Backspace

/** Shared by all lock surfaces. Recovery is supplied only by the app's navigation host. */
@Composable
fun TotpUnlockScreen(
    onCodeAttempt: (String) -> Boolean,
    modifier: Modifier = Modifier,
    lockedAppName: String? = null,
    onClose: (() -> Unit)? = null,
    onBiometricAuth: (() -> Unit)? = null
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val repository = LocalContext.current.appLockRepository()
    if (onClose != null) BackHandler(onBack = onClose)
    fun submit() {
        if (code.length == 6) {
            error = !onCodeAttempt(code)
            code = ""
        }
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.safeDrawingPadding().imePadding().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.totp_unlock_title), style = MaterialTheme.typography.headlineSmall)
            lockedAppName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.totp_unlock_description))
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6 && it.all { char -> char in '0'..'9' }) { code = it; error = false } },
                label = { Text(stringResource(R.string.totp_code_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = error
            )
            if (error) Text(stringResource(R.string.totp_invalid_code), color = MaterialTheme.colorScheme.error)
            val keyClick: (String) -> Unit = { key ->
                when (key) {
                    "backspace" -> code = code.dropLast(1)
                    "proceed" -> submit()
                    else -> if (code.length < 6) { code += key; error = false }
                }
            }
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { keys ->
                KeypadRow(repository.shouldDisableHaptics(), keys, onKeyClick = keyClick, buttonSize = 64.dp, buttonSpacing = 12.dp)
            }
            KeypadRow(
                repository.shouldDisableHaptics(), listOf("backspace", "0", "proceed"),
                icons = listOf(Backspace, null, Icons.Default.Check), onKeyClick = keyClick,
                buttonSize = 64.dp, buttonSpacing = 12.dp
            )
            if (onBiometricAuth != null) TextButton(onClick = onBiometricAuth) { Text(stringResource(R.string.totp_use_biometrics)) }
            if (onClose != null) TextButton(onClick = onClose) { Text(stringResource(R.string.cancel_button)) }
        }
    }
}
