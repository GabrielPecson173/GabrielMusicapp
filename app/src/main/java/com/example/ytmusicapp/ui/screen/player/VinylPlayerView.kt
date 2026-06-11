package com.example.ytmusicapp.ui.screen.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun VinylPlayerView(
    albumArtUrl: String?,
    isPlaying: Boolean,
    size: Dp = 280.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        VinylDisc(
            albumArtUrl = albumArtUrl,
            isPlaying = isPlaying,
            size = size
        )
        Tonearm(
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )
    }
}