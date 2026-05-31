package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recommendation_candidates",
    indices = [
        Index(value = ["videoId"], unique = true),
        Index(value = ["artistId"]),
        Index(value = ["genre"])
    ]
)
data class RecommendationCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val albumId: String,
    val albumName: String,
    val genre: String,
    val durationMs: Long,
    val popularityScore: Float,
    val metadataTokens: String,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false
)