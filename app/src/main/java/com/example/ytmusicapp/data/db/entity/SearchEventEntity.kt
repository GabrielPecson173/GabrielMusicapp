package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["clickedVideoId"])
    ]
)
data class SearchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val queryString: String,
    val timestamp: Long,
    val clickedVideoId: String?
)