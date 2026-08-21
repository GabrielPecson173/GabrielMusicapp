package com.example.ytmusicapp.ui.navigation

import kotlinx.serialization.Serializable

sealed class Screen {

    @Serializable
    object Home : Screen()

    @Serializable
    object Search : Screen()

    @Serializable
    object Library : Screen()

    @Serializable
    object Downloads : Screen()

    @Serializable
    object Settings : Screen()

    @Serializable
    data class Player(
        val trackId: String
    ) : Screen()

    @Serializable
    data class Artist(
        val artistKey: String
    ) : Screen()

    @Serializable
    data class Album(
        val artistKey: String,
        val albumName: String
    ) : Screen()

    @Serializable
    data class ArtistSection(
        val artistKey: String,
        val section: String
    ) : Screen()
}