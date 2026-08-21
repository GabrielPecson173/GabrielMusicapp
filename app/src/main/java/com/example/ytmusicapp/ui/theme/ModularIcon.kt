package com.example.ytmusicapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Picks the Modular-appropriate icon glyph: the filled Material icon everywhere else
 * (unchanged), or its thin-stroke Outlined counterpart when Modular is active — reusing
 * Material Icons Extended's existing outlined set rather than drawing new icon assets.
 */
@Composable
fun modularIcon(default: ImageVector, modular: ImageVector): ImageVector =
    if (LocalAppPalette.current.isModular) modular else default
