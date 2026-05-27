package com.example.ytmusicapp.ui.screen.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytmusicapp.data.db.entity.DownloadEntity
import com.example.ytmusicapp.data.repository.DownloadRepository
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.domain.usecase.DownloadTrackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val active: List<DownloadEntity> = emptyList(),
    val completed: List<DownloadEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadTrackUseCase: DownloadTrackUseCase,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeActive()
        observeCompleted()
    }

    private fun observeActive() {
        viewModelScope.launch {
            downloadRepository.getDownloadsByStatus(
                DownloadRepository.STATUS_DOWNLOADING
            ).collect { active ->
                _uiState.value = _uiState.value.copy(active = active)
            }
        }
    }

    private fun observeCompleted() {
        viewModelScope.launch {
            downloadRepository.getDownloadsByStatus(
                DownloadRepository.STATUS_COMPLETED
            ).collect { completed ->
                _uiState.value = _uiState.value.copy(completed = completed)
            }
        }
    }

    fun download(track: Track) {
        viewModelScope.launch {
            try {
                downloadTrackUseCase(track)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun cancel(trackId: String) {
        viewModelScope.launch {
            try {
                downloadRepository.cancel(trackId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun remove(trackId: String) {
        viewModelScope.launch {
            try {
                downloadRepository.remove(trackId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}