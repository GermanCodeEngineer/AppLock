package dev.pranav.applock.features.setpassword.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import dev.pranav.applock.AppLockApplication
import dev.pranav.applock.R
import dev.pranav.applock.core.navigation.finishPasswordSetup
import dev.pranav.applock.data.repository.PreferencesRepository
import dev.pranav.applock.features.lockscreen.ui.KeypadRow
import dev.pranav.applock.features.lockscreen.ui.PasswordIndicators
import dev.pranav.applock.ui.icons.Backspace

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun SetPasswordScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean
) {
    val state = rememberSetPasswordState(isFirstTimeSetup)
    val minLength = 4

    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val fragmentActivity = LocalActivity.current as? FragmentActivity
    val appLockRepository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
    }

    val configuration = LocalConfiguration.current
    val windowInfo = LocalWindowInfo.current

    val screenWidth = windowInfo.containerSize.width
    val screenHeight = windowInfo.containerSize.height
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    val isLandscape = screenWidth > screenHeight

    val horizontalPadding = remember(screenWidthDp, isLandscape) {
        if (isLandscape) {
            0.dp
        } else {
            screenWidthDp * 0.12f
        }
    }

    val buttonSpacing = remember(screenWidthDp, screenHeightDp, isLandscape) {
        if (isLandscape) {
            screenHeightDp * 0.015f
        } else {
            screenWidthDp * 0.02f
        }
    }

    val buttonSize =
        remember(screenWidthDp, screenHeightDp, isLandscape, buttonSpacing, horizontalPadding) {
            if (isLandscape) {
                val availableHeight = screenHeightDp * 0.8f
                val totalVerticalSpacing = buttonSpacing * 3
                val heightBasedSize = (availableHeight - totalVerticalSpacing) / 4f

                val availableWidth = (screenWidthDp * 0.45f)
                val totalHorizontalSpacing = buttonSpacing * 2
                val widthBasedSize = (availableWidth - totalHorizontalSpacing) / 3f

                minOf(heightBasedSize, widthBasedSize)
            } else {
                val availableWidth = screenWidthDp - (horizontalPadding * 2)
                val totalSpacing = buttonSpacing * 2
                (availableWidth - totalSpacing) / 3.5f
            }
        }

    SetPasswordBackHandler(
        state = state,
        navController = navController,
        activity = activity,
        context = context
    )

    fun onKeyClick(key: String) {
        when (key) {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
                state.updateCurrentInput(state.currentInput + key)
            }

            "backspace" -> {
                if (state.currentInput.isNotEmpty()) {
                    state.updateCurrentInput(state.currentInput.dropLast(1))
                }
                state.clearErrors()
            }

            "proceed" -> {
                state.submit(
                    validateOldPassword = { appLockRepository!!.validatePassword(it) },
                    onSavePassword = { pin ->
                        appLockRepository?.setLockType(PreferencesRepository.LOCK_TYPE_PIN)
                        appLockRepository?.setPassword(pin)
                        Toast.makeText(
                            context,
                            context.getString(R.string.password_set_successfully_toast),
                            Toast.LENGTH_SHORT
                        ).show()

                        navController.finishPasswordSetup(isFirstTimeSetup)
                    },
                    minLength = minLength
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            if (isFirstTimeSetup && !isLandscape) {
                TopAppBar(
                    title = {
                        Text(
                            text = when {
                                state.isVerifyOldPasswordMode -> stringResource(R.string.enter_current_pin_title)
                                state.isConfirmationMode -> stringResource(R.string.confirm_pin_title)
                                else -> stringResource(R.string.set_new_pin_title)
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
        }
    ) { innerPadding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(end = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            state.isVerifyOldPasswordMode -> stringResource(R.string.enter_current_pin_label)
                            state.isConfirmationMode -> stringResource(R.string.confirm_new_pin_label)
                            else -> stringResource(R.string.create_new_pin_label)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (state.showMismatchError) {
                        Text(
                            text = stringResource(R.string.pins_dont_match_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    if (state.showLengthError) {
                        Text(
                            text = stringResource(R.string.pin_min_length_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    if (state.showInvalidOldPasswordError) {
                        Text(
                            text = stringResource(R.string.incorrect_pin_try_again),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    PasswordIndicators(
                        passwordLength = state.currentInput.length
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            state.isVerifyOldPasswordMode -> stringResource(R.string.enter_current_pin_label)
                            state.isConfirmationMode -> stringResource(R.string.re_enter_new_pin_confirm_label)
                            else -> stringResource(R.string.tooltip_create_pin_min_length)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.alpha(0.8f),
                        textAlign = TextAlign.Center
                    )

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

                Column(
                    modifier = Modifier.weight(0.4f),
                    verticalArrangement = Arrangement.spacedBy(buttonSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val disableHaptics = appLockRepository!!.shouldDisableHaptics()

                    KeypadRow(
                        disableHaptics = disableHaptics,
                        keys = listOf("1", "2", "3"),
                        onKeyClick = ::onKeyClick,
                        buttonSize = buttonSize,
                        buttonSpacing = buttonSpacing
                    )
                    KeypadRow(
                        disableHaptics = disableHaptics,
                        keys = listOf("4", "5", "6"),
                        onKeyClick = ::onKeyClick,
                        buttonSize = buttonSize,
                        buttonSpacing = buttonSpacing
                    )
                    KeypadRow(
                        disableHaptics = disableHaptics,
                        keys = listOf("7", "8", "9"),
                        onKeyClick = ::onKeyClick,
                        buttonSize = buttonSize,
                        buttonSpacing = buttonSpacing
                    )

                    KeypadRow(
                        disableHaptics = disableHaptics,
                        keys = listOf("backspace", "0", "proceed"),
                        icons = listOf(
                            Backspace,
                            null,
                            if (state.isConfirmationMode || state.isVerifyOldPasswordMode) Icons.Default.Check else Icons.AutoMirrored.Rounded.KeyboardArrowRight
                        ),
                        onKeyClick = ::onKeyClick,
                        buttonSize = buttonSize,
                        buttonSpacing = buttonSpacing
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            state.isVerifyOldPasswordMode -> stringResource(R.string.enter_current_pin_label)
                            state.isConfirmationMode -> stringResource(R.string.confirm_new_pin_label)
                            else -> stringResource(R.string.create_new_pin_label)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }

                if (state.showMismatchError) {
                    Text(
                        text = stringResource(R.string.pins_dont_match_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                if (state.showLengthError) {
                    Text(
                        text = stringResource(R.string.pin_min_length_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                if (state.showInvalidOldPasswordError) {
                    Text(
                        text = stringResource(R.string.incorrect_pin_try_again),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                PasswordIndicators(
                    passwordLength = state.currentInput.length
                )

                Text(
                    text = when {
                        state.isVerifyOldPasswordMode -> stringResource(R.string.enter_current_pin_label)
                        state.isConfirmationMode -> stringResource(R.string.re_enter_new_pin_confirm_label)
                        else -> stringResource(R.string.tooltip_create_pin_min_length)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.alpha(0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.weight(1f))

                if (!state.isVerifyOldPasswordMode && !state.isConfirmationMode) {
                    MethodSwitchButtons(
                        currentMethod = SetPasswordLockMethod.PIN,
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
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(buttonSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = horizontalPadding)
                ) {
                    val disableHaptics = appLockRepository!!.shouldDisableHaptics()

                    KeypadRow(
                        disableHaptics = disableHaptics,
                        keys = listOf("1", "2", "3"),
                        onKeyClick = ::onKeyClick,
                        buttonSize = buttonSize,
                        buttonSpacing = buttonSpacing
                    )
                    KeypadRow(
                        disableHaptics = disableHaptics,
                        keys = listOf("4", "5", "6"),
                        onKeyClick = ::onKeyClick,
                        buttonSize = buttonSize,
                        buttonSpacing = buttonSpacing
                    )
                    KeypadRow(
                        disableHaptics = disableHaptics,
                        keys = listOf("7", "8", "9"),
                        onKeyClick = ::onKeyClick,
                        buttonSize = buttonSize,
                        buttonSpacing = buttonSpacing
                    )

                    KeypadRow(
                        disableHaptics = disableHaptics,
                        keys = listOf("backspace", "0", "proceed"),
                        icons = listOf(
                            Backspace,
                            null,
                            if (state.isConfirmationMode || state.isVerifyOldPasswordMode) Icons.Default.Check else Icons.AutoMirrored.Rounded.KeyboardArrowRight
                        ),
                        onKeyClick = ::onKeyClick,
                        buttonSize = buttonSize,
                        buttonSpacing = buttonSpacing
                    )
                }
            }
        }
    }
}
