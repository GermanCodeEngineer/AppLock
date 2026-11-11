package dev.pranav.applock.features.settings.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pranav.applock.R
import dev.pranav.applock.data.repository.LockedAppsRepository
import dev.pranav.applock.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ShortcutApp(val packageName: String, val label: String)

@Composable
fun AuthenticationShortcutSetting() {
    val context = LocalContext.current
    val preferences = remember(context) { PreferencesRepository(context) }
    var selected by remember { mutableStateOf(preferences.getAuthenticationShortcutPackage()) }
    var showPicker by remember { mutableStateOf(false) }
    val selectedLabel = remember(selected) {
        selected?.let { pkg -> runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg) }
    }
    SettingsGroup(items = listOf(ActionSettingItem(
        icon = Icons.AutoMirrored.Filled.OpenInNew,
        title = stringResource(R.string.authentication_shortcut_title),
        subtitle = selectedLabel ?: stringResource(R.string.authentication_shortcut_none),
        onClick = { showPicker = true }
    )))
    if (showPicker) {
        var search by remember { mutableStateOf("") }
        val apps by produceState<List<ShortcutApp>?>(null, context) {
            value = withContext(Dispatchers.IO) {
                val locked = LockedAppsRepository(context).getLockedApps()
                context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName && it.activityInfo.packageName !in locked }
                    .map { ShortcutApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
                    .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            }
        }
        fun select(packageName: String?) {
            preferences.setAuthenticationShortcutPackage(packageName)
            selected = packageName
            showPicker = false
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.authentication_shortcut_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.authentication_shortcut_description))
                    OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                        label = { Text(stringResource(R.string.authentication_shortcut_search)) }, modifier = Modifier.fillMaxWidth())
                    if (apps == null) CircularProgressIndicator()
                    else {
                        val filtered = apps.orEmpty().filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
                        if (filtered.isEmpty()) Text(stringResource(R.string.authentication_shortcut_empty))
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(filtered, key = { it.packageName }) { app ->
                                ListItem(headlineContent = { Text(app.label) }, supportingContent = { Text(app.packageName) },
                                    modifier = Modifier.clickable { select(app.packageName) })
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel_button)) } },
            dismissButton = { TextButton(onClick = { select(null) }) { Text(stringResource(R.string.authentication_shortcut_disable)) } }
        )
    }
}
