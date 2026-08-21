package com.example.ytmusicapp.domain.model

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val year: Int?,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false
)
