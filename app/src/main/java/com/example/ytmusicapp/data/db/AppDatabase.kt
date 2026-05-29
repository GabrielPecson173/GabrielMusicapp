package com.example.ytmusicapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ytmusicapp.data.db.dao.DownloadDao
import com.example.ytmusicapp.data.db.dao.PlaylistDao
import com.example.ytmusicapp.data.db.dao.QueueDao
import com.example.ytmusicapp.data.db.dao.TrackDao
import com.example.ytmusicapp.data.db.entity.DownloadEntity
import com.example.ytmusicapp.data.db.entity.PlaylistEntity
import com.example.ytmusicapp.data.db.entity.QueueEntity
import com.example.ytmusicapp.data.db.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        DownloadEntity::class,
        QueueEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun queueDao(): QueueDao

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
    }
}