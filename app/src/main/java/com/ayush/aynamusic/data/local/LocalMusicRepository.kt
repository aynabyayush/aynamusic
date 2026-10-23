package com.ayush.aynamusic.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicRepository(private val context: Context) {

    fun hasAudioPermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) return@withContext emptyList()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.YEAR
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= 15000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val tracks = ArrayList<Track>(512)
        runCatching {
            context.contentResolver.query(collection, projection, selection, null, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                while (cursor.moveToNext() && tracks.size < 20_000) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)
                        ?: cursor.getString(displayCol)?.substringBeforeLast('.')
                        ?: "Unknown track"
                    val artist = cursor.getString(artistCol)
                        ?.takeUnless { it.isBlank() || it == "<unknown>" }
                        ?: "Unknown artist"
                    val album = cursor.getString(albumCol)
                        ?.takeUnless { it.isBlank() || it == "<unknown>" }
                    val duration = cursor.getLong(durationCol)
                    if (duration <= 0) continue
                    val uri = ContentUris.withAppendedId(collection, id)
                    tracks += Track(
                        id = "local:$id",
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = null,
                        durationMs = duration,
                        source = TrackSource.LOCAL,
                        streamUrl = uri.toString(),
                        preview = false,
                        releaseYear = cursor.getString(yearCol)?.takeIf { it.length >= 4 }?.take(4)
                    )
                }
            }
        }
        tracks
    }
}
