package com.example.ytmusicapp.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytmusicapp.data.repository.HomeFeedRepository
import com.example.ytmusicapp.data.repository.HomeSection
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
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeFeedRepository: HomeFeedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { observeFeed() }

    private fun observeFeed() {
        viewModelScope.launch {
            homeFeedRepository.feedFlow()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { sections ->
                    _uiState.value = HomeUiState(sections = sections, isLoading = false)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            homeFeedRepository.refresh()
        }
    }

    fun onSectionImpression(sectionId: String) {
        viewModelScope.launch {
            homeFeedRepository.logImpression(sectionId)
        }
    }

    fun onSectionClick(sectionId: String) {
        viewModelScope.launch {
            homeFeedRepository.logSectionClick(sectionId)
        }
    }
}