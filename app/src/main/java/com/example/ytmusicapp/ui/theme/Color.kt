package com.example.ytmusicapp.ui.theme

import androidx.compose.ui.graphics.Color

// Primary
val Red80 = Color(0xFFFF6B6B)
val Red40 = Color(0xFFB00020)

// Secondary
val Pink80 = Color(0xFFFFB3B3)
val Pink40 = Color(0xFF8B0000)

// Tertiary
val Orange80 = Color(0xFFFFAB76)
val Orange40 = Color(0xFFE65100)

// Neutral — AMOLED black base, tiny contrast steps
val DarkBackground     = Color(0xFF000000) // true black
val DarkSurface        = Color(0xFF0D0D0D) // barely off-black
val DarkSurfaceVariant = Color(0xFF1A1A1A) // subtle lift for cards/sheets

val LightBackground     = Color(0xFFFFFBFE)
val LightSurface        = Color(0xFFFFFBFE)
val LightSurfaceVariant = Color(0xFFE7E0EC)

// On colors
val OnDark  = Color(0xFFEEEEEE) // slightly off-white — tiny contrast relief
val OnLight = Color(0xFF1C1B1F)

// Modular — shared brand accent across both Modular color modes
val ModularAccent      = Color(0xFFFF7518)
val ModularAccentDark  = Color(0xFFCC5E13)
val ModularOnAccent    = Color(0xFF1A1A1A)

// Modular Light — light gray canvas, dark charcoal panels
val ModularLightBackground     = Color(0xFFD9D9D9)
val ModularLightSurface        = Color(0xFF1A1A1A)
val ModularLightOnSurface      = Color(0xFFF2F2F2)
val ModularLightSubtleText     = Color(0xFF6B6B6B)
val ModularLightBorder         = Color(0xFF9A9A9A)
val ModularLightSurfaceVariant = Color(0xFFCACACA)

// Modular Dark — near-black canvas, barely-lifted charcoal panels (its own design,
// not an inversion of Modular Light — see the dark reference: panel and background
// stay close in value, with orange doing the contrast work instead of light-vs-dark).
val ModularDarkBackground     = Color(0xFF0D0D0D)
val ModularDarkSurface        = Color(0xFF1E1E1E)
val ModularDarkOnSurface      = Color(0xFFF2F2F2)
val ModularDarkSubtleText     = Color(0xFF8A8A8A)
val ModularDarkBorder         = Color(0xFF333333)
val ModularDarkSurfaceVariant = Color(0xFF262626)