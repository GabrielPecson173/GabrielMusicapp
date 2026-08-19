package com.example.ytmusicapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ytmusicapp.data.db.converter.VectorTypeConverter
import com.example.ytmusicapp.data.db.dao.*
import com.example.ytmusicapp.data.db.entity.*

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        DownloadEntity::class,
        QueueEntity::class,
        PlayEventEntity::class,
        SkipEventEntity::class,
        SearchEventEntity::class,
        RecommendationCandidateEntity::class,
        HomeFeedSectionEntity::class,
        TrackEmbeddingEntity::class,
        SuppressionEntity::class,
        SessionPairEntity::class,
        ShelfImpressionEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(VectorTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    // Core
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun queueDao(): QueueDao

    // Recommendation
    abstract fun playEventDao(): PlayEventDao
    abstract fun skipEventDao(): SkipEventDao
    abstract fun searchEventDao(): SearchEventDao
    abstract fun recommendationCandidateDao(): RecommendationCandidateDao
    abstract fun homeFeedSectionDao(): HomeFeedSectionDao
    abstract fun trackEmbeddingDao(): TrackEmbeddingDao
    abstract fun suppressionDao(): SuppressionDao
    abstract fun sessionPairDao(): SessionPairDao
    abstract fun shelfImpressionDao(): ShelfImpressionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playback_queue (
                        mediaId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        thumbnailUrl TEXT,
                        streamUrl TEXT NOT NULL,
                        queueIndex INTEGER NOT NULL,
                        isCurrentItem INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        sessionId TEXT NOT NULL DEFAULT '',
                        addedAtMs INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS play_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoId TEXT NOT NULL, timestamp INTEGER NOT NULL,
                    playbackDurationMs INTEGER NOT NULL, totalDurationMs INTEGER NOT NULL,
                    completionRate REAL NOT NULL, isCompleted INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_videoId ON play_events(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_timestamp ON play_events(timestamp)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS skip_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoId TEXT NOT NULL, timestamp INTEGER NOT NULL,
                    skipPositionMs INTEGER NOT NULL, wasUnder30s INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skip_events_videoId ON skip_events(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skip_events_timestamp ON skip_events(timestamp)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS search_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    queryString TEXT NOT NULL, timestamp INTEGER NOT NULL,
                    clickedVideoId TEXT)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_events_timestamp ON search_events(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_events_clickedVideoId ON search_events(clickedVideoId)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS recommendation_candidates (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoId TEXT NOT NULL, title TEXT NOT NULL,
                    artistId TEXT NOT NULL, artistName TEXT NOT NULL,
                    albumId TEXT NOT NULL, albumName TEXT NOT NULL,
                    genre TEXT NOT NULL, durationMs INTEGER NOT NULL,
                    popularityScore REAL NOT NULL, metadataTokens TEXT NOT NULL,
                    isFavorite INTEGER NOT NULL DEFAULT 0,
                    isDownloaded INTEGER NOT NULL DEFAULT 0)""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recommendation_candidates_videoId ON recommendation_candidates(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recommendation_candidates_artistId ON recommendation_candidates(artistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recommendation_candidates_genre ON recommendation_candidates(genre)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS home_feed_sections (
                    sectionId TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL, displayOrder INTEGER NOT NULL,
                    lastRefreshed INTEGER NOT NULL, isEphemeral INTEGER NOT NULL)""")

                db.execSQL("""CREATE TABLE IF NOT EXISTS track_embeddings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoId TEXT NOT NULL, embedding BLOB NOT NULL)""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_track_embeddings_videoId ON track_embeddings(videoId)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS suppression_list (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoId TEXT NOT NULL, suppressedUntilMs INTEGER NOT NULL)""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_suppression_list_videoId ON suppression_list(videoId)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS session_pairs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoIdA TEXT NOT NULL, videoIdB TEXT NOT NULL,
                    pairWeight REAL NOT NULL, sessionTimestamp INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_pairs_videoIdA ON session_pairs(videoIdA)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_pairs_videoIdB ON session_pairs(videoIdB)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_pairs_sessionTimestamp ON session_pairs(sessionTimestamp)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS shelf_impressions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sectionId TEXT NOT NULL, timestamp INTEGER NOT NULL,
                    clickCount INTEGER NOT NULL DEFAULT 0,
                    impressionCount INTEGER NOT NULL DEFAULT 0)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shelf_impressions_sectionId ON shelf_impressions(sectionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shelf_impressions_timestamp ON shelf_impressions(timestamp)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recommendation_candidates ADD COLUMN thumbnailUrl TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE downloads ADD COLUMN thumbnailUrl TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}