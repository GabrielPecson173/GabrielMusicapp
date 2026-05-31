package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "suppression_list",
    indices = [Index(value = ["videoId"], unique = true)]
)
data class SuppressionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val suppressedUntilMs: Long
)