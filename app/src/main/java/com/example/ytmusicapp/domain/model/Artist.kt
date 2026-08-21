package com.example.ytmusicapp.domain.model

data class Artist(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val songCount: Int = 0,
    val popular: List<Track> = emptyList(),
    val popularityRanked: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val singles: List<Track> = emptyList(),
    val allSongs: List<Track> = emptyList()
)
