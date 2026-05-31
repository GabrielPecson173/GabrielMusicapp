package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_pairs",
    indices = [
        Index(value = ["videoIdA"]),
        Index(value = ["videoIdB"]),
        Index(value = ["sessionTimestamp"])
    ]
)
data class SessionPairEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoIdA: String,
    val videoIdB: String,
    val pairWeight: Float,
    val sessionTimestamp: Long
)