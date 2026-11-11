package dev.pranav.applock.features.lockscreen.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pranav.applock.ui.components.AuthenticationShortcutButton
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.features.qr.ui.QrScannerDialog
import dev.pranav.applock.ui.icons.Backspace

/** Recovery is supplied only by the app's navigation host. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TotpUnlockScreen(
    onCodeAttempt: (String) -> Boolean,
    modifier: Modifier = Modifier,
    lockedAppName: String? = null,
    onClose: (() -> Unit)? = null,
    onBiometricAuth: (() -> Unit)? = null,
    onScanFromOverlay: (() -> Unit)? = null,
    openScannerInitially: Boolean = false,
    onRecovery: (() -> Unit)? = null,
    recoveryLabel: String? = null
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var invalidInput by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(openScannerInitially) }
    val context = LocalContext.current
    val repository = context.appLockRepository()
    if (onClose != null) BackHandler(onBack = onClose)
    fun acceptCode(value: String?) {
        val input = value?.trim().orEmpty()
        if (input.length == 6 && input.all { it in '0'..'9' }) {
            code = input
            error = false
            invalidInput = false
        } else {
            invalidInput = true
        }
    }
    fun submit() {
        if (code.length == 6) {
            error = !onCodeAttempt(code)
            invalidInput = false
            code = ""
        }
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val keypadSize = minOf(64.dp, (maxHeight / 10), (maxWidth - 64.dp) / 3)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.totp_unlock_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                lockedAppName?.let { Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.totp_unlock_description), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                // Read-only keeps Android's keyboard closed; the built-in keypad enters the code.
                OutlinedTextField(
                    value = code, onValueChange = {}, readOnly = true,
                    label = { Text(stringResource(R.string.totp_code_label)) },
                    singleLine = true, isError = error || invalidInput,
                    modifier = Modifier.widthIn(max = 280.dp)
                )
                if (error || invalidInput) Text(
                    stringResource(if (invalidInput) R.string.totp_input_invalid else R.string.totp_invalid_code),
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center
                )
                FlowRow(horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
                    AuthenticationShortcutButton()
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        acceptCode(clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
                    }) { Text(stringResource(R.string.totp_paste_code)) }
                    if (context is Activity || onScanFromOverlay != null) TextButton(onClick = {
                        if (onScanFromOverlay != null) onScanFromOverlay() else showScanner = true
                    }) {
                        Text(stringResource(R.string.scan_qr_code))
                    }
                }
                val keyClick: (String) -> Unit = { key ->
                    when (key) {
                        "backspace" -> { code = code.dropLast(1); invalidInput = false }
                        "proceed" -> submit()
                        else -> if (code.length < 6) { code += key; error = false; invalidInput = false }
                    }
                }
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { keys ->
                    KeypadRow(repository.shouldDisableHaptics(), keys, onKeyClick = keyClick, buttonSize = keypadSize, buttonSpacing = 12.dp)
                    Spacer(Modifier.height(8.dp))
                }
                KeypadRow(repository.shouldDisableHaptics(), listOf("backspace", "0", "proceed"),
                    icons = listOf(Backspace, null, Icons.Default.Check), onKeyClick = keyClick,
                    buttonSize = keypadSize, buttonSpacing = 12.dp)
                if (onBiometricAuth != null) TextButton(onClick = onBiometricAuth) { Text(stringResource(R.string.totp_use_biometrics)) }
                if (onRecovery != null && recoveryLabel != null) TextButton(onClick = onRecovery) { Text(recoveryLabel) }
                if (onClose != null) TextButton(onClick = onClose) { Text(stringResource(R.string.cancel_button)) }
            }
        }
    }
    if (showScanner) QrScannerDialog(
        onQrCodeScanned = { value -> showScanner = false; acceptCode(value) },
        onDismiss = { showScanner = false }
    )
}
