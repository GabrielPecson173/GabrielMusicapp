package com.example.ytmusicapp.ui.screen.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytmusicapp.data.datastore.UserPreferencesDataStore
import com.example.ytmusicapp.data.repository.ArtistRepository
import com.example.ytmusicapp.data.util.ArtistNameUtils
import com.example.ytmusicapp.domain.model.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistUiState(
    val artist: Artist? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isFollowing: Boolean = false,
    val albumDebugInfo: String? = null
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    private var loadedKey: String? = null

    fun load(artistKey: String) {
        val normalizedKey = ArtistNameUtils.normalize(artistKey)
        if (loadedKey == normalizedKey) return
        loadedKey = normalizedKey

        _uiState.update { it.copy(isLoading = true, error = null, artist = null) }
        viewModelScope.launch {
            try {
                artistRepository.observeArtistCatalog(artistKey).collect { artist ->
                    _uiState.update {
                        it.copy(
                            artist = artist,
                            isLoading = false,
                            albumDebugInfo = artistRepository.getLastAlbumDebugInfo(artistKey)
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
        viewModelScope.launch {
            userPreferencesDataStore.followedArtists.collect { followed ->
                _uiState.update { it.copy(isFollowing = normalizedKey in followed) }
            }
        }
    }

    fun toggleFollow() {
        val key = loadedKey ?: return
        viewModelScope.launch { userPreferencesDataStore.toggleFollowArtist(key) }
    }
}
