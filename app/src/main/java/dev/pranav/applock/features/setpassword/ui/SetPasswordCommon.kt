package dev.pranav.applock.features.setpassword.ui

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import dev.pranav.applock.R
import dev.pranav.applock.core.navigation.Screen

enum class SetPasswordLockMethod {
    PIN,
    PATTERN,
    PASSWORD
}

class SetPasswordState(
    val isFirstTimeSetup: Boolean
) {
    var passwordState by mutableStateOf("")
    var confirmPasswordState by mutableStateOf("")
    var isConfirmationMode by mutableStateOf(false)
    var isVerifyOldPasswordMode by mutableStateOf(!isFirstTimeSetup)

    var showMismatchError by mutableStateOf(false)
    var showLengthError by mutableStateOf(false)
    var showMaxLengthError by mutableStateOf(false)
    var showInvalidOldPasswordError by mutableStateOf(false)

    val currentInput: String
        get() = when {
            isVerifyOldPasswordMode -> passwordState
            isConfirmationMode -> confirmPasswordState
            else -> passwordState
        }

    fun updateCurrentInput(newInput: String) {
        when {
            isVerifyOldPasswordMode -> passwordState = newInput
            isConfirmationMode -> confirmPasswordState = newInput
            else -> passwordState = newInput
        }
        clearErrors()
    }

    fun clearErrors() {
        showMismatchError = false
        showLengthError = false
        showMaxLengthError = false
        showInvalidOldPasswordError = false
    }

    fun resetToStart(navController: NavController, activity: Activity?) {
        if (isVerifyOldPasswordMode) {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                activity?.finish()
            }
        } else {
            isConfirmationMode = false
            if (!isFirstTimeSetup) {
                isVerifyOldPasswordMode = true
            }
        }
        passwordState = ""
        confirmPasswordState = ""
        clearErrors()
    }

    fun onBiometricSuccess() {
        isVerifyOldPasswordMode = false
        passwordState = ""
        confirmPasswordState = ""
        showInvalidOldPasswordError = false
    }

    fun submit(
        validateOldPassword: (String) -> Boolean,
        onSavePassword: (String) -> Unit,
        minLength: Int = 4,
        maxLength: Int = Int.MAX_VALUE
    ) {
        val inputToTest = currentInput

        if (inputToTest.length < minLength) {
            showLengthError = true
            return
        }

        if (inputToTest.length > maxLength) {
            showMaxLengthError = true
            return
        }

        when {
            isVerifyOldPasswordMode -> {
                if (validateOldPassword(passwordState)) {
                    isVerifyOldPasswordMode = false
                    passwordState = ""
                    showInvalidOldPasswordError = false
                } else {
                    showInvalidOldPasswordError = true
                    passwordState = ""
                }
            }

            !isConfirmationMode -> {
                isConfirmationMode = true
                showLengthError = false
                showMaxLengthError = false
            }

            else -> {
                if (passwordState == confirmPasswordState) {
                    onSavePassword(passwordState)
                } else {
                    showMismatchError = true
                    confirmPasswordState = ""
                }
            }
        }
    }
}

@Composable
fun rememberSetPasswordState(isFirstTimeSetup: Boolean): SetPasswordState {
    return remember(isFirstTimeSetup) { SetPasswordState(isFirstTimeSetup) }
}

fun launchDeviceCredentialAuth(
    context: Context,
    fragmentActivity: FragmentActivity?,
    onSuccess: () -> Unit
) {
    if (fragmentActivity == null) return
    val executor = ContextCompat.getMainExecutor(context)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.authenticate_to_reset_pin_title))
        .setSubtitle(context.getString(R.string.use_device_pin_pattern_password_subtitle))
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    val biometricPrompt = BiometricPrompt(
        fragmentActivity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        })
    biometricPrompt.authenticate(promptInfo)
}

@Composable
fun SetPasswordBackHandler(
    state: SetPasswordState,
    navController: NavController,
    activity: Activity?,
    context: Context
) {
    BackHandler {
        if (state.isFirstTimeSetup) {
            if (state.isConfirmationMode) {
                state.isConfirmationMode = false
            } else {
                Toast.makeText(context, R.string.set_pin_to_continue_toast, Toast.LENGTH_SHORT).show()
            }
        } else {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                activity?.finish()
            }
        }
    }
}

@Composable
fun MethodSwitchButtons(
    currentMethod: SetPasswordLockMethod,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        if (currentMethod != SetPasswordLockMethod.PIN) {
            TextButton(onClick = {
                navController.navigate(Screen.SetPassword.route) {
                    popUpTo(
                        if (currentMethod == SetPasswordLockMethod.PATTERN) Screen.SetPasswordPattern.route
                        else Screen.SetPasswordAlphanumeric.route
                    ) { inclusive = true }
                }
            }) {
                Text(stringResource(R.string.use_pin_instead))
            }
        }
        if (currentMethod != SetPasswordLockMethod.PATTERN) {
            TextButton(onClick = { navController.navigate(Screen.SetPasswordPattern.route) }) {
                Text(stringResource(R.string.use_pattern_button))
            }
        }
        if (currentMethod != SetPasswordLockMethod.PASSWORD) {
            TextButton(onClick = { navController.navigate(Screen.SetPasswordAlphanumeric.route) }) {
                Text(stringResource(R.string.use_password_button))
            }
        }
    }
}

@Composable
fun SetPasswordBottomActions(
    state: SetPasswordState,
    navController: NavController,
    activity: Activity?,
    onLaunchBiometric: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        if (state.isVerifyOldPasswordMode) {
            TextButton(onClick = onLaunchBiometric) {
                Text(stringResource(R.string.reset_using_device_password_button))
            }
        }

        if (state.isVerifyOldPasswordMode || state.isConfirmationMode) {
            TextButton(
                onClick = { state.resetToStart(navController, activity) }
            ) {
                Text(
                    if (state.isVerifyOldPasswordMode) stringResource(R.string.cancel_button)
                    else stringResource(R.string.start_over_button)
                )
            }
        }
    }
}
