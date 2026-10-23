package com.ayush.aynamusic.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ayush.aynamusic.AynaApplication
import java.io.IOException

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AynaApplication ?: return Result.failure()
        val settings = app.container.settings.current()
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (!manual && !settings.autoCloudSync) return Result.success()
        if (!settings.cloudConfigured) {
            if (manual) {
                app.container.settings.setSyncResult(false, "Add a WebDAV backup URL first")
            }
            return Result.success()
        }
        if (app.container.network.isOnline()) {
            return runCatching {
                val message = app.container.cloudSync.sync()
                app.container.settings.setSyncResult(true, message)
                Result.success()
            }.getOrElse { error ->
                val message = error.friendlyMessage()
                app.container.settings.setSyncResult(false, message)
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        }
        if (manual) {
            app.container.settings.setSyncResult(false, "You're offline. Sync will run when you reconnect.")
        }
        return Result.retry()
    }

    private fun Throwable.friendlyMessage(): String {
        val text = message.orEmpty()
        return when {
            this is IOException && text.contains("401") -> "WebDAV sign-in failed. Check your username and app password."
            this is IOException && text.contains("403") -> "The server refused write access to this backup."
            this is IOException && text.contains("404") -> "The WebDAV folder doesn't exist yet."
            else -> text.ifBlank { "Cloud sync failed. Will retry shortly." }.take(180)
        }
    }

    companion object {
        const val KEY_MANUAL = "manual"
    }
}
