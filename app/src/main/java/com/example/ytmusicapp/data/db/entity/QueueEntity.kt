package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_queue")
data class QueueEntity(
    @PrimaryKey val mediaId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val streamUrl: String,
    val queueIndex: Int,
    val isCurrentItem: Boolean,
    val durationMs: Long = 0L,
    val sessionId: String = "",
    val addedAtMs: Long = System.currentTimeMillis()
)