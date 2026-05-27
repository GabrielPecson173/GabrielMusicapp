package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val filePath: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)