package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val videoId: String,
    val positionMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis()
)
