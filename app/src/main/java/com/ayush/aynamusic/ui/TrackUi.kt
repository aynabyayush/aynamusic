package com.ayush.aynamusic.ui

import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.UserTrackState

fun Track.withUserState(state: UserTrackState?): Track {
    if (state == null) return this
    return copy(
        isFavorite = state.favorite,
        downloadState = state.downloadState,
        localFilePath = state.localFilePath
    )
}

fun Track.isAvailableOffline(): Boolean = isLocal || isDownloaded
