package dev.pranav.applock.features.lockscreen.ui

import android.content.Context
import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pranav.applock.R
import dev.pranav.applock.core.ui.shapes
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.core.utils.vibrate
import dev.pranav.applock.ui.icons.Backspace
import dev.pranav.applock.ui.icons.Fingerprint

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PasswordIndicators(
    passwordLength: Int
) {
    val windowInfo = LocalWindowInfo.current
    val configuration = LocalConfiguration.current

    val screenWidth = windowInfo.containerSize.width
    val screenHeight = windowInfo.containerSize.height
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    val isLandscape = screenWidth > screenHeight

    val indicatorSize = remember(screenWidthDp) {
        when {
            screenWidthDp >= 900.dp -> 32.dp
            screenWidthDp >= 600.dp -> 28.dp
            isLandscape -> 26.dp
            else -> 22.dp
        }
    }

    val indicatorSpacing = remember(screenWidthDp) {
        when {
            screenWidthDp >= 900.dp -> 16.dp
            screenWidthDp >= 600.dp -> 14.dp
            isLandscape -> 12.dp
            else -> 8.dp
        }
    }

    val maxWidth = if (isLandscape) {
        minOf(screenWidthDp * 0.5f, 500.dp)
    } else {
        screenWidthDp * 0.85f
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(passwordLength) {
        if (passwordLength > 0) {
            lazyListState.animateScrollToItem(
                index = passwordLength - 1,
                scrollOffset = 0
            )
        }
    }

    Box(
        modifier = Modifier
            .width(maxWidth)
            .height(indicatorSize + 32.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(
                indicatorSpacing,
                Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(passwordLength) { index ->
                key("digit_$index") {
                    val isNewest = index == passwordLength - 1
                    var animationTarget by remember { mutableStateOf(0f) }

                    LaunchedEffect(Unit) {
                        animationTarget = 1f
                    }

                    val animationProgress by animateFloatAsState(
                        targetValue = animationTarget,
                        animationSpec = tween(
                            durationMillis = 600,
                            easing = FastOutSlowInEasing
                        ),
                        label = "indicatorProgress"
                    )

                    val scale = if (isNewest && animationProgress < 1f) {
                        when {
                            animationProgress < 0.6f -> 1.1f + (1f - animationProgress) * 0.4f
                            animationProgress < 0.9f -> 1.1f + (1f - animationProgress) * 0.2f
                            else -> 1f
                        }
                    } else {
                        1f
                    }

                    val shape = when {
                        isNewest && animationProgress < 1f -> shapes[index % shapes.size].toShape()
                        else -> CircleShape
                    }

                    val color = MaterialTheme.colorScheme.primary

                    val collapseProgress = if (isNewest && animationProgress > 0.6f) {
                        ((animationProgress - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val originalShapeScale = 1f - collapseProgress

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .size(indicatorSize)
                    ) {
                        if (collapseProgress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color = color, shape = CircleShape)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = originalShapeScale
                                    scaleY = originalShapeScale
                                }
                                .background(color = color, shape = shape)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun KeypadSection(
    passwordState: MutableState<String>,
    minLength: Int,
    showBiometricButton: Boolean,
    fromMainActivity: Boolean = false,
    onBiometricAuth: () -> Unit,
    onAuthSuccess: () -> Unit,
    onPinAttempt: ((pin: String) -> Boolean)? = null,
    onPasswordChange: () -> Unit,
    onPinIncorrect: () -> Unit
) {
    val context = LocalContext.current
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

    val estimatedTopContentHeight = 220.dp
    val availableHeight = screenHeightDp - estimatedTopContentHeight

    val buttonSize =
        remember(
            screenWidthDp,
            screenHeightDp,
            isLandscape,
            buttonSpacing,
            horizontalPadding,
            showBiometricButton
        ) {
            if (isLandscape) {
                val availableLandscapeHeight = screenHeightDp * 0.8f
                val totalVerticalSpacing = buttonSpacing * 3
                val heightBasedSize = (availableLandscapeHeight - totalVerticalSpacing) / 4f

                val availableWidth = (screenWidthDp * 0.45f)
                val totalHorizontalSpacing = buttonSpacing * 2
                val widthBasedSize = (availableWidth - totalHorizontalSpacing) / 3f

                minOf(heightBasedSize, widthBasedSize)
            } else {
                val availableWidth = screenWidthDp - (horizontalPadding * 2)
                val totalHorizontalSpacing = buttonSpacing * 2
                val widthBasedSize = (availableWidth - totalHorizontalSpacing) / 3.5f

                val totalVerticalSpacing = buttonSpacing * 3
                val biometricAllowance = if (showBiometricButton) 60.dp else 0.dp
                val heightBasedSize =
                    (availableHeight - totalVerticalSpacing - biometricAllowance) / 4f

                minOf(widthBasedSize, heightBasedSize)
            }
        }

    val onDigitKeyClick = remember(passwordState, minLength, onPasswordChange) {
        { key: String ->
            addDigitToPassword(
                passwordState,
                key,
                onPasswordChange
            )
        }
    }

    val disableHaptics = context.appLockRepository().shouldDisableHaptics()

    val onSpecialKeyClick = remember(
        passwordState,
        minLength,
        fromMainActivity,
        onAuthSuccess,
        onPinAttempt,
        context,
        onPasswordChange,
        onPinIncorrect
    ) {
        { key: String ->
            handleKeypadSpecialButtonLogic(
                key = key,
                passwordState = passwordState,
                minLength = minLength,
                fromMainActivity = fromMainActivity,
                onAuthSuccess = onAuthSuccess,
                onPinAttempt = onPinAttempt,
                context = context,
                onPasswordChange = onPasswordChange,
                onPinIncorrect = onPinIncorrect
            )
        }
    }

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
        KeypadRow(
            disableHaptics = disableHaptics,
            keys = listOf("1", "2", "3"),
            onKeyClick = onDigitKeyClick,
            buttonSize = buttonSize,
            buttonSpacing = buttonSpacing
        )
        KeypadRow(
            disableHaptics = disableHaptics,
            keys = listOf("4", "5", "6"),
            onKeyClick = onDigitKeyClick,
            buttonSize = buttonSize,
            buttonSpacing = buttonSpacing
        )
        KeypadRow(
            disableHaptics = disableHaptics,
            keys = listOf("7", "8", "9"),
            onKeyClick = onDigitKeyClick,
            buttonSize = buttonSize,
            buttonSpacing = buttonSpacing
        )
        KeypadRow(
            disableHaptics = disableHaptics,
            keys = listOf("backspace", "0", "proceed"),
            icons = listOf(Backspace, null, Icons.AutoMirrored.Rounded.KeyboardArrowRight),
            onKeyClick = onSpecialKeyClick,
            buttonSize = buttonSize,
            buttonSpacing = buttonSpacing
        )
    }
}

fun addDigitToPassword(
    passwordState: MutableState<String>,
    digit: String,
    onPasswordChange: () -> Unit
) {
    passwordState.value += digit
    onPasswordChange()
}

fun handleKeypadSpecialButtonLogic(
    key: String,
    passwordState: MutableState<String>,
    minLength: Int,
    fromMainActivity: Boolean,
    onAuthSuccess: () -> Unit,
    onPinAttempt: ((pin: String) -> Boolean)?,
    context: Context,
    onPasswordChange: () -> Unit,
    onPinIncorrect: () -> Unit
) {
    val appLockRepository = context.appLockRepository()

    when (key) {
        "0" -> addDigitToPassword(passwordState, key, onPasswordChange)
        "backspace" -> {
            if (passwordState.value.isNotEmpty()) {
                passwordState.value = passwordState.value.dropLast(1)
                onPasswordChange()
            }
        }

        "proceed" -> {
            if (passwordState.value.length < minLength) {
                if (!appLockRepository.shouldDisableHaptics()) {
                    vibrate(context, 100)
                }
                passwordState.value = ""
                return
            }
            if (passwordState.value.length >= minLength) {
                if (fromMainActivity) {
                    if (appLockRepository.validatePassword(passwordState.value)) {
                        onAuthSuccess()
                    } else {
                        passwordState.value = ""
                        if (!appLockRepository.shouldDisableHaptics()) {
                            vibrate(context, 100)
                        }
                        onPinIncorrect()
                    }
                } else {
                    onPinAttempt?.let { attempt ->
                        val pinWasCorrectAndProcessed = attempt(passwordState.value)
                        if (!pinWasCorrectAndProcessed) {
                            passwordState.value = ""
                            if (!appLockRepository.shouldDisableHaptics()) {
                                vibrate(context, 100)
                            }
                        }
                    } ?: run {
                        Log.e(
                            "PasswordOverlayScreen",
                            "onPinAttempt callback is null for app unlock path."
                        )
                        passwordState.value = ""
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeypadRow(
    disableHaptics: Boolean = false,
    keys: List<String>,
    icons: List<ImageVector?> = emptyList(),
    onKeyClick: (String) -> Unit,
    buttonSize: Dp,
    buttonSpacing: Dp
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier,
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEachIndexed { index, key ->
            val interactionSource = remember { MutableInteractionSource() }

            val isPressed by interactionSource.collectIsPressedAsState()

            val targetColor = if (isPressed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                if (icons.isNotEmpty() && index < icons.size && icons[index] != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright
            }

            val animatedContainerColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 150),
                label = "ButtonContainerColorAnimation"
            )

            val normalTextSize = MaterialTheme.typography.headlineLargeEmphasized.fontSize

            val targetFontSize = if (isPressed) normalTextSize * 1.2f else normalTextSize

            val animatedFontSize by animateFloatAsState(
                targetValue = targetFontSize.value,
                animationSpec = tween(durationMillis = 100),
                label = "ButtonTextSizeAnimation"
            )

            FilledTonalButton(
                onClick = {
                    if (!disableHaptics) vibrate(context, 100)
                    onKeyClick(key)
                },
                modifier = Modifier.size(buttonSize),
                interactionSource = interactionSource,
                shapes = ButtonShapes(
                    shape = CircleShape,
                    pressedShape = RoundedCornerShape(25),
                ),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = animatedContainerColor,
                ),
                elevation = ButtonDefaults.filledTonalButtonElevation()
            ) {
                val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

                if (icons.isNotEmpty() && index < icons.size && icons[index] != null) {
                    Icon(
                        imageVector = icons[index]!!,
                        contentDescription = key,
                        modifier = Modifier.size(buttonSize * 0.45f),
                        tint = contentColor
                    )
                } else {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.headlineLargeEmphasized.copy(
                            fontSize = animatedFontSize.sp
                        ),
                    )
                }
            }
        }
    }
}
