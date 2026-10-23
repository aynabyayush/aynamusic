package com.ayush.aynamusic.ui.settings

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayush.aynamusic.BuildConfig
import com.ayush.aynamusic.R
import com.ayush.aynamusic.data.prefs.AppSettings
import com.ayush.aynamusic.data.prefs.AudioQuality
import com.ayush.aynamusic.data.prefs.ThemeMode
import com.ayush.aynamusic.ui.components.ScreenTopBar
import com.ayush.aynamusic.ui.theme.Acid
import com.ayush.aynamusic.ui.theme.Mint

private data class RegionOption(val code: String, val label: String)

private val regions = listOf(
    RegionOption("IN", "India"),
    RegionOption("US", "United States"),
    RegionOption("GB", "United Kingdom"),
    RegionOption("AU", "Australia"),
    RegionOption("BR", "Brazil"),
    RegionOption("CA", "Canada"),
    RegionOption("DE", "Germany"),
    RegionOption("FR", "France"),
    RegionOption("JP", "Japan"),
    RegionOption("KR", "South Korea"),
    RegionOption("MX", "Mexico"),
    RegionOption("NG", "Nigeria")
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    online: Boolean,
    audioPermission: Boolean,
    onBack: () -> Unit,
    onOpenCloud: () -> Unit,
    onThemeChanged: (ThemeMode) -> Unit,
    onAmoledChanged: (Boolean) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onOfflineOnlyChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAudioQualityChanged: (AudioQuality) -> Unit,
    onAutoCloudChanged: (Boolean) -> Unit,
    onRegionChanged: (String) -> Unit,
    onSyncNow: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onScanLocal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var regionMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        ScreenTopBar(title = "Settings", onBack = onBack)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val side = if (maxWidth > 880.dp) (maxWidth - 800.dp) / 2 else 20.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    SettingsHero()
                }
                item {
                    SettingsSectionTitle("Appearance", "Make it unmistakably yours.")
                    SettingsCard {
                        ThemePicker(selected = settings.themeMode, onSelect = onThemeChanged)
                        SettingsDivider()
                        SettingSwitchRow(
                            icon = Icons.Rounded.DarkMode,
                            title = "Pure black",
                            subtitle = "Deeper blacks for AMOLED displays",
                            checked = settings.amoled,
                            enabled = settings.themeMode != ThemeMode.LIGHT,
                            onCheckedChange = onAmoledChanged
                        )
                        SettingsDivider()
                        SettingSwitchRow(
                            icon = Icons.Rounded.Palette,
                            title = "Dynamic color",
                            subtitle = if (Build.VERSION.SDK_INT >= 31) {
                                "Blend the palette with your wallpaper"
                            } else {
                                "Requires Android 12 or newer"
                            },
                            checked = settings.dynamicColor,
                            enabled = Build.VERSION.SDK_INT >= 31,
                            onCheckedChange = onDynamicColorChanged
                        )
                    }
                }

                item {
                    SettingsSectionTitle("Playback", "Quietly efficient by default.")
                    SettingsCard {
                        SettingSwitchRow(
                            icon = Icons.Rounded.CloudOff,
                            title = "Offline mode",
                            subtitle = "Only play downloaded and on-device music",
                            checked = settings.offlineOnly,
                            onCheckedChange = onOfflineOnlyChanged
                        )
                        SettingsDivider()
                        SettingSwitchRow(
                            icon = Icons.Rounded.Wifi,
                            title = "Download on Wi-Fi only",
                            subtitle = "Save mobile data when saving tracks",
                            checked = settings.wifiOnlyDownloads,
                            onCheckedChange = onWifiOnlyChanged
                        )
                        SettingsDivider()
                        Box {
                            SettingRow(
                                icon = Icons.Rounded.Headphones,
                                title = "Streaming audio quality",
                                subtitle = settings.audioQuality.label,
                                onClick = { qualityMenu = true },
                                trailing = {
                                    Icon(
                                        Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = qualityMenu,
                                onDismissRequest = { qualityMenu = false }
                            ) {
                                AudioQuality.entries.forEach { quality ->
                                    DropdownMenuItem(
                                        text = { Text(quality.label) },
                                        trailingIcon = {
                                            if (settings.audioQuality == quality) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            qualityMenu = false
                                            onAudioQualityChanged(quality)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsSectionTitle("Cloud backup", "Your library, quietly in sync.")
                    SettingsCard {
                        SettingRow(
                            icon = if (settings.cloudConfigured) {
                                if (settings.lastSyncOk) Icons.Rounded.CloudDone else Icons.Rounded.Cloud
                            } else {
                                Icons.Rounded.CloudOff
                            },
                            title = "WebDAV backup",
                            subtitle = when {
                                !settings.cloudConfigured -> "Not configured · tap to connect a private cloud"
                                settings.lastSyncAt > 0L -> "Last sync ${relativeTime(settings.lastSyncAt)} · tap to manage"
                                else -> "Ready · tap to manage your endpoint"
                            },
                            onClick = onOpenCloud,
                            trailing = {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                        if (settings.cloudConfigured) {
                            SettingsDivider()
                            SettingSwitchRow(
                                icon = Icons.Rounded.Sync,
                                title = "Automatic sync",
                                subtitle = "Back up changes when you reconnect",
                                checked = settings.autoCloudSync,
                                onCheckedChange = onAutoCloudChanged
                            )
                            SettingsDivider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (settings.lastSyncMessage.isBlank()) "Favorites, playlists and recent history only."
                                    else settings.lastSyncMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (settings.lastSyncMessage.isNotBlank() && !settings.lastSyncOk) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onSyncNow, enabled = online) {
                                    Text("Sync now")
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsSectionTitle("Music discovery", "Full tracks and previews from lawful catalogs.")
                    SettingsCard {
                        Box {
                            SettingRow(
                                icon = Icons.Rounded.Public,
                                title = "Charts & search region",
                                subtitle = regions.firstOrNull { it.code == settings.region }?.label
                                    ?: settings.region,
                                onClick = { regionMenu = true },
                                trailing = {
                                    Icon(
                                        Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = regionMenu,
                                onDismissRequest = { regionMenu = false }
                            ) {
                                regions.forEach { region ->
                                    DropdownMenuItem(
                                        text = { Text(region.label) },
                                        trailingIcon = {
                                            if (settings.region == region.code) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            regionMenu = false
                                            onRegionChanged(region.code)
                                        }
                                    )
                                }
                            }
                        }
                        SettingsDivider()
                        SettingRow(
                            icon = Icons.Rounded.MusicNote,
                            title = "Audius",
                            subtitle = "Full-length independent music · always legal",
                            trailing = {
                                SourcePill("FULL")
                            }
                        )
                        SettingsDivider()
                        SettingRow(
                            icon = Icons.Rounded.Headphones,
                            title = "Apple Music",
                            subtitle = "Global charts and search · 30-second previews",
                            trailing = {
                                SourcePill("PREVIEW")
                            }
                        )
                        SettingsDivider()
                        SettingRow(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "Full commercial releases",
                            subtitle = "Open tracks in Spotify, YouTube Music or JioSaavn",
                            trailing = {
                                SourcePill("LINKS")
                            }
                        )
                    }
                }

                item {
                    SettingsSectionTitle("On-device music", "Private, instant, and offline.")
                    SettingsCard {
                        SettingRow(
                            icon = Icons.Rounded.Download,
                            title = "Audio access",
                            subtitle = if (audioPermission) "Your local library is connected" else "Allow access to read audio files",
                            onClick = if (audioPermission) onScanLocal else onRequestAudioPermission,
                            trailing = {
                                if (audioPermission) {
                                    Text(
                                        "Rescan",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Button(
                                        onClick = onRequestAudioPermission,
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text("Allow")
                                    }
                                }
                            }
                        )
                        SettingsDivider()
                        SettingRow(
                            icon = Icons.Rounded.PlayCircle,
                            title = "Offline downloads",
                            subtitle = "Stored in AYNAMusic/downloads · removable anytime"
                        )
                    }
                }

                item {
                    SettingsSectionTitle("About")
                    SettingsCard {
                        SettingRow(
                            icon = Icons.Rounded.Info,
                            title = "AYNAMusic ${BuildConfig.VERSION_NAME}",
                            subtitle = "A considered player for everywhere you listen.",

                        )
                        SettingsDivider()
                        Text(
                            "No account, no ads, no trackers. Catalog data belongs to its respective creators and platforms. Manrope is licensed under the SIL Open Font License.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                item {
                    Text(
                        "Designed and Developed by AYNAbyAyush (Ayush Narayan).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHero() {
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
                        Color(0xFF0B0D12),
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "AYNAMusic App Icon",
                    modifier = Modifier.size(52.dp)
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text("AYNAMusic", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Online discovery, offline freedom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 9.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(Icons.Rounded.BrightnessAuto)
            Column(Modifier.padding(start = 13.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Follow the system, or choose your own",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeOption(
                label = "System",
                icon = Icons.Rounded.BrightnessAuto,
                selected = selected == ThemeMode.SYSTEM,
                onClick = { onSelect(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                label = "Light",
                icon = Icons.Rounded.LightMode,
                selected = selected == ThemeMode.LIGHT,
                onClick = { onSelect(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                label = "Dark",
                icon = Icons.Rounded.DarkMode,
                selected = selected == ThemeMode.DARK,
                onClick = { onSelect(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(icon)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 13.dp, end = 8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(37.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun SourcePill(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun relativeTime(at: Long): String {
    if (at <= 0) return "never"
    return DateUtils.getRelativeTimeSpanString(
        at,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString().lowercase()
}
