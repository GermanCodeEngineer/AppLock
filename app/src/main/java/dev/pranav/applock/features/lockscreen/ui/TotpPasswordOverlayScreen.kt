package dev.pranav.applock.features.lockscreen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.core.utils.vibrate
import dev.pranav.applock.features.totp.data.TotpSecretStore
import dev.pranav.applock.features.totp.domain.TotpService

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TotpPasswordOverlayScreen(
    modifier: Modifier = Modifier,
    showBiometricButton: Boolean = false,
    fromMainActivity: Boolean = false,
    showCloseButton: Boolean = false,
    onClose: () -> Unit = {},
    onBiometricAuth: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    lockedAppName: String? = null,
    triggeringPackageName: String? = null,
    onTotpAttempt: ((code: String) -> Boolean)? = null
) {
    val context = LocalContext.current
    val store = remember { TotpSecretStore(context) }

    val passwordState = remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    fun verifyCode(code: String) {
        val secret = store.getSecret()
        val isValid = if (fromMainActivity) {
            secret != null && TotpService.verify(secret, code)
        } else {
            onTotpAttempt?.invoke(code) == true
        }

        if (isValid) {
            onAuthSuccess()
        } else {
            showError = true
            passwordState.value = ""
            if (!context.appLockRepository().shouldDisableHaptics()) {
                vibrate(context, 100)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showCloseButton) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 8.dp, top = 8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = if (fromMainActivity) 24.dp else 12.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                        "Enter TOTP Code for $lockedAppName"
                    else
                        "Enter TOTP Code",
                    style = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                        MaterialTheme.typography.titleLargeEmphasized
                    else
                        MaterialTheme.typography.headlineMediumEmphasized,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                PasswordIndicators(
                    passwordLength = passwordState.value.length
                )

                if (showError) {
                    Text(
                        text = stringResource(R.string.totp_invalid_code_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                KeypadSection(
                    passwordState = passwordState,
                    minLength = 6,
                    showBiometricButton = showBiometricButton,
                    fromMainActivity = fromMainActivity,
                    onBiometricAuth = onBiometricAuth,
                    onAuthSuccess = onAuthSuccess,
                    onPinAttempt = { code ->
                        val isValid = if (fromMainActivity) {
                            val secret = store.getSecret()
                            secret != null && TotpService.verify(secret, code)
                        } else {
                            onTotpAttempt?.invoke(code) == true
                        }
                        if (isValid) {
                            onAuthSuccess()
                        } else {
                            showError = true
                            passwordState.value = ""
                        }
                        isValid
                    },
                    onPasswordChange = {
                        showError = false
                        if (passwordState.value.length > 6) {
                            passwordState.value = passwordState.value.take(6)
                        }
                        if (passwordState.value.length == 6) {
                            verifyCode(passwordState.value)
                        }
                    },
                    onPinIncorrect = {
                        showError = true
                    }
                )
            }
        }
    }

    BackHandler { }
}
