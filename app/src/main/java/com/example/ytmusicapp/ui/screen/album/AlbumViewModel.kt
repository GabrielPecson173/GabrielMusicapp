package com.example.ytmusicapp.ui.screen.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytmusicapp.data.repository.ArtistRepository
import com.example.ytmusicapp.domain.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumUiState(
    val album: Album? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val artistRepository: ArtistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    private var loadedFor: Pair<String, String>? = null

    fun load(artistKey: String, albumName: String) {
        val target = artistKey to albumName
        if (loadedFor == target) return
        loadedFor = target

        _uiState.update { it.copy(isLoading = true, error = null, album = null) }
        viewModelScope.launch {
            try {
                val album = artistRepository.getAlbumDetail(artistKey, albumName)
                _uiState.update {
                    if (album != null) it.copy(album = album, isLoading = false)
                    else it.copy(isLoading = false, error = "Album not found")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
