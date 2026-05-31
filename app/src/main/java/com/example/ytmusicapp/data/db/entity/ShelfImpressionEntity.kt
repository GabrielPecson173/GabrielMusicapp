package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shelf_impressions",
    indices = [
        Index(value = ["sectionId"]),
        Index(value = ["timestamp"])
    ]
)
data class ShelfImpressionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: String,
    val timestamp: Long,
    val clickCount: Int = 0,
    val impressionCount: Int = 0
)