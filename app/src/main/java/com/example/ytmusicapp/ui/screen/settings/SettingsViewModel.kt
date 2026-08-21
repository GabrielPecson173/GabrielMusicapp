package com.example.ytmusicapp.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.ytmusicapp.data.datastore.AppColorMode
import com.example.ytmusicapp.data.datastore.AppSettings
import com.example.ytmusicapp.data.datastore.AppTheme
import com.example.ytmusicapp.data.datastore.DefaultRepeatMode
import com.example.ytmusicapp.data.datastore.UserPreferencesDataStore
import com.example.ytmusicapp.data.repository.DownloadRepository
import com.example.ytmusicapp.data.repository.RecommendationRepository
import com.example.ytmusicapp.download.DownloadManagerProvider
import com.example.ytmusicapp.playback.PlaybackCacheProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesDataStore,
    private val downloadManagerProvider: DownloadManagerProvider,
    private val downloadRepository: DownloadRepository,
    private val playbackCacheProvider: PlaybackCacheProvider,
    private val recommendationRepository: RecommendationRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val settings: StateFlow<AppSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(theme: AppTheme) = viewModelScope.launch { prefs.setTheme(theme) }
    fun setColorMode(mode: AppColorMode) = viewModelScope.launch { prefs.setColorMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { prefs.setDynamicColor(enabled) }
    fun setCompactMode(enabled: Boolean) = viewModelScope.launch { prefs.setCompactMode(enabled) }
    fun setRememberPosition(enabled: Boolean) = viewModelScope.launch { prefs.setRememberPosition(enabled) }
    fun setRestorePlayerState(enabled: Boolean) = viewModelScope.launch { prefs.setRestorePlayerState(enabled) }
    fun setAutoplayEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setAutoplayEnabled(enabled) }
    fun setDefaultShuffle(enabled: Boolean) = viewModelScope.launch { prefs.setDefaultShuffle(enabled) }
    fun setDefaultRepeatMode(mode: DefaultRepeatMode) = viewModelScope.launch { prefs.setDefaultRepeatMode(mode) }
    fun setVolumeNormalization(enabled: Boolean) = viewModelScope.launch { prefs.setVolumeNormalization(enabled) }
    fun setLyricsAutoOpen(enabled: Boolean) = viewModelScope.launch { prefs.setLyricsAutoOpen(enabled) }
    fun setDiagnosticsEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setDiagnosticsEnabled(enabled) }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch { prefs.setWifiOnlyDownloads(enabled) }
        downloadManagerProvider.setWifiOnly(enabled)
    }

    fun clearAllDownloads() = viewModelScope.launch { downloadRepository.clearAllDownloads() }

    fun clearListeningHistory() = viewModelScope.launch { recommendationRepository.clearListeningHistory() }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() = viewModelScope.launch(Dispatchers.IO) {
        playbackCacheProvider.clearStreamingCache()
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
    }
}
