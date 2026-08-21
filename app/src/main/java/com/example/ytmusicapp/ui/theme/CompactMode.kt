package com.example.ytmusicapp.ui.theme

import androidx.compose.runtime.compositionLocalOf

/** Set once at the app root from the user's Appearance settings; read by list-row components. */
val LocalCompactMode = compositionLocalOf { false }
