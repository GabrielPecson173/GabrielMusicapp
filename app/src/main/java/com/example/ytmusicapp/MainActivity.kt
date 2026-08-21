package com.example.ytmusicapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import com.example.ytmusicapp.data.datastore.AppSettings
import com.example.ytmusicapp.data.datastore.UserPreferencesDataStore
import com.example.ytmusicapp.ui.navigation.AppNavGraph
import com.example.ytmusicapp.ui.theme.LocalAppPalette
import com.example.ytmusicapp.ui.theme.LocalCompactMode
import com.example.ytmusicapp.ui.theme.YtMusicAppTheme
import com.example.ytmusicapp.ui.theme.resolveAppPalette
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPreferencesDataStore: UserPreferencesDataStore

    @ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by userPreferencesDataStore.settings.collectAsState(initial = AppSettings())
            YtMusicAppTheme(theme = settings.theme, colorMode = settings.colorMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(
                    LocalCompactMode provides settings.compactMode,
                    LocalAppPalette provides resolveAppPalette(settings.theme, settings.colorMode)
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
