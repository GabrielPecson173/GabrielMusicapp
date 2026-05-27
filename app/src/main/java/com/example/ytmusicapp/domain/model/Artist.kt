package com.example.ytmusicapp.domain.model

data class Artist(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val albums: List<Album> = emptyList()
)