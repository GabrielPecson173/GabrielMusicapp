package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "skip_events",
    indices = [
        Index(value = ["videoId"]),
        Index(value = ["timestamp"])
    ]
)
data class SkipEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val timestamp: Long,
    val skipPositionMs: Long,
    val wasUnder30s: Boolean
)