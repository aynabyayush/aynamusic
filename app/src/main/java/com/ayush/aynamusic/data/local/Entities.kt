package com.ayush.aynamusic.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.ayush.aynamusic.data.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val source: String,
    val streamUrl: String,
    val externalUrl: String?,
    val preview: Boolean,
    val genre: String?,
    val releaseYear: String?,
    val favorite: Boolean = false,
    val downloadId: Long? = null,
    val downloadState: String = "NONE",
    val localFilePath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val customCoverUrl: String? = null
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId", "position"],
    indices = [Index("trackId")],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_tracks")
data class RecentEntity(
    @PrimaryKey val trackId: String,
    val playedAt: Long
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val instrumental: Boolean,
    val fetchedAt: Long
)

data class UserTrackStateRow(
    val id: String,
    @ColumnInfo(name = "favorite") val favorite: Boolean,
    @ColumnInfo(name = "downloadState") val downloadState: String,
    @ColumnInfo(name = "localFilePath") val localFilePath: String?
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): TrackEntity?

    @Query("SELECT downloadId FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getDownloadId(id: String): Long?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Update
    suspend fun update(track: TrackEntity)

    @Query("UPDATE tracks SET favorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE tracks SET downloadId = :downloadId, downloadState = :state, localFilePath = :path, updatedAt = :now WHERE id = :id")
    suspend fun setDownloadState(id: String, downloadId: Long?, state: String, path: String?, now: Long)

    @Query("SELECT * FROM tracks WHERE favorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE downloadState = 'DOWNLOADED' ORDER BY title COLLATE NOCASE")
    fun observeDownloads(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE downloadState IN ('QUEUED', 'DOWNLOADING', 'FAILED')")
    suspend fun getPendingDownloads(): List<TrackEntity>

    @Query("SELECT id, favorite, downloadState, localFilePath FROM tracks WHERE favorite = 1 OR downloadState != 'NONE'")
    fun observeUserStates(): Flow<List<UserTrackStateRow>>

    @Query("SELECT * FROM tracks WHERE favorite = 1 OR downloadState != 'NONE'")
    suspend fun getAllUserTracks(): List<TrackEntity>

    @Query("DELETE FROM tracks WHERE id = :id AND favorite = 0 AND downloadState = 'NONE' AND id NOT IN (SELECT trackId FROM playlist_tracks) AND id NOT IN (SELECT trackId FROM recent_tracks)")
    suspend fun cleanupOrphan(id: String)
}

@Dao
interface RecentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(recent: RecentEntity)

    @Query("SELECT t.* FROM tracks t INNER JOIN recent_tracks r ON t.id = r.trackId ORDER BY r.playedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<TrackEntity>>

    @Query("SELECT trackId FROM recent_tracks ORDER BY playedAt DESC LIMIT 100")
    suspend fun recentIds(): List<String>

    @Query("DELETE FROM recent_tracks WHERE trackId NOT IN (SELECT trackId FROM recent_tracks ORDER BY playedAt DESC LIMIT 100)")
    suspend fun trim()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecentEntity>)
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity)

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query(
        """
        SELECT p.id, p.name, p.description, p.createdAt, p.updatedAt,
            (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
            COALESCE(p.customCoverUrl,
                (SELECT t.artworkUrl FROM playlist_tracks pt
                 INNER JOIN tracks t ON t.id = pt.trackId
                 WHERE pt.playlistId = p.id ORDER BY pt.position LIMIT 1)
            ) AS coverUrl
        FROM playlists p
        ORDER BY p.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("UPDATE playlists SET customCoverUrl = :coverUrl, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCover(id: String, coverUrl: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTracks(playlistId: String): List<PlaylistTrackEntity>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC"
    )
    fun observeTracks(playlistId: String): Flow<List<TrackEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrack(item: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updatePosition(playlistId: String, trackId: String, position: Int)
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun get(trackId: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics")
    suspend fun clear()
}
