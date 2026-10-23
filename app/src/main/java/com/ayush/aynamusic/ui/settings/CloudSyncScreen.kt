package com.ayush.aynamusic.ui.settings

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ayush.aynamusic.data.prefs.AppSettings
import com.ayush.aynamusic.ui.components.ScreenTopBar
import com.ayush.aynamusic.ui.theme.Acid
import com.ayush.aynamusic.ui.theme.Mint

@Composable
fun CloudSyncScreen(
    settings: AppSettings,
    online: Boolean,
    onBack: () -> Unit,
    onSaveConfig: (String, String, String) -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onAutoSyncChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by rememberSaveable(settings.cloudUrl) { mutableStateOf(settings.cloudUrl) }
    var user by rememberSaveable(settings.cloudUser) { mutableStateOf(settings.cloudUser) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var showDisconnect by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun attemptSave() {
        val clean = url.trim()
        val parsed = runCatching { Uri.parse(clean) }.getOrNull()
        if (!clean.startsWith("https://", ignoreCase = true) ||
            parsed?.host.isNullOrBlank()
        ) {
            localError = "Enter a complete HTTPS URL (for example, https://cloud.example.com/music.json)"
            return
        }
        localError = null
        onSaveConfig(clean, user.trim(), password)
        password = ""
    }

    Column(modifier.fillMaxSize()) {
        ScreenTopBar(title = "Cloud backup", onBack = onBack)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val side = if (maxWidth > 800.dp) (maxWidth - 720.dp) / 2 else 20.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    CloudHero(settings = settings)
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Your private WebDAV endpoint",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 9.dp)
                                )
                            }
                            Text(
                                "AYNAMusic writes a single portable JSON backup. Your music files never leave this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 5.dp, bottom = 14.dp)
                            )

                            OutlinedTextField(
                                value = url,
                                onValueChange = {
                                    url = it.trim()
                                    localError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("HTTPS backup file URL") },
                                placeholder = { Text("https://cloud.example.com/dav/AYNAMusic.json") },
                                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Next
                                ),
                                isError = localError != null
                            )
                            Text(
                                "Use the full HTTPS URL to a .json file. WebDAV PUT must be enabled for this path.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                modifier = Modifier.padding(start = 4.dp, top = 5.dp, bottom = 10.dp)
                            )
                            OutlinedTextField(
                                value = user,
                                onValueChange = { user = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Username") },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                )
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                label = {
                                    Text(
                                        if (settings.cloudConfigured) "Password (leave blank to keep current)"
                                        else "Password"
                                    )
                                },
                                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                visualTransformation = if (passwordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    focus.clearFocus()
                                    attemptSave()
                                })
                            )

                            if (localError != null) {
                                Text(
                                    localError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 4.dp, top = 5.dp)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        focus.clearFocus()
                                        attemptSave()
                                    },
                                    enabled = url.isNotBlank(),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(if (settings.cloudConfigured) "Save & sync" else "Connect & sync")
                                }
                            }

                            Row(
                                modifier = Modifier.padding(top = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Credentials are encrypted with the Android Keystore and stay on this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (settings.cloudConfigured) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .background(
                                                if (settings.lastSyncOk) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                },
                                                RoundedCornerShape(14.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (settings.lastSyncOk) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                            contentDescription = null,
                                            tint = if (settings.lastSyncOk) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(start = 12.dp)
                                    ) {
                                        Text(
                                            when {
                                                !online -> "Waiting for a connection"
                                                settings.lastSyncOk -> "Backup is up to date"
                                                settings.lastSyncAt > 0 -> "Last sync needs attention"
                                                else -> "Ready for your first sync"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            buildString {
                                                append(
                                                    if (settings.lastSyncAt > 0) {
                                                        "Last synced ${relativeSync(settings.lastSyncAt)}"
                                                    } else {
                                                        "Not synced yet"
                                                    }
                                                )
                                                append(" · favorites, playlists, history")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (settings.lastSyncMessage.isNotBlank()) {
                                    Text(
                                        settings.lastSyncMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (settings.lastSyncOk) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 10.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Automatic sync",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = settings.autoCloudSync,
                                        onCheckedChange = onAutoSyncChanged
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!online) {
                                        Icon(
                                            Icons.Rounded.WifiOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "Sync resumes on reconnect",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 6.dp)
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    TextButton(onClick = onSyncNow, enabled = online) {
                                        Text("Sync now")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            onClick = { showDisconnect = true },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Disconnect cloud backup",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(16.dp)
                        )
                        Text(
                            "Only library metadata is uploaded. Downloads and local audio stay on your device. AYNAMusic never uploads files, passwords, or listening analytics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDisconnect) {
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text("Disconnect cloud backup?") },
            text = { Text("Automatic sync will stop and the saved password will be removed. Your remote backup file will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnect = false
                        onDisconnect()
                    }
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnect = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CloudHero(settings: AppSettings) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .background(
                        Brush.linearGradient(listOf(Acid, Mint)),
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (settings.lastSyncOk) Icons.Rounded.CloudDone else Icons.Rounded.CloudUpload,
                    contentDescription = null,
                    tint = Color(0xFF0B0D12),
                    modifier = Modifier.size(27.dp)
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    if (settings.cloudConfigured) "Cloud backup connected" else "Keep your library everywhere",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (settings.cloudConfigured) {
                        "Your liked songs and playlists are portable."
                    } else {
                        "Bring your own private WebDAV storage. No subscription, no middleman."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun relativeSync(at: Long): String {
    if (at <= 0) return "never"
    return DateUtils.getRelativeTimeSpanString(
        at,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString().lowercase()
}
