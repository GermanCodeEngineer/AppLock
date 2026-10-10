## Screens to Update:

1. **PinPasswordOverlayScreen** - Unlock screen with numeric PIN entry
2. **AlphanumericPasswordOverlayScreen** - Unlock screen with password text entry
3. **SetPasswordScreen** - Initial PIN setup screen

## Updated Code for Each File:

### 1. PasswordOverlayScreen.kt (PinPasswordOverlayScreen)

Add QR button next to or above the biometric button:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PinPasswordOverlayScreen(
    modifier: Modifier = Modifier,
    showBiometricButton: Boolean = false,
    showQrButton: Boolean = true,  // ADD THIS
    fromMainActivity: Boolean = false,
    showCloseButton: Boolean = false,
    onClose: () -> Unit = {},
    onBiometricAuth: () -> Unit = {},
    onQrCodeScanned: (String) -> Unit = {},  // ADD THIS - callback for QR result
    onAuthSuccess: () -> Unit,
    lockedAppName: String? = null,
    triggeringPackageName: String? = null,
    onPinAttempt: ((pin: String) -> Boolean)? = null
) {
    // ... existing code ...

    // Update KeypadSection call to include QR callback
    KeypadSection(
        passwordState = passwordState,
        minLength = minLength,
        showBiometricButton = showBiometricButton,
        showQrButton = showQrButton,  // ADD THIS
        fromMainActivity = fromMainActivity,
        onBiometricAuth = onBiometricAuth,
        onQrCodeScanned = onQrCodeScanned,  // ADD THIS
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
```

### 2. KeypadSection Update (in PasswordOverlayScreen.kt)

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeypadSection(
    passwordState: MutableState<String>,
    minLength: Int,
    showBiometricButton: Boolean,
    showQrButton: Boolean = true,  // ADD THIS
    fromMainActivity: Boolean = false,
    onBiometricAuth: () -> Unit,
    onQrCodeScanned: (String) -> Unit = {},  // ADD THIS
    onAuthSuccess: () -> Unit,
    onPinAttempt: ((pin: String) -> Boolean)? = null,
    onPasswordChange: () -> Unit,
    onPinIncorrect: () -> Unit
) {
    val context = LocalContext.current
    // ... existing code ...

    Column(
        verticalArrangement = Arrangement.spacedBy(buttonSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (isLandscape) {
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        } else {
            Modifier
                .padding(horizontal = horizontalPadding)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        }
    ) {
        // ADD THIS ROW for biometric and QR buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(44.dp)
        ) {
            if (showBiometricButton) {
                FilledTonalIconButton(
                    onClick = onBiometricAuth,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(40),
                ) {
                    Icon(
                        imageVector = Fingerprint,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        contentDescription = stringResource(R.string.biometric_authentication_cd),
                        tint = MaterialTheme.colorScheme.surfaceTint
                    )
                }
            }

            if (showQrButton) {
                FilledTonalIconButton(
                    onClick = { onQrCodeScanned("") },  // Trigger QR scanner
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(40),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,  // Use Material QR icon
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        contentDescription = "Scan QR Code",
                        tint = MaterialTheme.colorScheme.surfaceTint
                    )
                }
            }
        }

        // Rest of keypad buttons...
        KeypadRow(
            disableHaptics = disableHaptics,
            keys = listOf("1", "2", "3"),
            onKeyClick = onDigitKeyClick,
            buttonSize = buttonSize,
            buttonSpacing = buttonSpacing
        )
        // ... other rows ...
    }
}
```

### 3. AlphanumericPasswordOverlayScreen.kt Update

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlphanumericPasswordOverlayScreen(
    modifier: Modifier = Modifier,
    showBiometricButton: Boolean = false,
    showQrButton: Boolean = true,  // ADD THIS
    fromMainActivity: Boolean = false,
    showCloseButton: Boolean = false,
    onClose: () -> Unit = {},
    onBiometricAuth: () -> Unit = {},
    onQrCodeScanned: (String) -> Unit = {},  // ADD THIS
    onAuthSuccess: () -> Unit,
    lockedAppName: String? = null,
    triggeringPackageName: String? = null,
    onPasswordAttempt: ((password: String) -> Boolean)? = null
) {
    // ... existing code ...

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBiometricButton) {
            FilledTonalIconButton(
                onClick = onBiometricAuth,
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Fingerprint,
                    modifier = Modifier.size(24.dp),
                    contentDescription = stringResource(R.string.biometric_authentication_cd),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ADD THIS - QR Code button
        if (showQrButton) {
            FilledTonalIconButton(
                onClick = { onQrCodeScanned("") },  // Trigger QR scanner
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    modifier = Modifier.size(24.dp),
                    contentDescription = "Scan QR Code",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Button(
            onClick = {
                if (passwordState.length >= minLength) {
                    performVerification(
                        passwordState,
                        fromMainActivity,
                        appLockRepository,
                        onAuthSuccess,
                        onPasswordAttempt,
                        onIncorrect = {
                            passwordState = ""
                            showError = true
                        }
                    )
                }
            },
            modifier = Modifier.weight(1f),
            shapes = ButtonDefaults.shapes()
        ) {
            Text(stringResource(R.string.verify_button))
        }
    }
}
```

### 4. SetPasswordScreen.kt Update

```kotlin
@Composable
fun SetPasswordScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean
) {
    // ... existing variables ...
    var showQrButton by remember { mutableStateOf(true) }  // ADD THIS

    // ... existing code ...

    // When rendering KeypadRow for the PIN input, add QR handler:
    if (showQrButton && !isLandscape) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                // Trigger QR scanner - result handled by onQrCodeScanned
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan QR Code")
        }
    }
}
```

## Additional Setup Needed:

1. **Add QR icon import** at the top of files:
```kotlin
import androidx.compose.material.icons.filled.QrCode2
```

2. **Add QR Scanner Library** to `build.gradle.kts`:
```kotlin
implementation("com.google.mlkit:barcode-scanning:17.1.0")
implementation("androidx.camera:camera-core:1.3.0")
implementation("androidx.camera:camera-camera2:1.3.0")
implementation("androidx.camera:camera-lifecycle:1.3.0")
```

3. **Create a QR Scanner Composable** (new file):
```kotlin
// app/src/main/java/dev/pranav/applock/features/qr/ui/QrScannerScreen.kt

@Composable
fun QrScannerScreen(
    onQrCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Implement using CameraX and MLKit barcode scanning
    // Return extracted password when QR is detected
}
```
