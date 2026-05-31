package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_events",
    indices = [
        Index(value = ["videoId"]),
        Index(value = ["timestamp"])
    ]
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val timestamp: Long,
    val playbackDurationMs: Long,
    val totalDurationMs: Long,
    val completionRate: Float,
    val isCompleted: Boolean
)