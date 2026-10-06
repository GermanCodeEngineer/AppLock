package dev.pranav.applock.features.setpassword.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import dev.pranav.applock.AppLockApplication
import dev.pranav.applock.R
import dev.pranav.applock.core.navigation.finishPasswordSetup
import dev.pranav.applock.core.utils.SecurityUtils
import dev.pranav.applock.data.repository.PreferencesRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlphanumericSetPasswordScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean
) {
    val state = rememberSetPasswordState(isFirstTimeSetup)
    var passwordVisible by remember { mutableStateOf(false) }

    val minLength = 4
    val maxLength = 64
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val fragmentActivity = LocalActivity.current as? FragmentActivity
    val appLockRepository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    SetPasswordBackHandler(
        state = state,
        navController = navController,
        activity = activity,
        context = context
    )

    fun submitPassword() {
        state.submit(
            validateOldPassword = { appLockRepository!!.validatePassword(it) },
            onSavePassword = { newPassword ->
                appLockRepository?.setLockType(PreferencesRepository.LOCK_TYPE_PASSWORD)
                appLockRepository?.setPassword(newPassword)
                Toast.makeText(
                    context,
                    context.getString(R.string.password_set_successfully_toast),
                    Toast.LENGTH_SHORT
                ).show()

                navController.finishPasswordSetup(isFirstTimeSetup)
            },
            minLength = minLength,
            maxLength = maxLength
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            state.isVerifyOldPasswordMode -> stringResource(R.string.enter_current_password_label)
                            state.isConfirmationMode -> stringResource(R.string.confirm_alphanumeric_password_label)
                            else -> stringResource(R.string.set_alphanumeric_password_title)
                        },
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = when {
                    state.isVerifyOldPasswordMode -> stringResource(R.string.enter_current_password_label)
                    state.isConfirmationMode -> stringResource(R.string.confirm_alphanumeric_password_label)
                    else -> stringResource(R.string.create_alphanumeric_password_label)
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = state.currentInput,
                onValueChange = { input ->
                    val sanitized = SecurityUtils.sanitizePassword(input)
                    state.updateCurrentInput(sanitized)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.password_hint)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    val image = if (passwordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                isError = state.showMismatchError || state.showLengthError || state.showMaxLengthError || state.showInvalidOldPasswordError,
                singleLine = true
            )

            if (state.showMismatchError) {
                Text(
                    text = stringResource(R.string.passwords_dont_match_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.Start)
                )
            }

            if (state.showLengthError) {
                Text(
                    text = stringResource(R.string.password_too_short_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.Start)
                )
            }

            if (state.showMaxLengthError) {
                Text(
                    text = stringResource(R.string.password_too_long_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.Start)
                )
            }

            if (state.showInvalidOldPasswordError) {
                Text(
                    text = stringResource(R.string.incorrect_password_try_again),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { submitPassword() },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.next_button))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!state.isVerifyOldPasswordMode && !state.isConfirmationMode) {
                MethodSwitchButtons(
                    currentMethod = SetPasswordLockMethod.PASSWORD,
                    navController = navController,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            SetPasswordBottomActions(
                state = state,
                navController = navController,
                activity = activity,
                onLaunchBiometric = {
                    launchDeviceCredentialAuth(
                        context = context,
                        fragmentActivity = fragmentActivity,
                        onSuccess = { state.onBiometricSuccess() }
                    )
                }
            )
        }
    }
}
