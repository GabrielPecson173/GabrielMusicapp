package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_feed_sections")
data class HomeFeedSectionEntity(
    @PrimaryKey val sectionId: String,
    val title: String,
    val displayOrder: Int,
    val lastRefreshed: Long,
    val isEphemeral: Boolean
)