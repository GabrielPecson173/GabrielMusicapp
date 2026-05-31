package com.example.ytmusicapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AmoledColorScheme = darkColorScheme(
    primary          = Red80,
    onPrimary        = OnDark,
    secondary        = Pink80,
    onSecondary      = OnDark,
    tertiary         = Orange80,
    onTertiary       = OnDark,
    background       = DarkBackground,
    onBackground     = OnDark,
    surface          = DarkSurface,
    onSurface        = OnDark,
    surfaceVariant   = DarkSurfaceVariant,
    onSurfaceVariant = OnDark,
)

@Composable
fun YtMusicAppTheme(
    darkTheme: Boolean = true,        // always dark
    dynamicColor: Boolean = false,    // no dynamic — kills AMOLED black
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AmoledColorScheme,
        typography  = AppTypography,
        content     = content
    )
}