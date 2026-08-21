package com.example.ytmusicapp.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytmusicapp.data.repository.ArtistRepository
import com.example.ytmusicapp.data.tracker.EventTracker
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.domain.usecase.SearchMusicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val artistMatches: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchMusicUseCase: SearchMusicUseCase,
    private val artistRepository: ArtistRepository,
    private val eventTracker: EventTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { query -> search(query) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        _queryFlow.value = query
    }

    fun clearQuery() {
        _uiState.value = SearchUiState()
        _queryFlow.value = ""
    }

    fun onTrackClick(track: Track) {
        eventTracker.logSearch(
            query = _uiState.value.query,
            clickedVideoId = track.id
        )
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val results = searchMusicUseCase(query)
                _uiState.value = _uiState.value.copy(
                    results = results,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
        viewModelScope.launch {
            val artists = runCatching { artistRepository.searchArtists(query) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(artistMatches = artists)
        }
    }
}