package com.example.ytmusicapp.ui.screen.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.ytmusicapp.data.repository.QueueRepository
import com.example.ytmusicapp.domain.model.QueueMode
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.domain.model.StreamInfo
import com.example.ytmusicapp.domain.usecase.GenerateQueueUseCase
import com.example.ytmusicapp.domain.usecase.GetStreamUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RepeatMode { NONE, ONE, ALL }

data class PlayerUiState(
    val track: Track? = null,
    val streamInfo: StreamInfo? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val isQueueExpanded: Boolean = false,
    val queueMode: QueueMode = QueueMode.AUTOPLAY
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val generateQueueUseCase: GenerateQueueUseCase,
    private val queueRepository: QueueRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var mediaController: MediaController? = null
    private var pendingTrackId: String? = null
    private var currentlyPlayingId: String? = null
    // Flag: true while fetchAutoQueue is writing to DB, suppresses observePersistedQueue stomps
    private var isGeneratingQueue = false
    private var hasActiveSession = false

    init {
        startPositionUpdater()
        observePersistedQueue()
    }

    private fun startPositionUpdater() {
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                mediaController?.let { c ->
                    _uiState.value = _uiState.value.copy(positionMs = c.currentPosition)
                }
            }
        }
    }

    private fun observePersistedQueue() {
        viewModelScope.launch {
            queueRepository.observeQueue().collect { tracks ->
                // Only restore from DB on cold start (no active session yet).
                // Once the user taps a track, in-memory state is source of truth.
                if (tracks.isEmpty() || hasActiveSession) return@collect
                val currentIndex = tracks.indexOfFirst { it.id == currentlyPlayingId }
                    .takeIf { it >= 0 } ?: _uiState.value.queueIndex
                _uiState.value = _uiState.value.copy(
                    queue = tracks,
                    queueIndex = currentIndex
                )
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
                if (state == Player.STATE_ENDED) onTrackEnded()
            }
        })
        pendingTrackId?.let { id ->
            pendingTrackId = null
            loadTrack(id)
        }
    }

    fun setTrack(track: Track) {
        hasActiveSession = true
        _uiState.value = _uiState.value.copy(track = track)
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _uiState.value = _uiState.value.copy(
            queue = tracks,
            queueIndex = startIndex
        )
        viewModelScope.launch {
            queueRepository.replaceQueue(tracks, startIndex)
        }
    }

    fun toggleQueueExpanded() {
        _uiState.value = _uiState.value.copy(isQueueExpanded = !_uiState.value.isQueueExpanded)
    }

    fun setQueueMode(mode: QueueMode) {
        _uiState.value = _uiState.value.copy(queueMode = mode)
    }

    fun loadTrack(videoId: String) {
        if (mediaController == null) {
            pendingTrackId = videoId
            return
        }
        if (currentlyPlayingId == videoId) return
        viewModelScope.launch {
            currentlyPlayingId = videoId
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, streamInfo = null)
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
                // track must be set via setTrack() before loadTrack() is called
                if (_uiState.value.track != null && _uiState.value.queue.size <= 3) {
                    fetchAutoQueue()
                }
            } catch (e: Exception) {
                currentlyPlayingId = null
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun fetchAutoQueue() {
        val track = _uiState.value.track ?: return
        val mode = _uiState.value.queueMode
        viewModelScope.launch {
            isGeneratingQueue = true
            try {
                val generated = generateQueueUseCase(
                    seedTrack = track,
                    mode = mode,
                    limit = 20
                )
                val current = _uiState.value.queue
                val merged = (current + generated).distinctBy { it.id }
                _uiState.value = _uiState.value.copy(
                    queue = merged,
                    queueIndex = current.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                )
                queueRepository.replaceQueue(merged)
            } catch (_: Exception) {
                // silently fail
            } finally {
                isGeneratingQueue = false
            }
        }
    }

    fun onPlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
            _uiState.value = _uiState.value.copy(isPlaying = false)
        } else {
            controller.play()
            _uiState.value = _uiState.value.copy(isPlaying = true)
        }
    }

    fun onSeek(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    fun onSkipNext() {
        val state = _uiState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        val nextIndex = if (state.isShuffle) {
            queue.indices.filter { it != state.queueIndex }.randomOrNull() ?: return
        } else {
            if (state.queueIndex + 1 < queue.size) state.queueIndex + 1
            else if (state.repeatMode == RepeatMode.ALL) 0
            else return
        }
        if (nextIndex >= queue.size - 3) fetchAutoQueue()

        val nextTrack = queue[nextIndex]
        currentlyPlayingId = null
        _uiState.value = state.copy(track = nextTrack, queueIndex = nextIndex)
        loadTrack(nextTrack.id)
    }

    fun onSkipPrevious() {
        val state = _uiState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        if (state.positionMs > 3000L) {
            onSeek(0L)
            return
        }
        val prevIndex = if (state.queueIndex - 1 >= 0) state.queueIndex - 1
        else if (state.repeatMode == RepeatMode.ALL) queue.size - 1
        else return
        val prevTrack = queue[prevIndex]
        currentlyPlayingId = null
        _uiState.value = state.copy(track = prevTrack, queueIndex = prevIndex)
        loadTrack(prevTrack.id)
    }

    fun onToggleShuffle() {
        _uiState.value = _uiState.value.copy(isShuffle = !_uiState.value.isShuffle)
    }

    fun onToggleRepeat() {
        val next = when (_uiState.value.repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        _uiState.value = _uiState.value.copy(repeatMode = next)
    }

    private fun onTrackEnded() {
        val state = _uiState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                currentlyPlayingId = null
                loadTrack(state.track?.id ?: return)
            }
            else -> onSkipNext()
        }
    }

    fun playFromQueue(index: Int) {
        val state = _uiState.value
        val track = state.queue.getOrNull(index) ?: return
        currentlyPlayingId = null
        _uiState.value = state.copy(track = track, queueIndex = index)
        loadTrack(track.id)
        viewModelScope.launch {
            queueRepository.setCurrentItem(track.id)
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _uiState.value
        val newQueue = state.queue.toMutableList().also { it.removeAt(index) }
        val newIndex = when {
            index < state.queueIndex -> state.queueIndex - 1
            index == state.queueIndex -> state.queueIndex.coerceAtMost(newQueue.size - 1)
            else -> state.queueIndex
        }
        _uiState.value = state.copy(queue = newQueue, queueIndex = newIndex)
        viewModelScope.launch {
            state.queue.getOrNull(index)?.let {
                queueRepository.removeFromQueue(it.id)
            }
        }
    }

    fun releaseController() {
        mediaController = null
    }
}