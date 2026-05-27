package com.example.ytmusicapp.domain.model

data class Playlist(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val tracks: List<Track> = emptyList()
)