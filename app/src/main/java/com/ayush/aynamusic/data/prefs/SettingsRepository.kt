package com.ayush.aynamusic.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ayna_settings"
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AudioQuality(val label: String, val bitRateKbps: Int, val suffix: String) {
    MAXIMUM("Maximum (320 kbps)", 320, "_320"),
    HIGH("High (160 kbps)", 160, "_160"),
    MEDIUM("Medium (96 kbps)", 96, "_96")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
    val offlineOnly: Boolean = false,
    val wifiOnlyDownloads: Boolean = true,
    val audioQuality: AudioQuality = AudioQuality.MAXIMUM,
    val autoCloudSync: Boolean = true,
    val region: String = "IN",
    val cloudUrl: String = "",
    val cloudUser: String = "",
    val lastSyncAt: Long = 0L,
    val lastSyncOk: Boolean = false,
    val lastSyncMessage: String = "",
    val recentSearches: List<String> = emptyList(),
    val localScanVersion: Long = 0L
) {
    val cloudConfigured: Boolean get() = cloudUrl.isNotBlank()
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val amoled = booleanPreferencesKey("amoled")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val offlineOnly = booleanPreferencesKey("offline_only")
        val wifiOnlyDownloads = booleanPreferencesKey("wifi_only_downloads")
        val audioQuality = stringPreferencesKey("audio_quality")
        val autoCloudSync = booleanPreferencesKey("auto_cloud_sync")
        val region = stringPreferencesKey("region")
        val cloudUrl = stringPreferencesKey("cloud_url")
        val cloudUser = stringPreferencesKey("cloud_user")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncOk = booleanPreferencesKey("last_sync_ok")
        val lastSyncMessage = stringPreferencesKey("last_sync_message")
        val recentSearches = stringPreferencesKey("recent_searches")
        val localScanVersion = longPreferencesKey("local_scan_version")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { prefs ->
            AppSettings(
                themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: "SYSTEM") }
                    .getOrDefault(ThemeMode.SYSTEM),
                amoled = prefs[Keys.amoled] ?: false,
                dynamicColor = prefs[Keys.dynamicColor] ?: false,
                offlineOnly = prefs[Keys.offlineOnly] ?: false,
                wifiOnlyDownloads = prefs[Keys.wifiOnlyDownloads] ?: true,
                audioQuality = runCatching { AudioQuality.valueOf(prefs[Keys.audioQuality] ?: "MAXIMUM") }
                    .getOrDefault(AudioQuality.MAXIMUM),
                autoCloudSync = prefs[Keys.autoCloudSync] ?: true,
                region = prefs[Keys.region] ?: "IN",
                cloudUrl = prefs[Keys.cloudUrl] ?: "",
                cloudUser = prefs[Keys.cloudUser] ?: "",
                lastSyncAt = prefs[Keys.lastSyncAt] ?: 0L,
                lastSyncOk = prefs[Keys.lastSyncOk] ?: false,
                lastSyncMessage = prefs[Keys.lastSyncMessage] ?: "",
                recentSearches = parseSearches(prefs[Keys.recentSearches]),
                localScanVersion = prefs[Keys.localScanVersion] ?: 0L
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.theme] = mode.name }
    }

    suspend fun setAmoled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.amoled] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun setOfflineOnly(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.offlineOnly] = enabled }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.settingsDataStore.edit { it[Keys.audioQuality] = quality.name }
    }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.wifiOnlyDownloads] = enabled }
    }

    suspend fun setAutoCloudSync(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoCloudSync] = enabled }
    }

    suspend fun setRegion(region: String) {
        context.settingsDataStore.edit { it[Keys.region] = region }
    }

    suspend fun saveCloudConfig(url: String, user: String) {
        context.settingsDataStore.edit {
            it[Keys.cloudUrl] = url.trim()
            it[Keys.cloudUser] = user.trim()
        }
    }

    suspend fun clearCloudConfig() {
        context.settingsDataStore.edit {
            it.remove(Keys.cloudUrl)
            it.remove(Keys.cloudUser)
            it.remove(Keys.lastSyncAt)
            it.remove(Keys.lastSyncOk)
            it.remove(Keys.lastSyncMessage)
        }
    }

    suspend fun setSyncResult(ok: Boolean, message: String, at: Long = System.currentTimeMillis()) {
        context.settingsDataStore.edit {
            it[Keys.lastSyncOk] = ok
            it[Keys.lastSyncMessage] = message
            it[Keys.lastSyncAt] = at
        }
    }

    suspend fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        context.settingsDataStore.edit { prefs ->
            val current = parseSearches(prefs[Keys.recentSearches])
            val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) })
                .take(8)
            prefs[Keys.recentSearches] = JSONArray(updated).toString()
        }
    }

    suspend fun clearRecentSearches() {
        context.settingsDataStore.edit { it.remove(Keys.recentSearches) }
    }

    suspend fun bumpLocalScanVersion() {
        context.settingsDataStore.edit {
            it[Keys.localScanVersion] = System.currentTimeMillis()
        }
    }

    private fun parseSearches(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }
}
