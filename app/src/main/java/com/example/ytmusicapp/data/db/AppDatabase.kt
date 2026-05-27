package com.example.ytmusicapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.ytmusicapp.data.db.dao.DownloadDao
import com.example.ytmusicapp.data.db.dao.PlaylistDao
import com.example.ytmusicapp.data.db.dao.TrackDao
import com.example.ytmusicapp.data.db.entity.DownloadEntity
import com.example.ytmusicapp.data.db.entity.PlaylistEntity
import com.example.ytmusicapp.data.db.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
}