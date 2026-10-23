package com.ayush.aynamusic.data.library

import androidx.room.withTransaction
import com.ayush.aynamusic.cloud.CloudSyncScheduler
import com.ayush.aynamusic.data.local.AppDatabase
import com.ayush.aynamusic.data.local.PlaylistEntity
import com.ayush.aynamusic.data.local.PlaylistTrackEntity
import com.ayush.aynamusic.data.local.RecentEntity
import com.ayush.aynamusic.data.local.TrackEntity
import com.ayush.aynamusic.data.local.toEntity
import com.ayush.aynamusic.data.local.toModel
import com.ayush.aynamusic.data.local.toTrack
import com.ayush.aynamusic.data.model.DownloadState
import com.ayush.aynamusic.data.model.PlaylistSummary
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.UserTrackState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val context: android.content.Context,
    private val database: AppDatabase
) {
    private val tracks = database.trackDao()
    private val playlists = database.playlistDao()
    private val recents = database.recentDao()

    val favorites: Flow<List<Track>> = tracks.observeFavorites()
        .map { list -> list.map(TrackEntity::toTrack) }

    val downloads: Flow<List<Track>> = tracks.observeDownloads()
        .map { list -> list.map(TrackEntity::toTrack) }

    val recent: Flow<List<Track>> = recents.observeRecent()
        .map { list -> list.map(TrackEntity::toTrack) }

    val userStates: Flow<Map<String, UserTrackState>> = tracks.observeUserStates()
        .map { rows -> rows.associate { it.id to it.toModel() } }

    val playlistSummaries: Flow<List<PlaylistSummary>> = playlists.observeSummaries()

    fun playlistTracks(playlistId: String): Flow<List<Track>> =
        playlists.observeTracks(playlistId).map { list -> list.map(TrackEntity::toTrack) }

    suspend fun getTrack(id: String): Track? = tracks.get(id)?.toTrack()

    suspend fun getDownloadId(id: String): Long? = tracks.getDownloadId(id)

    suspend fun getTracks(ids: List<String>): Map<String, Track> =
        tracks.getByIds(ids).associate { it.id to it.toTrack() }

    suspend fun recordPlay(track: Track) {
        val existing = tracks.get(track.id)
        val now = System.currentTimeMillis()
        val entity = if (existing == null) {
            track.toEntity(favorite = track.isFavorite, addedAt = now, updatedAt = now)
        } else {
            existing.copy(
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl ?: existing.artworkUrl,
                durationMs = track.durationMs,
                streamUrl = track.streamUrl,
                externalUrl = track.externalUrl ?: existing.externalUrl,
                updatedAt = now
            )
        }
        tracks.upsert(entity)
        recents.record(RecentEntity(track.id, now))
        recents.trim()
    }

    suspend fun toggleFavorite(track: Track): Boolean {
        val existing = tracks.get(track.id)
        val next = if (existing == null) {
            tracks.upsert(track.copy(isFavorite = true).toEntity(favorite = true))
            true
        } else {
            val favorite = !existing.favorite
            tracks.setFavorite(existing.id, favorite, System.currentTimeMillis())
            favorite
        }
        CloudSyncScheduler.schedule(context)
        return next
    }

    suspend fun setFavorite(track: Track, favorite: Boolean) {
        val existing = tracks.get(track.id)
        if (existing == null) {
            if (favorite) tracks.upsert(track.copy(isFavorite = true).toEntity(favorite = true))
        } else {
            tracks.setFavorite(existing.id, favorite, System.currentTimeMillis())
        }
        CloudSyncScheduler.schedule(context)
    }

    suspend fun ensureTrack(track: Track, favorite: Boolean = track.isFavorite) {
        val existing = tracks.get(track.id)
        if (existing == null) {
            tracks.upsert(track.toEntity(favorite = favorite))
        } else {
            tracks.upsert(
                existing.copy(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.artworkUrl ?: existing.artworkUrl,
                    durationMs = track.durationMs,
                    streamUrl = track.streamUrl,
                    externalUrl = track.externalUrl ?: existing.externalUrl,
                    favorite = existing.favorite || favorite,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun createPlaylist(name: String, description: String? = null): String {
        val clean = name.trim().ifBlank { "New playlist" }.take(80)
        val id = "pl:${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        playlists.insert(
            PlaylistEntity(
                id = id,
                name = clean,
                description = description?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now
            )
        )
        CloudSyncScheduler.schedule(context)
        return id
    }

    suspend fun getPlaylist(id: String): PlaylistEntity? = playlists.get(id)

    suspend fun getAllPlaylists(): List<PlaylistEntity> = playlists.getAll()

    suspend fun renamePlaylist(id: String, name: String) {
        val existing = playlists.get(id) ?: return
        playlists.update(
            existing.copy(
                name = name.trim().ifBlank { existing.name }.take(80),
                updatedAt = System.currentTimeMillis()
            )
        )
        CloudSyncScheduler.schedule(context)
    }

    suspend fun deletePlaylist(id: String) {
        playlists.deletePlaylist(id)
        CloudSyncScheduler.schedule(context)
    }

    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean {
        val playlist = playlists.get(playlistId) ?: return false
        ensureTrack(track)
        var added = false
        database.withTransaction {
            val existingIds = playlists.getPlaylistTracks(playlistId).map { it.trackId }
            if (track.id !in existingIds) {
                val position = playlists.maxPosition(playlistId) + 1
                playlists.addTrack(
                    PlaylistTrackEntity(playlistId, track.id, position, System.currentTimeMillis())
                )
                playlists.update(playlist.copy(updatedAt = System.currentTimeMillis()))
                added = true
            }
        }
        if (added) CloudSyncScheduler.schedule(context)
        return added
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        database.withTransaction {
            playlists.removeTrack(playlistId, trackId)
            playlists.get(playlistId)?.let {
                playlists.update(it.copy(updatedAt = System.currentTimeMillis()))
            }
        }
        CloudSyncScheduler.schedule(context)
        tracks.cleanupOrphan(trackId)
    }

    suspend fun reorderPlaylist(playlistId: String, orderedTrackIds: List<String>) {
        database.withTransaction {
            orderedTrackIds.forEachIndexed { index, id ->
                playlists.updatePosition(playlistId, id, index)
            }
            playlists.get(playlistId)?.let {
                playlists.update(it.copy(updatedAt = System.currentTimeMillis()))
            }
        }
        CloudSyncScheduler.schedule(context)
    }

    suspend fun playlistTrackIds(playlistId: String): List<String> =
        playlists.getPlaylistTracks(playlistId).map { it.trackId }

    suspend fun getAllUserPlaylistTracks(): List<Track> = withContext(Dispatchers.IO) {
        val summaries = playlists.getAll()
        val allTrackIds = summaries.flatMap { p ->
            playlists.getPlaylistTracks(p.id).map { it.trackId }
        }.distinct()
        getTracks(allTrackIds).values.toList()
    }

    suspend fun playlistEntries(playlistId: String): List<Triple<Track, Int, Long>> {
        val items = playlists.getPlaylistTracks(playlistId)
        val tracksById = getTracks(items.map { it.trackId })
        return items.mapNotNull { item ->
            tracksById[item.trackId]?.let { Triple(it, item.position, item.addedAt) }
        }
    }

    suspend fun markDownloadState(
        trackId: String,
        state: DownloadState,
        downloadId: Long? = null,
        localFilePath: String? = null
    ) {
        tracks.setDownloadState(
            id = trackId,
            downloadId = downloadId,
            state = state.name,
            path = localFilePath,
            now = System.currentTimeMillis()
        )
    }

    suspend fun pendingDownloads(): List<Track> = tracks.getPendingDownloads().map { it.toTrack() }

    suspend fun recentIds(): List<String> = recents.recentIds()

    suspend fun importRecent(ids: List<String>) {
        val now = System.currentTimeMillis()
        recents.insertAll(ids.mapIndexed { index, id -> RecentEntity(id, now - index * 1_000L) })
        recents.trim()
    }

    // Cloud restore operations intentionally do not schedule another sync mid-sync.
    suspend fun importFavoritesFromCloud(favorites: List<Track>) {
        database.withTransaction {
            favorites.filter { it.source != com.ayush.aynamusic.data.model.TrackSource.LOCAL }
                .forEach { track ->
                    val existing = tracks.get(track.id)
                    if (existing == null) {
                        tracks.upsert(track.copy(isFavorite = true).toEntity(favorite = true))
                    } else {
                        tracks.setFavorite(track.id, true, System.currentTimeMillis())
                    }
                }
        }
    }

    suspend fun importPlaylistFromCloud(
        playlist: PlaylistEntity,
        items: List<Triple<Track, Int, Long>>
    ) {
        database.withTransaction {
            items.filter { it.first.source != com.ayush.aynamusic.data.model.TrackSource.LOCAL }
                .forEach { (track, _, _) ->
                    if (tracks.get(track.id) == null) tracks.upsert(track.toEntity())
                }
            playlists.insert(playlist)
            playlists.clearTracks(playlist.id)
            items.filter { it.first.source != com.ayush.aynamusic.data.model.TrackSource.LOCAL }
                .sortedBy { it.second }
                .forEach { (track, position, addedAt) ->
                    playlists.addTrack(
                        PlaylistTrackEntity(playlist.id, track.id, position, addedAt)
                    )
                }
        }
    }

    suspend fun importRecentFromCloud(entries: List<Pair<Track, Long>>) {
        database.withTransaction {
            entries.filter { it.first.source != com.ayush.aynamusic.data.model.TrackSource.LOCAL }
                .forEach { (track, playedAt) ->
                    if (tracks.get(track.id) == null) tracks.upsert(track.toEntity())
                    recents.record(RecentEntity(track.id, playedAt))
                }
            recents.trim()
        }
    }
}
