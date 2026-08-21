package com.example.ytmusicapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.compositionLocalOf
import com.example.ytmusicapp.data.datastore.AppColorMode
import com.example.ytmusicapp.data.datastore.AppTheme

/**
 * Token set for the screens that render their own surface rather than reading
 * [androidx.compose.material3.MaterialTheme.colorScheme] (Home, the full-screen player, and the
 * player's bottom sheets). Centralizing these here — instead of each screen hardcoding its own
 * private `Color(0x...)` palette — is what lets a theme like Modular reach those screens too.
 */
data class AppPalette(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val subtleText: Color,
    val border: Color,
    val accent: Color,
    val accentAlt: Color,
    val onAccent: Color,
    val isModular: Boolean = false
)

private val ClassicDarkPalette = AppPalette(
    background  = Color(0xFF0E0E0E),
    onBackground = Color(0xFFEEEEEE),
    surface     = Color(0xFF1C1C1C),
    onSurface   = Color(0xFFEEEEEE),
    subtleText  = Color(0xFF666666),
    border      = Color(0xFF333333),
    accent      = Color(0xFFBEF264),
    accentAlt   = Color(0xFF4D7C0F),
    onAccent    = Color(0xFF000000)
)

private val ClassicLightPalette = AppPalette(
    background  = LightBackground,
    onBackground = OnLight,
    surface     = LightSurfaceVariant,
    onSurface   = OnLight,
    subtleText  = Color(0xFF6F6F6F),
    border      = Color(0xFFCAC4D0),
    accent      = Color(0xFF4D7C0F),
    accentAlt   = Color(0xFF84CC16),
    onAccent    = Color(0xFFFFFFFF)
)

private val ModularLightPalette = AppPalette(
    background  = ModularLightBackground,
    onBackground = ModularLightSurface,
    surface     = ModularLightSurface,
    onSurface   = ModularLightOnSurface,
    subtleText  = ModularLightSubtleText,
    border      = ModularLightBorder,
    accent      = ModularAccent,
    accentAlt   = ModularAccentDark,
    onAccent    = ModularOnAccent,
    isModular   = true
)

private val ModularDarkPalette = AppPalette(
    background  = ModularDarkBackground,
    onBackground = ModularDarkOnSurface,
    surface     = ModularDarkSurface,
    onSurface   = ModularDarkOnSurface,
    subtleText  = ModularDarkSubtleText,
    border      = ModularDarkBorder,
    accent      = ModularAccent,
    accentAlt   = ModularAccentDark,
    onAccent    = ModularOnAccent,
    isModular   = true
)

val LocalAppPalette = compositionLocalOf { ClassicDarkPalette }

/**
 * The color an "active" indicator (a playing slider's progress, an enabled toggle icon) should
 * use. Only Modular repaints these accent orange — every other theme keeps its existing
 * onBackground-based look exactly as it was before Modular existed.
 */
val AppPalette.activeIndicatorColor: Color get() = if (isModular) accent else onBackground

fun resolveAppPalette(theme: AppTheme, colorMode: AppColorMode): AppPalette {
    val dark = colorMode == AppColorMode.DARK
    return when {
        theme == AppTheme.MODULAR && dark -> ModularDarkPalette
        theme == AppTheme.MODULAR         -> ModularLightPalette
        dark                              -> ClassicDarkPalette
        else                              -> ClassicLightPalette
    }
}
