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
    data class Player(
        val trackId: String
    ) : Screen()
}