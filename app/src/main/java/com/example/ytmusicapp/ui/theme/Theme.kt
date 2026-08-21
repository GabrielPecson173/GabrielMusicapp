package com.example.ytmusicapp.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.ytmusicapp.data.datastore.AppColorMode
import com.example.ytmusicapp.data.datastore.AppTheme

private val ClassicDarkColorScheme = darkColorScheme(
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

private val ClassicLightColorScheme = lightColorScheme(
    primary          = Red40,
    onPrimary        = OnLight,
    secondary        = Pink40,
    onSecondary      = OnLight,
    tertiary         = Orange40,
    onTertiary       = OnLight,
    background       = LightBackground,
    onBackground     = OnLight,
    surface          = LightSurface,
    onSurface        = OnLight,
    surfaceVariant   = LightSurfaceVariant,
    onSurfaceVariant = OnLight,
)

private val ModularLightColorScheme = lightColorScheme(
    primary          = ModularAccent,
    onPrimary        = ModularOnAccent,
    secondary        = ModularLightBorder,
    onSecondary      = ModularLightOnSurface,
    tertiary         = ModularAccentDark,
    onTertiary       = ModularOnAccent,
    background       = ModularLightBackground,
    onBackground     = ModularLightSurface,
    surface          = ModularLightBackground,
    onSurface        = ModularLightSurface,
    surfaceVariant   = ModularLightSurfaceVariant,
    onSurfaceVariant = ModularLightSubtleText,
)

// A dark scheme, not Light inverted — background and surface stay close in value (per the
// dark reference), with the orange accent doing the contrast work instead of a light canvas.
private val ModularDarkColorScheme = darkColorScheme(
    primary          = ModularAccent,
    onPrimary        = ModularOnAccent,
    secondary        = ModularDarkBorder,
    onSecondary      = ModularDarkOnSurface,
    tertiary         = ModularAccentDark,
    onTertiary       = ModularOnAccent,
    background       = ModularDarkBackground,
    onBackground     = ModularDarkOnSurface,
    surface          = ModularDarkSurface,
    onSurface        = ModularDarkOnSurface,
    surfaceVariant   = ModularDarkSurfaceVariant,
    onSurfaceVariant = ModularDarkSubtleText,
)

@Composable
fun YtMusicAppTheme(
    theme: AppTheme = AppTheme.CLASSIC,
    colorMode: AppColorMode = AppColorMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = colorMode == AppColorMode.DARK
    val context = LocalContext.current
    val colorScheme = when {
        theme == AppTheme.MODULAR && darkTheme -> ModularDarkColorScheme
        theme == AppTheme.MODULAR              -> ModularLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> ClassicDarkColorScheme
        else      -> ClassicLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
