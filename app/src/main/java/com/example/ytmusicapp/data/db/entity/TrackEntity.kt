package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val isDownloaded: Boolean = false
)