package com.ayush.aynamusic.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        RecentEntity::class,
        LyricsEntity::class,
        UserTrackMemoryEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun recentDao(): RecentDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun userTrackMemoryDao(): UserTrackMemoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN customCoverUrl TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_tracks_new` (
                        `playlistId` TEXT NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `trackId`, `position`),
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                        FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `playlist_tracks_new` (`playlistId`, `trackId`, `position`, `addedAt`)
                    SELECT `playlistId`, `trackId`, `position`, `addedAt` FROM `playlist_tracks`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `playlist_tracks`".trimIndent())
                db.execSQL("ALTER TABLE `playlist_tracks_new` RENAME TO `playlist_tracks`".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` ON `playlist_tracks` (`trackId`)".trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_track_memory` (
                        `trackId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `genre` TEXT,
                        `language` TEXT,
                        `playCount` INTEGER NOT NULL DEFAULT 0,
                        `completionCount` INTEGER NOT NULL DEFAULT 0,
                        `skipCount` INTEGER NOT NULL DEFAULT 0,
                        `totalPlayDurationMs` INTEGER NOT NULL DEFAULT 0,
                        `lastPlayedAt` INTEGER NOT NULL DEFAULT 0,
                        `isNiche` INTEGER NOT NULL DEFAULT 0,
                        `nicheScore` INTEGER NOT NULL DEFAULT 0,
                        `affinityScore` REAL NOT NULL DEFAULT 0.0,
                        `source` TEXT,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_track_memory ADD COLUMN `tier0Count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_track_memory ADD COLUMN `tier1Count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_track_memory ADD COLUMN `tier2Count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_track_memory ADD COLUMN `tier3Count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_track_memory ADD COLUMN `tier4Count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_track_memory ADD COLUMN `tier5Count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_track_memory ADD COLUMN `lastCompletionRatio` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE user_track_memory ADD COLUMN `avgCompletionRatio` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE user_track_memory ADD COLUMN `playlistOccurrenceCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ayna_music.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}
