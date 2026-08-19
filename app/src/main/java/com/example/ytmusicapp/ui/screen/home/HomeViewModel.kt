package com.example.ytmusicapp.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytmusicapp.data.repository.HomeFeedRepository
import com.example.ytmusicapp.data.repository.HomeSection
import com.example.ytmusicapp.data.repository.RecommendationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val fullFeedSections: List<HomeSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedGenre: String = "all",
    val availableGenres: List<String> = listOf("all")
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeFeedRepository: HomeFeedRepository,
    private val recommendationRepository: RecommendationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeFeed()
        loadAvailableGenres()
    }

    // ── Feed ──────────────────────────────────────────────────────────────────

    private fun observeFeed() {
        viewModelScope.launch {
            homeFeedRepository.feedFlow()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { sections ->
                    _uiState.update { state ->
                        val visibleSections = if (state.selectedGenre == "all") sections else state.sections
                        state.copy(
                            fullFeedSections = sections,
                            sections = visibleSections,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, selectedGenre = "all") }
            homeFeedRepository.refresh()
            loadAvailableGenres()
        }
    }

    // ── Genre chips ───────────────────────────────────────────────────────────

    private fun loadAvailableGenres() {
        viewModelScope.launch {
            val genres = recommendationRepository.getAvailableGenres()
            _uiState.update { it.copy(availableGenres = listOf("all") + genres) }
        }
    }

    fun selectGenre(genre: String) {
        if (genre == "all") {
            _uiState.update { state ->
                state.copy(
                    selectedGenre = "all",
                    sections = state.fullFeedSections,
                    isLoading = false,
                    error = null
                )
            }
            return
        }

        _uiState.update { it.copy(selectedGenre = genre, isLoading = true, error = null) }
        viewModelScope.launch {
            val shelf = homeFeedRepository.buildGenreShelf(genre)
            _uiState.update { state ->
                state.copy(
                    sections  = listOfNotNull(shelf),
                    isLoading = false,
                    error     = if (shelf == null) "No $genre tracks found yet" else null
                )
            }
        }
    }

    // ── Impression / click telemetry ──────────────────────────────────────────

    fun onSectionImpression(sectionId: String) {
        viewModelScope.launch { homeFeedRepository.logImpression(sectionId) }
    }

    fun onSectionClick(sectionId: String) {
        viewModelScope.launch { homeFeedRepository.logSectionClick(sectionId) }
    }
}