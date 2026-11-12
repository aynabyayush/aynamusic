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
import com.ayush.aynamusic.data.model.UserInterestProfile
import com.ayush.aynamusic.data.model.UserTrackState
import com.ayush.aynamusic.data.local.UserTrackMemoryEntity
import com.ayush.aynamusic.data.remote.MusicTrackUtils
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
    private val userMemoryDao = database.userTrackMemoryDao()

    val userTrackMemories: Flow<List<UserTrackMemoryEntity>> = userMemoryDao.observeTopAffinityTracks()

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

    suspend fun recordPlaybackMemory(
        track: Track,
        playDurationMs: Long,
        completionRatio: Double
    ) = withContext(Dispatchers.IO) {
        val existing = userMemoryDao.get(track.id)
        val now = System.currentTimeMillis()

        val ratio = completionRatio.coerceIn(0.0, 1.0)
        val isNiche = MusicTrackUtils.isNicheTrack(track)
        val nicheScore = MusicTrackUtils.calculateNicheScore(track)
        val lang = MusicTrackUtils.detectLanguageTag(track) ?: MusicTrackUtils.cleanHtml(track.genre)
        val genre = MusicTrackUtils.extractGenre(track)

        val playCount = (existing?.playCount ?: 0) + 1
        val completed = ratio >= 0.75
        val skipped = ratio < 0.25
        val completionCount = (existing?.completionCount ?: 0) + (if (completed) 1 else 0)
        val skipCount = (existing?.skipCount ?: 0) + (if (skipped) 1 else 0)
        val totalDuration = (existing?.totalPlayDurationMs ?: 0L) + playDurationMs

        val t0 = (existing?.tier0Count ?: 0) + (if (ratio < 0.10) 1 else 0)
        val t1 = (existing?.tier1Count ?: 0) + (if (ratio >= 0.10 && ratio < 0.25) 1 else 0)
        val t2 = (existing?.tier2Count ?: 0) + (if (ratio >= 0.25 && ratio < 0.50) 1 else 0)
        val t3 = (existing?.tier3Count ?: 0) + (if (ratio >= 0.50 && ratio < 0.75) 1 else 0)
        val t4 = (existing?.tier4Count ?: 0) + (if (ratio >= 0.75 && ratio < 0.90) 1 else 0)
        val t5 = (existing?.tier5Count ?: 0) + (if (ratio >= 0.90) 1 else 0)

        val prevAvg = existing?.avgCompletionRatio ?: 0.0
        val avgRatio = ((prevAvg * (playCount - 1)) + ratio) / playCount

        val playlistBonus = (existing?.playlistOccurrenceCount ?: 0) * 5.0
        val favoriteBonus = if (track.isFavorite) 6.0 else 0.0
        val nicheBonus = if (isNiche) 2.0 else 0.0
        val tierWeightedSum = (t5 * 12.0) + (t4 * 7.0) + (t3 * 3.0) + (t2 * -1.0) + (t1 * -5.0) + (t0 * -10.0)

        val affinity = tierWeightedSum + playlistBonus + favoriteBonus + nicheBonus

        val memory = UserTrackMemoryEntity(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            genre = genre,
            language = lang,
            playCount = playCount,
            completionCount = completionCount,
            skipCount = skipCount,
            totalPlayDurationMs = totalDuration,
            lastPlayedAt = now,
            isNiche = isNiche,
            nicheScore = nicheScore,
            affinityScore = affinity,
            source = track.source.name,
            tier0Count = t0,
            tier1Count = t1,
            tier2Count = t2,
            tier3Count = t3,
            tier4Count = t4,
            tier5Count = t5,
            lastCompletionRatio = ratio,
            avgCompletionRatio = avgRatio,
            playlistOccurrenceCount = existing?.playlistOccurrenceCount ?: 0
        )
        userMemoryDao.upsert(memory)
    }

    suspend fun getUserTrackMemory(trackId: String): UserTrackMemoryEntity? = withContext(Dispatchers.IO) {
        userMemoryDao.get(trackId)
    }

    suspend fun getAllUserTrackMemories(): List<UserTrackMemoryEntity> = withContext(Dispatchers.IO) {
        userMemoryDao.getAllMemories()
    }

    suspend fun getNicheTrackMemories(): List<UserTrackMemoryEntity> = withContext(Dispatchers.IO) {
        userMemoryDao.getNicheMemories()
    }

    suspend fun isNicheListenerForGenreLanguage(genre: String?, language: String?): Boolean = withContext(Dispatchers.IO) {
        if (genre.isNullOrBlank() && language.isNullOrBlank()) return@withContext false
        val g = genre.orEmpty()
        val l = language.orEmpty()
        val memories = userMemoryDao.getNicheMemoriesByGenreLanguage(g, l)
        memories.isNotEmpty() && memories.any { it.tier4Count + it.tier5Count > 0 || it.playCount > 1 || it.affinityScore > 0 }
    }

    suspend fun minePlaylistTasteData(): Triple<Map<String, Double>, Map<String, Double>, Set<String>> = withContext(Dispatchers.IO) {
        val allPlaylists = playlists.getAll()
        val minedArtistCounts = mutableMapOf<String, Int>()
        val minedGenreCounts = mutableMapOf<String, Int>()
        val minedTrackIds = mutableSetOf<String>()

        val playlistTrackCounts = mutableMapOf<String, Int>()

        for (p in allPlaylists) {
            val pTracks = playlists.getPlaylistTracks(p.id)
            for (pt in pTracks) {
                minedTrackIds.add(pt.trackId)
                playlistTrackCounts[pt.trackId] = (playlistTrackCounts[pt.trackId] ?: 0) + 1
            }
        }

        val allUserTracksMap = tracks.getByIds(minedTrackIds.toList()).associateBy { it.id }

        for ((trackId, occurrences) in playlistTrackCounts) {
            val trackEntity = allUserTracksMap[trackId] ?: continue
            val normArtist = MusicTrackUtils.normalizeArtist(trackEntity.artist)
            if (normArtist.isNotBlank()) {
                minedArtistCounts[normArtist] = (minedArtistCounts[normArtist] ?: 0) + occurrences
            }
            if (!trackEntity.genre.isNullOrBlank()) {
                val g = trackEntity.genre.lowercase().trim()
                minedGenreCounts[g] = (minedGenreCounts[g] ?: 0) + occurrences
            }

            val existingMem = userMemoryDao.get(trackId)
            if (existingMem != null && existingMem.playlistOccurrenceCount != occurrences) {
                val updatedMem = existingMem.copy(
                    playlistOccurrenceCount = occurrences,
                    affinityScore = existingMem.affinityScore + (occurrences - existingMem.playlistOccurrenceCount) * 5.0
                )
                userMemoryDao.upsert(updatedMem)
            }
        }

        val maxArtistCount = minedArtistCounts.values.maxOrNull()?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
        val normArtists = minedArtistCounts.mapValues { it.value / maxArtistCount }

        val maxGenreCount = minedGenreCounts.values.maxOrNull()?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
        val normGenres = minedGenreCounts.mapValues { it.value / maxGenreCount }

        Triple(normArtists, normGenres, minedTrackIds)
    }

    suspend fun getUserInterestProfile(): UserInterestProfile = withContext(Dispatchers.IO) {
        val memories = userMemoryDao.getAllMemories()
        val allUserTracks = tracks.getAllUserTracks()
        val favIds = allUserTracks.filter { it.favorite }.map { it.id }.toSet()

        val (minedArtists, minedGenres, minedTrackIds) = minePlaylistTasteData()

        if (memories.isEmpty() && favIds.isEmpty() && minedTrackIds.isEmpty()) {
            return@withContext UserInterestProfile()
        }

        val artistScores = mutableMapOf<String, Double>()
        val genreScores = mutableMapOf<String, Double>()
        val langScores = mutableMapOf<String, Double>()
        val highAffinity = mutableSetOf<String>()
        val skipped = mutableSetOf<String>()

        var nicheCount = 0
        var sumAvgRatios = 0.0
        val totalCount = memories.size

        favIds.forEach { highAffinity.add(it) }
        minedTrackIds.forEach { highAffinity.add(it) }

        for (m in memories) {
            if (m.isNiche) nicheCount++
            sumAvgRatios += m.avgCompletionRatio

            val weight = m.affinityScore.coerceAtLeast(0.1)
            val cleanArt = MusicTrackUtils.normalizeArtist(m.artist)
            if (cleanArt.isNotBlank()) {
                artistScores[cleanArt] = (artistScores[cleanArt] ?: 0.0) + weight
            }
            if (!m.genre.isNullOrBlank()) {
                val g = m.genre.lowercase().trim()
                genreScores[g] = (genreScores[g] ?: 0.0) + weight
            }
            if (!m.language.isNullOrBlank()) {
                val l = m.language.lowercase().trim()
                langScores[l] = (langScores[l] ?: 0.0) + weight
            }

            if (m.affinityScore >= 2.0 || m.tier4Count + m.tier5Count >= 1 || m.avgCompletionRatio >= 0.75) {
                highAffinity.add(m.trackId)
            }
            if (m.tier0Count + m.tier1Count >= 2 || (m.tier0Count + m.tier1Count > m.tier4Count + m.tier5Count + 1)) {
                skipped.add(m.trackId)
            }
        }

        val totalArtistSum = artistScores.values.sum().coerceAtLeast(1.0)
        val normalizedArtists = artistScores.mapValues { it.value / totalArtistSum }

        val totalGenreSum = genreScores.values.sum().coerceAtLeast(1.0)
        val normalizedGenres = genreScores.mapValues { it.value / totalGenreSum }

        val totalLangSum = langScores.values.sum().coerceAtLeast(1.0)
        val normalizedLanguages = langScores.mapValues { it.value / totalLangSum }

        val overallAvgRatio = if (totalCount > 0) sumAvgRatios / totalCount.toDouble() else 0.0
        val nicheRatio = if (totalCount > 0) nicheCount.toDouble() / totalCount.toDouble() else 0.0

        UserInterestProfile(
            topArtists = normalizedArtists,
            topGenres = normalizedGenres,
            topLanguages = normalizedLanguages,
            minedPlaylistArtists = minedArtists,
            minedPlaylistGenres = minedGenres,
            minedPlaylistTrackIds = minedTrackIds,
            highAffinityTrackIds = highAffinity,
            skippedTrackIds = skipped,
            avgCompletionRatio = overallAvgRatio,
            nicheRatio = nicheRatio,
            totalTrackedSongs = totalCount
        )
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

    suspend fun updatePlaylistCover(id: String, coverUrl: String?) {
        playlists.updateCover(id, coverUrl?.trim()?.takeIf { it.isNotBlank() })
        CloudSyncScheduler.schedule(context)
    }

    suspend fun deletePlaylist(id: String) {
        playlists.deletePlaylist(id)
        CloudSyncScheduler.schedule(context)
    }

    suspend fun addToPlaylist(playlistId: String, track: Track, scheduleSync: Boolean = true): Boolean {
        val playlist = playlists.get(playlistId) ?: return false
        ensureTrack(track)
        var added = false
        database.withTransaction {
            val position = playlists.maxPosition(playlistId) + 1
            playlists.addTrack(
                PlaylistTrackEntity(playlistId, track.id, position, System.currentTimeMillis())
            )
            playlists.update(playlist.copy(updatedAt = System.currentTimeMillis()))
            added = true
        }
        if (added && scheduleSync) CloudSyncScheduler.schedule(context)
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
