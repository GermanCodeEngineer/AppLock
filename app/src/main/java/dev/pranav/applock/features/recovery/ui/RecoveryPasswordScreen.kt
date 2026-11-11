package dev.pranav.applock.features.recovery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.pranav.applock.ui.components.AuthenticationShortcutButton
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.features.recovery.domain.RecoveryPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecoveryPasswordScreen(
    isSetup: Boolean,
    onCompleted: () -> Unit,
    onBack: () -> Unit
) {
    val repository = LocalContext.current.appLockRepository()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    BackHandler { if (!busy) onBack() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp)
                .imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthenticationShortcutButton(enabled = !busy)
            Text(
                stringResource(if (isSetup) R.string.recovery_setup_title else R.string.recovery_unlock_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(stringResource(if (isSetup) R.string.recovery_setup_description else R.string.recovery_unlock_description))
            OutlinedTextField(
                value = password,
                onValueChange = { if (it.length <= RecoveryPassword.MAX_LENGTH) { password = it; error = null } },
                label = { Text(stringResource(R.string.recovery_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            if (isSetup) {
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { if (it.length <= RecoveryPassword.MAX_LENGTH) { confirmation = it; error = null } },
                    label = { Text(stringResource(R.string.recovery_confirm_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (busy) CircularProgressIndicator()
            Button(
                enabled = !busy && password.isNotEmpty(),
                onClick = {
                    when {
                        isSetup && (password.length < RecoveryPassword.MIN_LENGTH || password.isBlank()) ->
                            error = R.string.recovery_length_error
                        isSetup && password != confirmation -> error = R.string.recovery_mismatch_error
                        else -> {
                            busy = true
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (isSetup) repository.setRecoveryPassword(password)
                                        else repository.recoverAuthentication(password)
                                    }.getOrDefault(false)
                                }
                                busy = false
                                if (success) {
                                    password = ""
                                    confirmation = ""
                                    onCompleted()
                                } else {
                                    error = if (isSetup) R.string.authentication_save_error else R.string.recovery_invalid_error
                                }
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(if (isSetup) R.string.recovery_save_button else R.string.recovery_reset_button))
            }
            if (!isSetup) TextButton(enabled = !busy, onClick = onBack) { Text(stringResource(R.string.cancel_button)) }
        }
    }
}
