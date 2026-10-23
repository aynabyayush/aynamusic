package com.ayush.aynamusic.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.ayush.aynamusic.data.library.LibraryRepository
import com.ayush.aynamusic.data.model.DownloadState
import com.ayush.aynamusic.data.model.Track
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadRepository(
    private val context: Context,
    private val library: LibraryRepository
) {
    private val manager = context.getSystemService(DownloadManager::class.java)

    suspend fun enqueue(track: Track, wifiOnly: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (track.isLocal) return@withContext Result.failure(IllegalStateException("This track is already on your device"))
        if (track.isDownloaded) return@withContext Result.success(Unit)
        if (track.downloadState == DownloadState.DOWNLOADING || track.downloadState == DownloadState.QUEUED) {
            return@withContext Result.failure(IllegalStateException("Download already in progress"))
        }
        val folder = downloadFolder(context)
        folder.mkdirs()
        val target = File(folder, fileName(track))
        if (target.exists()) target.delete()
        val mime = if (track.preview) "audio/mp4" else "audio/mpeg"
        val request = DownloadManager.Request(Uri.parse(track.streamUrl))
            .setTitle(track.title)
            .setDescription(if (track.preview) "${track.artist} • offline preview" else track.artist)
            .setMimeType(mime)
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationUri(Uri.fromFile(target))
        runCatching {
            library.ensureTrack(track)
            val id = manager.enqueue(request)
            library.markDownloadState(
                track.id,
                DownloadState.QUEUED,
                downloadId = id,
                localFilePath = target.absolutePath
            )
            Unit
        }.onFailure {
            library.markDownloadState(track.id, DownloadState.FAILED)
        }
    }

    suspend fun refreshDownloads(): Int = withContext(Dispatchers.IO) {
        var changed = 0
        val pending = library.pendingDownloads()
        for (track in pending) {
            val downloadId = trackDownloadId(track.id)
            if (downloadId == null) {
                if (!track.localFilePath.isNullOrBlank() && File(track.localFilePath).exists()) {
                    library.markDownloadState(track.id, DownloadState.DOWNLOADED, localFilePath = track.localFilePath)
                } else {
                    library.markDownloadState(track.id, DownloadState.FAILED)
                }
                changed++
                continue
            }
            val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
            cursor.use {
                if (!it.moveToFirst()) {
                    library.markDownloadState(track.id, DownloadState.FAILED)
                    changed++
                    return@use
                }
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val path = track.localFilePath
                        if (!path.isNullOrBlank() && File(path).exists() && File(path).length() > 0) {
                            library.markDownloadState(track.id, DownloadState.DOWNLOADED, localFilePath = path)
                            changed++
                        } else {
                            library.markDownloadState(track.id, DownloadState.FAILED)
                            changed++
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        library.markDownloadState(track.id, DownloadState.FAILED)
                        changed++
                    }
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING -> {
                        if (track.downloadState != DownloadState.DOWNLOADING) {
                            library.markDownloadState(
                                track.id,
                                DownloadState.DOWNLOADING,
                                downloadId = downloadId,
                                localFilePath = track.localFilePath
                            )
                            changed++
                        }
                    }
                }
            }
        }
        changed
    }

    suspend fun remove(track: Track) = withContext(Dispatchers.IO) {
        library.getDownloadId(track.id)?.let { runCatching { manager.remove(it) } }
        track.localFilePath?.let { path -> runCatching { File(path).delete() } }
        library.markDownloadState(
            track.id,
            DownloadState.NONE,
            downloadId = null,
            localFilePath = null
        )
        Unit
    }

    fun downloadFolderSize(): Long {
        val folder = downloadFolder(context)
        if (!folder.exists()) return 0L
        return folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearDownloadFolder() {
        downloadFolder(context).deleteRecursively()
    }

    private suspend fun trackDownloadId(trackId: String): Long? = library.getDownloadId(trackId)

    companion object {
        fun downloadFolder(context: Context): File =
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "downloads")

        fun fileName(track: Track): String {
            val safe = track.id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(90)
            val ext = if (track.preview) "m4a" else "mp3"
            return "$safe.$ext"
        }
    }
}
