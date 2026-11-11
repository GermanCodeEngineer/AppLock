package dev.pranav.applock.features.totp.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.features.totp.domain.TotpService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TotpSetupScreen(onFinished: () -> Unit, onBack: () -> Unit) {
    val repository = LocalContext.current.appLockRepository()
    val scope = rememberCoroutineScope()
    // Pending enrollment is deliberately separate from the active secret.
    val secret = remember { TotpService.generateSecret() }
    val qr = remember(secret) {
        val matrix = MultiFormatWriter().encode(TotpService.buildOtpauthUri(secret), BarcodeFormat.QR_CODE, 512, 512)
        val pixels = IntArray(512 * 512) { index -> if (matrix[index % 512, index / 512]) Color.BLACK else Color.WHITE }
        Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    BackHandler { if (!busy) onBack() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp).imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.totp_setup_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.totp_setup_description))
            Image(qr, stringResource(R.string.totp_qr_description), modifier = Modifier.size(240.dp))
            SelectionContainer { Text(secret.chunked(4).joinToString(" ")) }
            Text(stringResource(R.string.totp_secret_warning))
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6 && it.all { char -> char in '0'..'9' }) { code = it; error = null } },
                label = { Text(stringResource(R.string.totp_code_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !busy
            )
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(enabled = code.length == 6 && !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { repository.enableTotp(secret, code) } }
                    busy = false
                    if (result.getOrDefault(false)) onFinished()
                    else error = if (result.isFailure) R.string.authentication_save_error else R.string.totp_invalid_code
                }
            }) { Text(stringResource(R.string.totp_enable_button)) }
            TextButton(enabled = !busy, onClick = onBack) { Text(stringResource(R.string.totp_other_method)) }
        }
    }
}
