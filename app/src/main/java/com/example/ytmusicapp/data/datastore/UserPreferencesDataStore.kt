package com.example.ytmusicapp.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The visual design system. Independent of [AppColorMode] — adding a future theme means
 *  adding one more entry here, never a new axis alongside light/dark. */
enum class AppTheme { CLASSIC, MODULAR }

/** Light/dark appearance, independent of which [AppTheme] is selected. */
enum class AppColorMode { LIGHT, DARK }

/** Starting shuffle/repeat state for a freshly started listening session. */
enum class DefaultRepeatMode { OFF, ALL, ONE }

data class AppSettings(
    // Appearance
    val theme: AppTheme = AppTheme.CLASSIC,
    val colorMode: AppColorMode = AppColorMode.DARK,
    val dynamicColor: Boolean = false,
    val compactMode: Boolean = false,
    // Playback & state continuity
    val rememberPosition: Boolean = true,
    val restorePlayerState: Boolean = true,
    val autoplayEnabled: Boolean = true,
    val defaultShuffle: Boolean = false,
    val defaultRepeatMode: DefaultRepeatMode = DefaultRepeatMode.OFF,
    // Audio
    val volumeNormalization: Boolean = true,
    // Downloads
    val wifiOnlyDownloads: Boolean = false,
    // Lyrics
    val lyricsAutoOpen: Boolean = false,
    // Privacy
    val diagnosticsEnabled: Boolean = true
)

/** Centralized app preferences, backed by the app's existing [DataStore] (see AppModule). */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val THEME                = stringPreferencesKey("theme")
        val COLOR_MODE            = stringPreferencesKey("color_mode")
        val LEGACY_THEME_MODE     = stringPreferencesKey("theme_mode") // pre-split single selector
        val DYNAMIC_COLOR        = booleanPreferencesKey("dynamic_color")
        val COMPACT_MODE         = booleanPreferencesKey("compact_mode")
        val REMEMBER_POSITION    = booleanPreferencesKey("remember_position")
        val RESTORE_PLAYER_STATE = booleanPreferencesKey("restore_player_state")
        val AUTOPLAY_ENABLED     = booleanPreferencesKey("autoplay_enabled")
        val DEFAULT_SHUFFLE      = booleanPreferencesKey("default_shuffle")
        val DEFAULT_REPEAT_MODE  = stringPreferencesKey("default_repeat_mode")
        val VOLUME_NORMALIZATION = booleanPreferencesKey("volume_normalization")
        val WIFI_ONLY_DOWNLOADS  = booleanPreferencesKey("wifi_only_downloads")
        val LYRICS_AUTO_OPEN     = booleanPreferencesKey("lyrics_auto_open")
        val DIAGNOSTICS_ENABLED  = booleanPreferencesKey("diagnostics_enabled")
        val FOLLOWED_ARTISTS     = stringSetPreferencesKey("followed_artists")
    }

    val followedArtists: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.FOLLOWED_ARTISTS] ?: emptySet()
    }

    suspend fun toggleFollowArtist(artistKey: String) = dataStore.edit { prefs ->
        val current = prefs[Keys.FOLLOWED_ARTISTS] ?: emptySet()
        prefs[Keys.FOLLOWED_ARTISTS] = if (artistKey in current) current - artistKey else current + artistKey
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val (legacyTheme, legacyColorMode) = legacyThemeMode(prefs[Keys.LEGACY_THEME_MODE])
        AppSettings(
            theme = prefs[Keys.THEME]
                ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: legacyTheme,
            colorMode = prefs[Keys.COLOR_MODE]
                ?.let { runCatching { AppColorMode.valueOf(it) }.getOrNull() } ?: legacyColorMode,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            compactMode = prefs[Keys.COMPACT_MODE] ?: false,
            rememberPosition = prefs[Keys.REMEMBER_POSITION] ?: true,
            restorePlayerState = prefs[Keys.RESTORE_PLAYER_STATE] ?: true,
            autoplayEnabled = prefs[Keys.AUTOPLAY_ENABLED] ?: true,
            defaultShuffle = prefs[Keys.DEFAULT_SHUFFLE] ?: false,
            defaultRepeatMode = prefs[Keys.DEFAULT_REPEAT_MODE]
                ?.let { runCatching { DefaultRepeatMode.valueOf(it) }.getOrNull() } ?: DefaultRepeatMode.OFF,
            volumeNormalization = prefs[Keys.VOLUME_NORMALIZATION] ?: true,
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY_DOWNLOADS] ?: false,
            lyricsAutoOpen = prefs[Keys.LYRICS_AUTO_OPEN] ?: false,
            diagnosticsEnabled = prefs[Keys.DIAGNOSTICS_ENABLED] ?: true
        )
    }

    /** Maps the old single Theme selector (System/Light/Dark/Modular) onto the new
     *  Theme × Appearance pair, so anyone who picked Modular before the split keeps the
     *  same look instead of silently reverting to Classic. */
    private fun legacyThemeMode(stored: String?): Pair<AppTheme, AppColorMode> = when (stored) {
        "LIGHT"    -> AppTheme.CLASSIC to AppColorMode.LIGHT
        "MODULAR"  -> AppTheme.MODULAR to AppColorMode.LIGHT
        else       -> AppTheme.CLASSIC to AppColorMode.DARK // DARK, SYSTEM, or never set
    }

    suspend fun setTheme(theme: AppTheme) = dataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setColorMode(mode: AppColorMode) = dataStore.edit { it[Keys.COLOR_MODE] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setCompactMode(enabled: Boolean) = dataStore.edit { it[Keys.COMPACT_MODE] = enabled }
    suspend fun setRememberPosition(enabled: Boolean) = dataStore.edit { it[Keys.REMEMBER_POSITION] = enabled }
    suspend fun setRestorePlayerState(enabled: Boolean) = dataStore.edit { it[Keys.RESTORE_PLAYER_STATE] = enabled }
    suspend fun setAutoplayEnabled(enabled: Boolean) = dataStore.edit { it[Keys.AUTOPLAY_ENABLED] = enabled }
    suspend fun setDefaultShuffle(enabled: Boolean) = dataStore.edit { it[Keys.DEFAULT_SHUFFLE] = enabled }
    suspend fun setDefaultRepeatMode(mode: DefaultRepeatMode) = dataStore.edit { it[Keys.DEFAULT_REPEAT_MODE] = mode.name }
    suspend fun setVolumeNormalization(enabled: Boolean) = dataStore.edit { it[Keys.VOLUME_NORMALIZATION] = enabled }
    suspend fun setWifiOnlyDownloads(enabled: Boolean) = dataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled }
    suspend fun setLyricsAutoOpen(enabled: Boolean) = dataStore.edit { it[Keys.LYRICS_AUTO_OPEN] = enabled }
    suspend fun setDiagnosticsEnabled(enabled: Boolean) = dataStore.edit { it[Keys.DIAGNOSTICS_ENABLED] = enabled }
}
