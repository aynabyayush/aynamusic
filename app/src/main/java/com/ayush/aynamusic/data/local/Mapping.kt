package com.ayush.aynamusic.data.local

import com.ayush.aynamusic.data.model.DownloadState
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.TrackSource
import com.ayush.aynamusic.data.model.UserTrackState

fun TrackEntity.toTrack(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    source = runCatching { TrackSource.valueOf(source) }.getOrDefault(TrackSource.AUDIUS),
    streamUrl = streamUrl,
    externalUrl = externalUrl,
    preview = preview,
    genre = genre,
    releaseYear = releaseYear,
    isFavorite = favorite,
    downloadState = runCatching { DownloadState.valueOf(downloadState) }.getOrDefault(DownloadState.NONE),
    localFilePath = localFilePath
)

fun Track.toEntity(
    favorite: Boolean = isFavorite,
    downloadId: Long? = null,
    downloadState: DownloadState = this.downloadState,
    localFilePath: String? = this.localFilePath,
    addedAt: Long = System.currentTimeMillis(),
    updatedAt: Long = System.currentTimeMillis()
): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    source = source.name,
    streamUrl = streamUrl,
    externalUrl = externalUrl,
    preview = preview,
    genre = genre,
    releaseYear = releaseYear,
    favorite = favorite,
    downloadId = downloadId,
    downloadState = downloadState.name,
    localFilePath = localFilePath,
    addedAt = addedAt,
    updatedAt = updatedAt
)

fun Track.mergeUserState(state: UserTrackState?): Track {
    if (state == null) return this
    return copy(
        isFavorite = state.favorite,
        downloadState = state.downloadState,
        localFilePath = state.localFilePath
    )
}

fun UserTrackStateRow.toModel(): UserTrackState = UserTrackState(
    id = id,
    favorite = favorite,
    downloadState = runCatching { DownloadState.valueOf(downloadState) }
        .getOrDefault(DownloadState.NONE),
    localFilePath = localFilePath
)
