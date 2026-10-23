package com.ayush.aynamusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ayush.aynamusic.cloud.CloudSyncScheduler
import com.ayush.aynamusic.cloud.CloudSyncManager
import com.ayush.aynamusic.data.library.LibraryRepository
import com.ayush.aynamusic.data.local.AppDatabase
import com.ayush.aynamusic.data.local.LocalMusicRepository
import com.ayush.aynamusic.data.network.NetworkMonitor
import com.ayush.aynamusic.data.prefs.SecretStore
import com.ayush.aynamusic.data.prefs.SettingsRepository
import com.ayush.aynamusic.data.remote.LyricsRepository
import com.ayush.aynamusic.data.remote.MusicCatalogRepository
import com.ayush.aynamusic.downloads.DownloadRepository
import com.ayush.aynamusic.imaging.LocalArtworkFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AynaApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        appScope.launch {
            container.downloads.refreshDownloads()
            val settings = container.settings.current()
            if (settings.autoCloudSync && settings.cloudConfigured) {
                CloudSyncScheduler.ensurePeriodic(this@AynaApplication)
                CloudSyncScheduler.schedule(this@AynaApplication)
            }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(LocalArtworkFetcher.Factory()) }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.12)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("artwork"))
                .maxSizeBytes(80L * 1024 * 1024)
                .build()
        }
        .allowHardware(true)
        .crossfade(false)
        .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.WARN)
            .build()

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        val playback = NotificationChannel(
            "ayna_playback",
            getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playback controls and now playing"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(playback)
    }
}

class AppContainer(context: Application) {
    val database: AppDatabase = AppDatabase.get(context)
    val settings = SettingsRepository(context)
    val secrets = SecretStore(context)
    val network = NetworkMonitor(context)
    val library = LibraryRepository(context, database)
    val catalog = MusicCatalogRepository(context, settings)
    val localMusic = LocalMusicRepository(context)
    val downloads = DownloadRepository(context, library)
    val lyrics = LyricsRepository(context, database.lyricsDao())
    val cloudSync = CloudSyncManager(context, library, settings, secrets)
}
