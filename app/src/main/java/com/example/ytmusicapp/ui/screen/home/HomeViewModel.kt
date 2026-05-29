package com.example.ytmusicapp.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytmusicapp.data.repository.QueueRepository
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.domain.usecase.SearchMusicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeSection(
    val title: String,
    val tracks: List<Track>
)

data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

private val SECTIONS = listOf(
    "Trending Now"  to listOf("trending music 2025", "viral songs 2025", "hot songs this week"),
    "Fresh Drops"   to listOf("new music releases 2025", "best new artists 2025", "new singles this week"),
    "Viral Hits"    to listOf("tiktok songs 2025", "popular hits now", "music hits today"),
    "Late Night"    to listOf("late night vibes music", "chill r&b 2025", "slow songs 2025"),
    "High Energy"   to listOf("gym music 2025", "phonk music 2025", "high bpm workout music"),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchMusicUseCase: SearchMusicUseCase,
    private val queueRepository: QueueRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { loadSections() }

    private fun loadSections() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)
            try {
                val fetched = SECTIONS.map { (title, queries) ->
                    async {
                        val query = queries.random()
                        val tracks = searchMusicUseCase(query).shuffled().take(10)
                        HomeSection(title, tracks)
                    }
                }.awaitAll()
                    .filter { it.tracks.isNotEmpty() }

                _uiState.value = HomeUiState(sections = fetched)

                val allTracks = fetched.flatMap { it.tracks }
                queueRepository.saveTracksToLibrary(allTracks)
            } catch (e: Exception) {
                _uiState.value = HomeUiState(error = e.message)
            }
        }
    }

    fun refresh() = loadSections()
}