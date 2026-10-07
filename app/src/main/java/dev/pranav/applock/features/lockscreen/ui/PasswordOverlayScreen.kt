package dev.pranav.applock.features.lockscreen.ui

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.features.totp.data.TotpSecretStore
import dev.pranav.applock.features.totp.domain.TotpService
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.data.repository.PreferencesRepository
import dev.pranav.applock.services.AppLockManager
import dev.pranav.applock.ui.theme.AppLockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class PasswordOverlayActivity: FragmentActivity() {
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var appLockRepository: AppLockRepository
    internal var lockedPackageNameFromIntent: String? = null
    internal var triggeringPackageNameFromIntent: String? = null

    private var isBiometricPromptShowingLocal = false
    private var appName: String = ""

    private val TAG = "PasswordOverlayActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lockedPackageNameFromIntent = intent.getStringExtra("locked_package")
        triggeringPackageNameFromIntent = intent.getStringExtra("triggering_package")
        if (lockedPackageNameFromIntent == null) {
            Log.e(TAG, "No locked_package name provided in intent. Finishing.")
            finishAffinity()
            return
        }

        enableEdgeToEdge()

        appLockRepository = applicationContext.appLockRepository()

        onBackPressedDispatcher.addCallback(
            this,
            object: OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Prevent back navigation to maintain security
                    Log.d(TAG, "Back pressed ignored on AppLock overlay")
                }
            })

        setupWindow()
        loadAppNameAndSetupUI()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        setupBiometricPromptInternal()
    }

    override fun onPostResume() {
        super.onPostResume()
        setupBiometricPromptInternal()
        if (appLockRepository.isBiometricAuthEnabled()) {
            triggerBiometricPrompt()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed - orientation: ${newConfig.orientation}")
    }

    private fun setupWindow() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SECURE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        val layoutParams = window.attributes
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT

        if (appLockRepository.shouldUseMaxBrightness()) {
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.attributes = layoutParams
    }

    private fun loadAppNameAndSetupUI() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                appName = packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(lockedPackageNameFromIntent!!, 0)
                ).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading app name: ${e.message}")
                appName = getString(R.string.default_app_name)
            }
        }
        setupUI()
    }

    private fun setupUI() {
        val onPinAttemptCallback = { pin: String ->
            val isValid = appLockRepository.validatePassword(pin)
            if (isValid) {
                lockedPackageNameFromIntent?.let { pkgName ->
                    AppLockManager.unlockApp(pkgName)
                    finishAfterTransition()
                }
            }
            isValid
        }

        val onPatternAttemptCallback = { pattern: String ->
            val isValid = appLockRepository.validatePattern(pattern)
            if (isValid) {
                lockedPackageNameFromIntent?.let { pkgName ->
                    AppLockManager.unlockApp(pkgName)
                    finishAfterTransition()
                }
            }
            isValid
        }

        setContent {
            AppLockTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentColor = MaterialTheme.colorScheme.primaryContainer
                ) { innerPadding ->
                    val lockType = appLockRepository.getLockType()
                    when (lockType) {
                        PreferencesRepository.LOCK_TYPE_PATTERN -> {
                            PatternLockScreen(
                                modifier = Modifier.padding(innerPadding),
                                fromMainActivity = false,
                                lockedAppName = appName,
                                triggeringPackageName = triggeringPackageNameFromIntent,
                                onPatternAttempt = onPatternAttemptCallback
                            )
                        }

                        PreferencesRepository.LOCK_TYPE_PASSWORD -> {
                            AlphanumericPasswordOverlayScreen(
                                modifier = Modifier.padding(innerPadding),
                                showBiometricButton = appLockRepository.isBiometricAuthEnabled(),
                                fromMainActivity = false,
                                onBiometricAuth = { triggerBiometricPrompt() },
                                onAuthSuccess = {},
                                lockedAppName = appName,
                                triggeringPackageName = triggeringPackageNameFromIntent,
                                onPasswordAttempt = onPinAttemptCallback,
                                showCloseButton = true,
                                onClose = { finish() }
                            )
                        }

                        PreferencesRepository.LOCK_TYPE_TOTP -> {
                            TotpPasswordOverlayScreen(
                                modifier = Modifier.padding(innerPadding),
                                showBiometricButton = appLockRepository.isBiometricAuthEnabled(),
                                fromMainActivity = false,
                                onBiometricAuth = { triggerBiometricPrompt() },
                                onAuthSuccess = {},
                                lockedAppName = appName,
                                triggeringPackageName = triggeringPackageNameFromIntent,
                                onTotpAttempt = { code ->
                                    val secret = TotpSecretStore(this).getSecret()
                                    val isValid = secret != null && TotpService.verify(secret, code)
                                    if (isValid) {
                                        lockedPackageNameFromIntent?.let { pkgName ->
                                            AppLockManager.unlockApp(pkgName)
                                            finishAfterTransition()
                                        }
                                    }
                                    isValid
                                },
                                showCloseButton = true,
                                onClose = { finish() }
                            )
                        }

                        else -> {
                            PinPasswordOverlayScreen(
                                modifier = Modifier.padding(innerPadding),
                                showBiometricButton = appLockRepository.isBiometricAuthEnabled(),
                                fromMainActivity = false,
                                onBiometricAuth = { triggerBiometricPrompt() },
                                onAuthSuccess = {},
                                lockedAppName = appName,
                                triggeringPackageName = triggeringPackageNameFromIntent,
                                onPinAttempt = onPinAttemptCallback,
                                showCloseButton = true,
                                onClose = { finish() }
                            )

                            BackHandler { }
                        }
                    }
                }
            }
        }
    }

    private fun setupBiometricPromptInternal() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt =
            BiometricPrompt(this@PasswordOverlayActivity, executor, authenticationCallbackInternal)

        val appNameForPrompt = appName.ifEmpty { getString(R.string.this_app) }
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_app_title, appNameForPrompt))
            .setSubtitle(getString(R.string.confirm_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin_button))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .setConfirmationRequired(false)
            .build()
    }

    private val authenticationCallbackInternal =
        object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isBiometricPromptShowingLocal = false
                AppLockManager.reportBiometricAuthFinished()
                Log.w(TAG, "Authentication error: $errString ($errorCode)")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isBiometricPromptShowingLocal = false
                lockedPackageNameFromIntent?.let { pkgName ->
                    AppLockManager.temporarilyUnlockAppWithBiometrics(pkgName)
                }
                finishAfterTransition()
            }
        }

    override fun onResume() {
        super.onResume()
        AppLockManager.isLockScreenShown.set(true)
        lifecycleScope.launch {
            applyUserPreferences()
        }
    }

    private fun applyUserPreferences() {
        if (appLockRepository.shouldUseMaxBrightness()) {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            if (window.decorView.isAttachedToWindow) {
                windowManager.updateViewLayout(window.decorView, window.attributes)
            }
        }
    }

    fun triggerBiometricPrompt() {
        if (appLockRepository.isBiometricAuthEnabled()) {
            AppLockManager.reportBiometricAuthStarted()
            isBiometricPromptShowingLocal = true
            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling biometricPrompt.authenticate: ${e.message}", e)
                isBiometricPromptShowingLocal = false
                AppLockManager.reportBiometricAuthFinished()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations && !isBiometricPromptShowingLocal) {
            AppLockManager.isLockScreenShown.set(false)
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        AppLockManager.isLockScreenShown.set(true)
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return
        }
        AppLockManager.isLockScreenShown.set(false)
        if (!isFinishing && !isDestroyed) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLockManager.isLockScreenShown.set(false)
        AppLockManager.reportBiometricAuthFinished()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PinPasswordOverlayScreen(
    modifier: Modifier = Modifier,
    showBiometricButton: Boolean = false,
    fromMainActivity: Boolean = false,
    showCloseButton: Boolean = false,
    onClose: () -> Unit = {},
    onBiometricAuth: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    lockedAppName: String? = null,
    triggeringPackageName: String? = null,
    onPinAttempt: ((pin: String) -> Boolean)? = null
) {
    val appLockRepository = LocalContext.current.appLockRepository()
    val windowInfo = LocalWindowInfo.current

    val screenWidth = windowInfo.containerSize.width
    val screenHeight = windowInfo.containerSize.height
    val isLandscape = screenWidth > screenHeight

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp

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

            val passwordState = remember { mutableStateOf("") }
            var showError by remember { mutableStateOf(false) }
            val minLength = 4

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                                "Enter PIN for $lockedAppName"
                            else
                                "Enter PIN",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        PasswordIndicators(
                            passwordLength = passwordState.value.length,
                        )

                        if (showError) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.incorrect_pin_try_again),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        KeypadSection(
                            passwordState = passwordState,
                            minLength = minLength,
                            showBiometricButton = showBiometricButton,
                            fromMainActivity = fromMainActivity,
                            onBiometricAuth = onBiometricAuth,
                            onAuthSuccess = onAuthSuccess,
                            onPinAttempt = onPinAttempt,
                            onPasswordChange = {
                                showError = false

                                if (appLockRepository.isAutoUnlockEnabled()) {
                                    onPinAttempt?.invoke(passwordState.value)
                                }
                            },
                            onPinIncorrect = { showError = true }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = if (fromMainActivity) 24.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    val topSpacerHeight = if (screenHeightDp < 600.dp) 12.dp else 48.dp
                    Spacer(modifier = Modifier.height(topSpacerHeight))

                    Text(
                        text = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                            "Enter PIN for $lockedAppName"
                        else
                            "Enter PIN",
                        style = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                            MaterialTheme.typography.titleLargeEmphasized
                        else
                            MaterialTheme.typography.headlineMediumEmphasized,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PasswordIndicators(
                        passwordLength = passwordState.value.length,
                    )

                    if (showError) {
                        Text(
                            text = stringResource(R.string.incorrect_pin_try_again),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    KeypadSection(
                        passwordState = passwordState,
                        minLength = minLength,
                        showBiometricButton = showBiometricButton,
                        fromMainActivity = fromMainActivity,
                        onBiometricAuth = onBiometricAuth,
                        onAuthSuccess = onAuthSuccess,
                        onPinAttempt = onPinAttempt,
                        onPasswordChange = {
                            showError = false

                            if (appLockRepository.isAutoUnlockEnabled()) {
                                onPinAttempt?.invoke(passwordState.value)
                            }
                        },
                        onPinIncorrect = { showError = true }
                    )
                }
            }
        }
    }
}
