package dev.pranav.applock.features.totp.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.pranav.applock.features.setpassword.ui.MethodSwitchButtons
import dev.pranav.applock.features.setpassword.ui.SetPasswordLockMethod
import dev.pranav.applock.R
import dev.pranav.applock.features.totp.data.TotpSecretStore
import dev.pranav.applock.features.totp.domain.TotpEnrollment
import dev.pranav.applock.features.totp.domain.TotpService
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import java.util.EnumMap

private enum class SetupStep {
    SHOW_SECRET,
    VERIFY
}

@Composable
fun TotpSetupScreen(
    navController: NavController,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { TotpSecretStore(context) }

    var enrollment by remember { mutableStateOf<TotpEnrollment?>(null) }
    var step by remember { mutableStateOf(SetupStep.SHOW_SECRET) }
    var verificationCode by remember { mutableStateOf("") }
    var verificationError by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun createEnrollment() {
        val newEnrollment = TotpService.createEnrollment()
        enrollment = newEnrollment
        step = SetupStep.SHOW_SECRET
        verificationCode = ""
        verificationError = false
        qrBitmap = createQrBitmap(newEnrollment.otpauthUri, 720)
    }

    LaunchedEffect(Unit) {
        // Existing setup is intentionally not overwritten automatically.
        // Start a fresh enrollment only if this screen is explicitly opened.
        createEnrollment()
    }

    val currentEnrollment = enrollment ?: return

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.totp_setup_title),
                        style = MaterialTheme.typography.titleMediumEmphasized
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = if (step == SetupStep.SHOW_SECRET) {
                    stringResource(R.string.totp_setup_scan_title)
                } else {
                    stringResource(R.string.totp_setup_verify_title)
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (step == SetupStep.SHOW_SECRET) {
                    stringResource(R.string.totp_setup_scan_description)
                } else {
                    stringResource(R.string.totp_setup_verify_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            if (step == SetupStep.SHOW_SECRET) {
                qrBitmap?.let { bitmap ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.totp_qr_content_description),
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.totp_manual_setup_label),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = TotpService.formatSecret(currentEnrollment.secret),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(currentEnrollment.secret)
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.totp_copy_secret)
                            )
                        }
                    },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.totp_secret_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { step = SetupStep.VERIFY },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.totp_continue_button))
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = ::createEnrollment,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.totp_generate_new_button))
                }
            } else {
                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = {
                        verificationCode = it.filter(Char::isDigit).take(6)
                        verificationError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.totp_code_label)) },
                    supportingText = {
                        if (verificationError) {
                            Text(
                                stringResource(R.string.totp_invalid_code_error),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    isError = verificationError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val valid = TotpService.verify(
                            currentEnrollment.secret,
                            verificationCode
                        )

                        if (valid) {
                            store.saveSecret(currentEnrollment.secret)
                            onFinished()
                        } else {
                            verificationError = true
                        }
                    },
                    enabled = verificationCode.length == 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.totp_finish_button))
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        step = SetupStep.SHOW_SECRET
                        verificationCode = ""
                        verificationError = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.totp_back_to_qr_button))
                }
            }

            Spacer(Modifier.height(16.dp))

            MethodSwitchButtons(
                currentMethod = SetPasswordLockMethod.TOTP,
                navController = navController,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun createQrBitmap(
    content: String,
    size: Int
): Bitmap {
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.MARGIN, 1)
        put(EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M)
    }

    val matrix: BitMatrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        hints
    )

    val bitmap = Bitmap.createBitmap(
        size,
        size,
        Bitmap.Config.ARGB_8888
    )

    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x,
                y,
                if (matrix.get(x, y)) Color.BLACK
                else android.graphics.Color.WHITE
            )
        }
    }

    return bitmap
}
