package dev.pranav.applock.core.navigation

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.pranav.applock.AppLockApplication
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.LogUtils
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.data.repository.PreferencesRepository
import dev.pranav.applock.features.antiuninstall.ui.AntiUninstallScreen
import dev.pranav.applock.features.appintro.ui.AppIntroScreen
import dev.pranav.applock.features.applist.ui.MainScreen
import dev.pranav.applock.features.lockscreen.ui.AlphanumericPasswordOverlayScreen
import dev.pranav.applock.features.lockscreen.ui.PatternLockScreen
import dev.pranav.applock.features.lockscreen.ui.PinPasswordOverlayScreen
import dev.pranav.applock.features.lockscreen.ui.TotpUnlockScreen
import dev.pranav.applock.features.recovery.ui.RecoveryPasswordScreen
import dev.pranav.applock.features.recovery.ui.DeviceCredentialRecoveryScreen
import dev.pranav.applock.features.setpassword.ui.AlphanumericSetPasswordScreen
import dev.pranav.applock.features.setpassword.ui.PatternSetPasswordScreen
import dev.pranav.applock.features.setpassword.ui.SetPasswordScreen
import dev.pranav.applock.features.settings.ui.SettingsScreen
import dev.pranav.applock.features.totp.ui.TotpSetupScreen
import dev.pranav.applock.features.triggerexclusions.ui.TriggerExclusionsScreen

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    val activity = LocalActivity.current
    val application = LocalContext.current.applicationContext as AppLockApplication

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(ANIMATION_DURATION)) +
                    scaleIn(initialScale = SCALE_INITIAL, animationSpec = tween(ANIMATION_DURATION))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(ANIMATION_DURATION)) +
                    scaleIn(initialScale = SCALE_INITIAL, animationSpec = tween(ANIMATION_DURATION))
        },
    ) {
        composable(Screen.AppIntro.route) {
            AppIntroScreen(navController)
        }

        composable(Screen.SetPassword.route) {
            SetPasswordScreen(navController, isFirstTimeSetup = true)
        }

        composable(Screen.ChangePassword.route) {
            when (application.appLockRepository.getLockType()) {
                PreferencesRepository.LOCK_TYPE_TOTP -> {
                    TotpSetupScreen(
                        onFinished = { navController.finishPasswordSetup(false) },
                        onBack = { navController.navigate(Screen.SetPassword.route) }
                    )
                }
                PreferencesRepository.LOCK_TYPE_PATTERN -> {
                    PatternSetPasswordScreen(navController, false)
                }
                PreferencesRepository.LOCK_TYPE_PASSWORD -> {
                    AlphanumericSetPasswordScreen(navController, false)
                }
                else -> {
                    SetPasswordScreen(navController, isFirstTimeSetup = false)
                }
            }
        }

        composable(Screen.SetPasswordPattern.route) {
            PatternSetPasswordScreen(navController, isFirstTimeSetup = true)
        }

        composable(Screen.SetPasswordAlphanumeric.route) {
            AlphanumericSetPasswordScreen(navController, isFirstTimeSetup = true)
        }

        composable(Screen.RecoverySetup.route) {
            RecoveryPasswordScreen(
                isSetup = true,
                onCompleted = {
                    navController.navigate(if (application.appLockRepository.hasAuthentication()) Screen.Main.route else Screen.SetPassword.route) {
                        popUpTo(navController.graph.id)
                    }
                },
                onBack = { activity?.finish() }
            )
        }

        composable(Screen.RecoveryUnlock.route) {
            RecoveryPasswordScreen(
                isSetup = false,
                onCompleted = {
                    navController.navigate(Screen.SetPassword.route) { popUpTo(navController.graph.id) }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TotpSetup.route) {
            TotpSetupScreen(
                onFinished = { navController.finishPasswordSetup(true) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DeviceCredentialRecovery.route) {
            DeviceCredentialRecoveryScreen(
                onCompleted = {
                    navController.navigate(Screen.RecoverySetup.route) { popUpTo(navController.graph.id) }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(navController)
        }

        composable(Screen.PasswordOverlay.route) {
            val context = LocalActivity.current as FragmentActivity
            val lockType = application.appLockRepository.getLockType()

            BackHandler { activity?.finish() }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (lockType) {
                        PreferencesRepository.LOCK_TYPE_TOTP -> {
                            TotpUnlockScreen(
                                onCodeAttempt = { code ->
                                    application.appLockRepository.validateTotp(code).also { valid ->
                                        if (valid) handleAuthenticationSuccess(navController)
                                    }
                                },
                                onBiometricAuth = if (application.appLockRepository.isBiometricAuthEnabled()) {
                                    { handleBiometricAuthentication(context, navController) }
                                } else null
                            )
                        }

                        PreferencesRepository.LOCK_TYPE_PATTERN -> {
                            PatternLockScreen(
                                fromMainActivity = true,
                                onPatternAttempt = { pattern ->
                                    val isValid = application.appLockRepository.validatePattern(pattern)
                                    if (isValid) {
                                        handleAuthenticationSuccess(navController)
                                    }
                                    isValid
                                },
                                onBiometricAuth = {
                                    handleBiometricAuthentication(context, navController)
                                }
                            )
                        }

                        PreferencesRepository.LOCK_TYPE_PASSWORD -> {
                            AlphanumericPasswordOverlayScreen(
                                showBiometricButton = application.appLockRepository.isBiometricAuthEnabled(),
                                fromMainActivity = true,
                                onBiometricAuth = {
                                    handleBiometricAuthentication(context, navController)
                                },
                                onAuthSuccess = {
                                    handleAuthenticationSuccess(navController)
                                }
                            )
                        }

                        else -> {
                            PinPasswordOverlayScreen(
                                showBiometricButton = application.appLockRepository.isBiometricAuthEnabled(),
                                fromMainActivity = true,
                                onBiometricAuth = {
                                    handleBiometricAuthentication(context, navController)
                                },
                                onAuthSuccess = {
                                    handleAuthenticationSuccess(navController)
                                }
                            )
                        }
                    }
                }
                val hasRecoveryPassword = application.appLockRepository.hasRecoveryPassword()
                TextButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding(),
                    onClick = {
                        navController.navigate(if (hasRecoveryPassword) Screen.RecoveryUnlock.route else Screen.DeviceCredentialRecovery.route)
                    }
                ) {
                    Text(stringResource(if (hasRecoveryPassword) R.string.recovery_use_button else R.string.recovery_forgot_password_button))
                }
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }

        composable(Screen.TriggerExclusions.route) {
            TriggerExclusionsScreen(navController)
        }

        composable(Screen.AntiUninstall.route) {
            AntiUninstallScreen(navController)
        }
    }
}

fun NavController.finishPasswordSetup(isFirstTimeSetup: Boolean) {
    val repository = context.appLockRepository()
    navigate(if (repository.hasRecoveryPassword()) Screen.Main.route else Screen.RecoverySetup.route) {
        // Setup must not remain reachable through Back after a credential has been saved.
        popUpTo(graph.id)
        launchSingleTop = true
    }
}

private fun handleBiometricAuthentication(
    context: FragmentActivity,
    navController: NavHostController
) {
    try {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            context,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "Biometric authentication error: $errString ($errorCode)")
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    LogUtils.d(TAG, "Biometric authentication succeeded")
                    handleAuthenticationSuccess(navController)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed (not recognized)")
                }
            }
        )

        val promptInfo = createBiometricPromptInfo()
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        Log.e(TAG, "Error during biometric authentication", e)
    }
}

