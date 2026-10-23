package com.ayush.aynamusic.data.model

import android.net.Uri
import java.io.File

enum class TrackSource { AUDIUS, APPLE, SAAVN, DEEZER, JAMENDO, LOCAL }

enum class DownloadState { NONE, QUEUED, DOWNLOADING, DOWNLOADED, FAILED }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val source: TrackSource,
    val streamUrl: String,
    val externalUrl: String? = null,
    val preview: Boolean = false,
    val genre: String? = null,
    val releaseYear: String? = null,
    val isFavorite: Boolean = false,
    val downloadState: DownloadState = DownloadState.NONE,
    val localFilePath: String? = null
) {
    val isLocal: Boolean get() = source == TrackSource.LOCAL
    val isDownloaded: Boolean get() = downloadState == DownloadState.DOWNLOADED
    val playableUri: String
        get() = localFilePath?.takeIf { isDownloaded && File(it).exists() }
            ?.let { Uri.fromFile(File(it)).toString() }
            ?: streamUrl

    val sourceLabel: String
        get() = when {
            isLocal -> "On device"
            source == TrackSource.SAAVN -> "JioSaavn"
            source == TrackSource.AUDIUS -> "Audius"
            source == TrackSource.JAMENDO -> "Jamendo"
            source == TrackSource.DEEZER -> "Deezer preview"
            preview -> "Apple Music preview"
            else -> "Apple Music"
        }

    val artworkRequest: Any?
        get() = when {
            isLocal && streamUrl.startsWith("content://") -> LocalArtwork(Uri.parse(streamUrl))
            !artworkUrl.isNullOrBlank() -> artworkUrl
            else -> null
        }
}

/** Coil model that extracts embedded artwork from an audio content URI. */
data class LocalArtwork(val audioUri: Uri)

data class UserTrackState(
    val id: String,
    val favorite: Boolean,
    val downloadState: DownloadState,
    val localFilePath: String?
)

data class PlaylistSummary(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
    val coverUrl: String?
)

data class CatalogSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>
)

data class HomeFeed(
    val sections: List<CatalogSection>,
    val updatedAt: Long
)

data class SearchResults(
    val local: List<Track> = emptyList(),
    val full: List<Track> = emptyList(),
    val previews: List<Track> = emptyList()
) {
    val isEmpty: Boolean get() = local.isEmpty() && full.isEmpty() && previews.isEmpty()
    val total: Int get() = local.size + full.size + previews.size
}

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val synced: List<LyricLine>,
    val plain: String?,
    val instrumental: Boolean = false
)

data class SpotifyParsedTrack(val title: String, val artist: String)

data class SpotifyParsedPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val tracks: List<SpotifyParsedTrack>
)

data class PlaylistImportState(
    val playlistId: String,
    val playlistName: String,
    val current: Int,
    val total: Int,
    val status: String,
    val isDone: Boolean = false
) {
    val progressFraction: Float
        get() = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}
