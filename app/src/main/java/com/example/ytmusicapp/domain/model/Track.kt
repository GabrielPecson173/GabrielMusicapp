package com.example.ytmusicapp.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val isDownloaded: Boolean = false
)