package com.example.ytmusicapp.ui.screen.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.ytmusicapp.domain.model.StreamInfo
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.domain.usecase.GetStreamUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val track: Track? = null,
    val streamInfo: StreamInfo? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val getStreamUrlUseCase: GetStreamUrlUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var mediaController: MediaController? = null

    init { startPositionUpdater() }

    private fun startPositionUpdater() {
        viewModelScope.launch {
            while (true) {
                delay(500)
                mediaController?.let { c ->
                    _uiState.value = _uiState.value.copy(positionMs = c.currentPosition)
                }
            }
        }
    }

    fun setMediaController(controller: MediaController) {
        mediaController = controller
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _uiState.value = _uiState.value.copy(
                        durationMs = controller.duration.coerceAtLeast(0L),
                        isLoading = false
                    )
                }
            }
        })
    }

    fun releaseController() {
        mediaController?.release()
        mediaController = null
    }

    // Call this BEFORE navigating to PlayerScreen
    fun setTrack(track: Track) {
        _uiState.value = _uiState.value.copy(track = track)
    }

    fun loadTrack(videoId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val streamInfo = getStreamUrlUseCase(videoId)
                val mediaItem = MediaItem.fromUri(streamInfo.streamUrl)
                mediaController?.stop()
                mediaController?.clearMediaItems()
                mediaController?.setMediaItem(mediaItem)
                mediaController?.prepare()
                mediaController?.play()
                _uiState.value = _uiState.value.copy(
                    streamInfo = streamInfo,
                    durationMs = streamInfo.durationMs,
                    isLoading = false,
                    isPlaying = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onPlayPause() {
        if (mediaController?.isPlaying == true) {
            mediaController?.pause()
            _uiState.value = _uiState.value.copy(isPlaying = false)
        } else {
            mediaController?.play()
            _uiState.value = _uiState.value.copy(isPlaying = true)
        }
    }

    fun onSeek(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    fun onStop() {
        mediaController?.stop()
        _uiState.value = _uiState.value.copy(isPlaying = false, positionMs = 0L)
    }

    override fun onCleared() {
        releaseController()
        super.onCleared()
    }
}