private fun createBiometricPromptInfo(): BiometricPrompt.PromptInfo {
    return BiometricPrompt.PromptInfo.Builder()
        .setTitle(BIOMETRIC_TITLE)
        .setSubtitle(BIOMETRIC_SUBTITLE)
        .setNegativeButtonText(BIOMETRIC_NEGATIVE_BUTTON)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        .setConfirmationRequired(false)
        .build()
}

private fun handleAuthenticationSuccess(navController: NavHostController) {
    if (!navController.context.appLockRepository().hasRecoveryPassword()) {
        navController.navigate(Screen.RecoverySetup.route) { popUpTo(navController.graph.id) }
        return
    }
    if (navController.previousBackStackEntry != null) {
        navController.popBackStack()
    } else {
        navigateToMain(navController)
    }
}

private fun navigateToMain(navController: NavHostController) {
    navController.navigate(Screen.Main.route) {
        popUpTo(Screen.PasswordOverlay.route) { inclusive = true }
    }
}

private const val TAG = "AppNavHost"
private const val ANIMATION_DURATION = 400
private const val SCALE_INITIAL = 0.9f
private const val BIOMETRIC_TITLE = "Confirm password"
private const val BIOMETRIC_SUBTITLE = "Confirm biometric to continue"
private const val BIOMETRIC_NEGATIVE_BUTTON = "Use PIN"
