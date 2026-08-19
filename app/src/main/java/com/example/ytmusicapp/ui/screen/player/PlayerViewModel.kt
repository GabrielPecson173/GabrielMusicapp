package com.example.ytmusicapp.ui.screen.player

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.ytmusicapp.data.repository.DownloadRepository
import com.example.ytmusicapp.data.repository.HomeFeedRepository
import com.example.ytmusicapp.data.repository.LyricsRepository
import com.example.ytmusicapp.data.repository.QueueRepository
import com.example.ytmusicapp.data.repository.RecommendationRepository
import com.example.ytmusicapp.data.tracker.EventTracker
import com.example.ytmusicapp.domain.model.QueueMode
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.domain.model.StreamInfo
import com.example.ytmusicapp.domain.usecase.GenerateQueueUseCase
import com.example.ytmusicapp.domain.usecase.GetStreamUrlUseCase
import com.example.ytmusicapp.playback.AudioPlaybackService
import com.example.ytmusicapp.playback.PlaybackCacheProvider
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class RepeatMode { NONE, ONE, ALL }
enum class PlayerSheet { SHARE, INFO, MORE, LYRICS, SLEEP_TIMER, SPEED_PITCH, EQUALIZER }

data class EqualizerState(
    val enabled: Boolean,
    val numBands: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val centerFreqsHz: List<Int>,
    val levels: List<Int>
)

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
    val queueMode: QueueMode = QueueMode.AUTOPLAY,
    val isLiked: Boolean = false,
    val activeSheet: PlayerSheet? = null,
    val lyrics: String? = null,
    val isLyricsLoading: Boolean = false,
    val lyricsError: String? = null,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val sleepTimerEndAtMs: Long? = null,
    val sleepTimerPauseAtTrackEnd: Boolean = false
)

private const val POSITION_SAVE_INTERVAL_MS = 5_000L
private const val MIN_RESTORE_POSITION_MS   = 10_000L
private const val COMPLETION_THRESHOLD      = 0.90f

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val generateQueueUseCase: GenerateQueueUseCase,
    private val queueRepository: QueueRepository,
    private val recommendationRepository: RecommendationRepository,
    private val homeFeedRepository: HomeFeedRepository,
    private val lyricsRepository: LyricsRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackCacheProvider: PlaybackCacheProvider,
    private val eventTracker: EventTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var mediaController: MediaController? = null
    private var pendingTrackId: String? = null
    private var currentlyPlayingId: String? = null
    private var isGeneratingQueue = false
    private var hasActiveSession = false
    private var skipFromTrackEnd = false
    private var trackStartMs: Long = 0L
    private var positionSaveTicker = 0
    private var hasRestoredPosition = false
    private var lyricsTrackId: String? = null
    private var sleepTimerJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        startPositionUpdater()
        observePersistedQueue()
    }

    fun showSheet(sheet: PlayerSheet)  { _uiState.value = _uiState.value.copy(activeSheet = sheet) }
    fun dismissSheet()                 { _uiState.value = _uiState.value.copy(activeSheet = null) }
    fun toggleQueueExpanded()          { _uiState.value = _uiState.value.copy(isQueueExpanded = !_uiState.value.isQueueExpanded) }
    fun setQueueMode(mode: QueueMode)  { _uiState.value = _uiState.value.copy(queueMode = mode) }
    fun onToggleShuffle()              { _uiState.value = _uiState.value.copy(isShuffle = !_uiState.value.isShuffle) }
    fun onToggleLike()                 { _uiState.value = _uiState.value.copy(isLiked = !_uiState.value.isLiked) }
    fun onGenerateListeningCard()      { /* TODO: render bitmap + share */ }
    fun onSleepTimer()                 { _uiState.value = _uiState.value.copy(activeSheet = PlayerSheet.SLEEP_TIMER) }
    fun onSpeedPitch()                 { _uiState.value = _uiState.value.copy(activeSheet = PlayerSheet.SPEED_PITCH) }
    fun onEqualizer()                  { _uiState.value = _uiState.value.copy(activeSheet = PlayerSheet.EQUALIZER) }
    fun onOpenYouTube()                { /* handled in sheet via context */ }

    fun onLyrics() {
        _uiState.value = _uiState.value.copy(activeSheet = PlayerSheet.LYRICS)
        val track = _uiState.value.track ?: return
        if (lyricsTrackId == track.id) return
        lyricsTrackId = track.id
        _uiState.value = _uiState.value.copy(isLyricsLoading = true, lyrics = null, lyricsError = null)
        viewModelScope.launch {
            try {
                val lyrics = lyricsRepository.getLyrics(
                    title = track.title,
                    artist = track.artist,
                    durationSec = (track.durationMs / 1000L).toInt()
                )
                _uiState.value = _uiState.value.copy(lyrics = lyrics, isLyricsLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    lyricsError = e.message ?: "Lyrics not available",
                    isLyricsLoading = false
                )
            }
        }
    }

    // ── Sleep timer ───────────────────────────────────────────────────────────

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _uiState.value = _uiState.value.copy(sleepTimerEndAtMs = endAt, sleepTimerPauseAtTrackEnd = false)
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            mediaController?.pause()
            _uiState.value = _uiState.value.copy(isPlaying = false, sleepTimerEndAtMs = null)
        }
    }

    fun startSleepTimerAtTrackEnd() {
        sleepTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(sleepTimerEndAtMs = null, sleepTimerPauseAtTrackEnd = true)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(sleepTimerEndAtMs = null, sleepTimerPauseAtTrackEnd = false)
    }

    // ── Speed & pitch ─────────────────────────────────────────────────────────

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.playbackParameters = PlaybackParameters(speed, _uiState.value.playbackPitch)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun setPlaybackPitch(pitch: Float) {
        mediaController?.playbackParameters = PlaybackParameters(_uiState.value.playbackSpeed, pitch)
        _uiState.value = _uiState.value.copy(playbackPitch = pitch)
    }

    fun resetPlaybackParameters() {
        mediaController?.playbackParameters = PlaybackParameters(1f, 1f)
        _uiState.value = _uiState.value.copy(playbackSpeed = 1f, playbackPitch = 1f)
    }

    // ── Equalizer ─────────────────────────────────────────────────────────────

    suspend fun getAudioSessionId(): Int {
        val controller = mediaController ?: return C.AUDIO_SESSION_ID_UNSET
        val result = sendCustomCommand(controller, AudioPlaybackService.CMD_GET_AUDIO_SESSION_ID, Bundle.EMPTY)
            ?: return C.AUDIO_SESSION_ID_UNSET
        return result.extras.getInt(AudioPlaybackService.EXTRA_AUDIO_SESSION_ID, C.AUDIO_SESSION_ID_UNSET)
    }

    suspend fun getEqualizerState(): EqualizerState? {
        val controller = mediaController ?: return null
        val result = sendCustomCommand(controller, AudioPlaybackService.CMD_GET_EQUALIZER_STATE, Bundle.EMPTY)
            ?: return null
        if (result.resultCode != SessionResult.RESULT_SUCCESS) return null
        val extras = result.extras
        val numBands = extras.getInt(AudioPlaybackService.EXTRA_EQ_NUM_BANDS, 0)
        if (numBands <= 0) return null
        return EqualizerState(
            enabled       = extras.getBoolean(AudioPlaybackService.EXTRA_EQ_ENABLED, false),
            numBands      = numBands,
            minLevel      = extras.getInt(AudioPlaybackService.EXTRA_EQ_MIN_LEVEL, -1500),
            maxLevel      = extras.getInt(AudioPlaybackService.EXTRA_EQ_MAX_LEVEL, 1500),
            centerFreqsHz = extras.getIntArray(AudioPlaybackService.EXTRA_EQ_CENTER_FREQS)?.toList() ?: emptyList(),
            levels        = extras.getIntArray(AudioPlaybackService.EXTRA_EQ_LEVELS)?.toList() ?: emptyList()
        )
    }

    fun setEqualizerBand(band: Int, level: Int) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putInt(AudioPlaybackService.EXTRA_EQ_BAND, band)
            putInt(AudioPlaybackService.EXTRA_EQ_LEVEL, level)
        }
        controller.sendCustomCommand(SessionCommand(AudioPlaybackService.CMD_SET_EQUALIZER_BAND, Bundle.EMPTY), args)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        val controller = mediaController ?: return
        val args = Bundle().apply { putBoolean(AudioPlaybackService.EXTRA_EQ_ENABLED, enabled) }
        controller.sendCustomCommand(SessionCommand(AudioPlaybackService.CMD_SET_EQUALIZER_ENABLED, Bundle.EMPTY), args)
    }

    private suspend fun sendCustomCommand(controller: MediaController, action: String, args: Bundle): SessionResult? {
        return suspendCancellableCoroutine { cont ->
            val future = controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
            future.addListener({
                val result = try { future.get() } catch (e: Exception) { null }
                cont.resumeWith(Result.success(result))
            }, MoreExecutors.directExecutor())
        }
    }

    fun onToggleRepeat() {
        val next = when (_uiState.value.repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL  -> RepeatMode.ONE
            RepeatMode.ONE  -> RepeatMode.NONE
        }
        _uiState.value = _uiState.value.copy(repeatMode = next)
        // REPEAT_MODE_ONE is handled natively by ExoPlayer — it loops the buffered/cached
        // media in place with no reload and no network. ALL/queue-advance stays app-level
        // since the queue is a dynamic, recommendation-extended list, not a static playlist.
        mediaController?.repeatMode = if (next == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun onRadioMode() {
        _uiState.value = _uiState.value.copy(queueMode = QueueMode.AUTOPLAY)
        val track = _uiState.value.track ?: return
        viewModelScope.launch {
            try {
                val generated = generateQueueUseCase(seedTrack = track, mode = QueueMode.AUTOPLAY, limit = 30)
                val newQueue = listOf(track) + generated
                _uiState.value = _uiState.value.copy(queue = newQueue, queueIndex = 0)
                queueRepository.replaceQueue(newQueue, 0)
                prefetchNextTrack()
            } catch (_: Exception) {}
        }
    }

    fun onAddToQueue() {
        val state = _uiState.value
        val track = state.track ?: return
        val queue = state.queue + track
        _uiState.value = state.copy(queue = queue)
        viewModelScope.launch { queueRepository.replaceQueue(queue) }
    }

    /** Queues an arbitrary track (not necessarily the one currently playing) to play next. */
    fun queueTrackNext(track: Track) {
        val state = _uiState.value
        if (state.track == null) { setTrack(track); setQueue(listOf(track), 0); loadTrack(track.id); return }
        val queue = state.queue.toMutableList()
        queue.add((state.queueIndex + 1).coerceAtMost(queue.size), track)
        _uiState.value = state.copy(queue = queue)
        viewModelScope.launch { queueRepository.replaceQueue(queue) }
        prefetchNextTrack()
    }

    /** Appends an arbitrary track (not necessarily the one currently playing) to the queue's end. */
    fun queueTrackAtEnd(track: Track) {
        val state = _uiState.value
        if (state.track == null) { setTrack(track); setQueue(listOf(track), 0); loadTrack(track.id); return }
        val queue = state.queue + track
        _uiState.value = state.copy(queue = queue)
        viewModelScope.launch { queueRepository.replaceQueue(queue) }
    }

    /** Starts a fresh radio queue seeded by an arbitrary track and begins playing it. */
    fun startRadioWith(track: Track) {
        setQueueMode(QueueMode.RADIO)
        setTrack(track)
        setQueue(listOf(track), 0)
        currentlyPlayingId = null
        loadTrack(track.id)
    }

    fun onPlayNext() {
        val state   = _uiState.value
        val current = state.track ?: return
        val queue   = state.queue.toMutableList()
        queue.add((state.queueIndex + 1).coerceAtMost(queue.size), current)
        _uiState.value = state.copy(queue = queue)
        viewModelScope.launch { queueRepository.replaceQueue(queue) }
        prefetchNextTrack()
    }

    private fun startPositionUpdater() {
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                val controller = mediaController ?: continue
                val position = controller.currentPosition
                _uiState.value = _uiState.value.copy(positionMs = position)
                positionSaveTicker++
                if (positionSaveTicker >= (POSITION_SAVE_INTERVAL_MS / 500).toInt()) {
                    positionSaveTicker = 0
                    persistPosition(position)
                }
            }
        }
    }

    private fun persistPosition(positionMs: Long) {
        val videoId  = currentlyPlayingId ?: return
        val duration = _uiState.value.durationMs
        if (duration > 0L && positionMs.toFloat() / duration >= COMPLETION_THRESHOLD) return
        viewModelScope.launch { recommendationRepository.savePlaybackPosition(videoId, positionMs) }
    }

    private fun observePersistedQueue() {
        viewModelScope.launch {
            queueRepository.observeQueue().collect { tracks ->
                if (tracks.isEmpty() || hasActiveSession) return@collect
                val currentIndex = tracks.indexOfFirst { it.id == currentlyPlayingId }
                    .takeIf { it >= 0 } ?: _uiState.value.queueIndex
                _uiState.value = _uiState.value.copy(queue = tracks, queueIndex = currentIndex)
            }
        }
    }

    fun setMediaController(controller: MediaController) {
        mediaController = controller
        controller.repeatMode = if (_uiState.value.repeatMode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = controller.playWhenReady)
                if (!isPlaying) persistPosition(controller.currentPosition)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val duration = controller.duration.coerceAtLeast(0L)
                    _uiState.value = _uiState.value.copy(durationMs = duration, isLoading = false, error = null)
                    if (!hasRestoredPosition) { hasRestoredPosition = true; restoreSavedPosition(duration) }
                }
                if (state == Player.STATE_ENDED) onTrackEnded()
            }
            override fun onPlayerError(error: PlaybackException) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Playback failed")
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Native REPEAT_MODE_ONE loop: ExoPlayer restarted the same (already cached)
                // item in place — STATE_ENDED never fires, so record the analytics/sleep-timer
                // side effects that would normally happen there.
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
                onTrackLooped()
            }
        })
        pendingTrackId?.let { id -> pendingTrackId = null; loadTrack(id) }
    }

    private fun onTrackLooped() {
        val state = _uiState.value
        state.track?.let { track ->
            eventTracker.logPlay(
                videoId         = track.id,
                playDurationMs  = state.durationMs,
                totalDurationMs = state.durationMs,
                isCompleted     = true
            )
            viewModelScope.launch { recommendationRepository.savePlaybackPosition(track.id, 0L) }
        }
        trackStartMs = System.currentTimeMillis()
        if (state.sleepTimerPauseAtTrackEnd) {
            _uiState.value = _uiState.value.copy(sleepTimerPauseAtTrackEnd = false, isPlaying = false)
            mediaController?.pause()
        }
    }

    private fun restoreSavedPosition(durationMs: Long) {
        val videoId = currentlyPlayingId ?: return
        viewModelScope.launch {
            val savedMs = recommendationRepository.getPlaybackPosition(videoId)
            if (savedMs < MIN_RESTORE_POSITION_MS) return@launch
            if (durationMs > 0L && savedMs.toFloat() / durationMs >= COMPLETION_THRESHOLD) return@launch
            mediaController?.seekTo(savedMs)
            _uiState.value = _uiState.value.copy(positionMs = savedMs)
        }
    }

    fun setTrack(track: Track) {
        hasActiveSession = true
        _uiState.value = _uiState.value.copy(track = track)
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _uiState.value = _uiState.value.copy(queue = tracks, queueIndex = startIndex)
        viewModelScope.launch { queueRepository.replaceQueue(tracks, startIndex) }
    }

    fun loadTrack(videoId: String) {
        if (mediaController == null) { pendingTrackId = videoId; return }
        if (currentlyPlayingId == videoId) return
        prefetchJob?.cancel()
        playbackCacheProvider.cancelPrefetch()
        viewModelScope.launch {
            currentlyPlayingId = videoId
            trackStartMs = System.currentTimeMillis()
            positionSaveTicker = 0
            hasRestoredPosition = false
            _uiState.value = _uiState.value.copy(
                isLoading = true, error = null, streamInfo = null,
                lyrics = null, lyricsError = null, isLyricsLoading = false
            )

            // Best-effort — resolves a fresh signed URL and an early duration estimate, but a
            // failure here must NOT block playback: if this track's bytes are already cached
            // (fully played before, prefetched, or downloaded), ExoPlayer can play it straight
            // from cache with zero network. Real unplayable errors surface via onPlayerError.
            val streamInfo = try { getStreamUrlUseCase(videoId) } catch (e: Exception) { null }

            try {
                val mediaItem = MediaItem.fromUri("ytmusic://$videoId")
                mediaController?.stop()
                mediaController?.clearMediaItems()
                mediaController?.setMediaItem(mediaItem)
                mediaController?.prepare()
                mediaController?.play()
                _uiState.value = _uiState.value.copy(
                    streamInfo = streamInfo,
                    durationMs = streamInfo?.durationMs ?: 0L,
                    isLoading  = false,
                    isPlaying  = true
                )
                viewModelScope.launch {
                    try { recommendationRepository.fetchAndIngestRelatedTracksBulk(videoId); homeFeedRepository.refresh() }
                    catch (_: Exception) {}
                }
                if (_uiState.value.track != null && _uiState.value.queue.size <= 3) fetchAutoQueue()
                prefetchNextTrack()
            } catch (e: Exception) {
                currentlyPlayingId = null
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    /** Begins background-caching the next queued track while the current one plays. */
    private fun prefetchNextTrack() {
        prefetchJob?.cancel()
        playbackCacheProvider.cancelPrefetch()
        val state = _uiState.value
        val nextTrack = state.queue.getOrNull(state.queueIndex + 1) ?: return
        prefetchJob = viewModelScope.launch {
            try {
                // Already a completed offline download — permanently cached already, nothing to do.
                val download = downloadRepository.getDownloadByTrackId(nextTrack.id).first()
                if (download?.status == DownloadRepository.STATUS_COMPLETED) return@launch
                withContext(Dispatchers.IO) { playbackCacheProvider.prefetchBlocking(nextTrack.id) }
            } catch (_: Exception) {
                // Best-effort only — live playback will resolve/cache normally when this
                // track's turn actually comes.
            }
        }
    }

    private fun fetchAutoQueue() {
        val track = _uiState.value.track ?: return
        val mode  = _uiState.value.queueMode
        viewModelScope.launch {
            isGeneratingQueue = true
            try {
                val generated = generateQueueUseCase(seedTrack = track, mode = mode, limit = 20)
                val current   = _uiState.value.queue
                val merged    = (current + generated).distinctBy { it.id }
                _uiState.value = _uiState.value.copy(
                    queue      = merged,
                    queueIndex = current.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                )
                queueRepository.replaceQueue(merged)
            } catch (_: Exception) {
            } finally { isGeneratingQueue = false }
        }
    }

    fun onPlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) { controller.pause(); _uiState.value = _uiState.value.copy(isPlaying = false) }
        else                      { controller.play();  _uiState.value = _uiState.value.copy(isPlaying = true)  }
    }

    fun onSeek(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    fun onSkipNext() {
        val state = _uiState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        if (!skipFromTrackEnd) {
            state.track?.let { track ->
                persistPosition(state.positionMs)
                eventTracker.logSkip(videoId = track.id, skipPositionMs = state.positionMs)
                eventTracker.logPlay(
                    videoId         = track.id,
                    playDurationMs  = (System.currentTimeMillis() - trackStartMs).coerceAtLeast(0L),
                    totalDurationMs = state.durationMs,
                    isCompleted     = false
                )
            }
        }
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
        if (state.positionMs > 3000L) { onSeek(0L); return }
        val prevIndex = if (state.queueIndex - 1 >= 0) state.queueIndex - 1
        else if (state.repeatMode == RepeatMode.ALL) queue.size - 1
        else return
        val prevTrack = queue[prevIndex]
        currentlyPlayingId = null
        _uiState.value = state.copy(track = prevTrack, queueIndex = prevIndex)
        loadTrack(prevTrack.id)
    }

    private fun onTrackEnded() {
        val state = _uiState.value
        state.track?.let { track ->
            eventTracker.logPlay(
                videoId         = track.id,
                playDurationMs  = state.durationMs,
                totalDurationMs = state.durationMs,
                isCompleted     = true
            )
            viewModelScope.launch { recommendationRepository.savePlaybackPosition(track.id, 0L) }
        }
        if (state.sleepTimerPauseAtTrackEnd) {
            _uiState.value = _uiState.value.copy(sleepTimerPauseAtTrackEnd = false, isPlaying = false)
            mediaController?.pause()
            return
        }
        when (state.repeatMode) {
            RepeatMode.ONE -> { currentlyPlayingId = null; loadTrack(state.track?.id ?: return) }
            else           -> { skipFromTrackEnd = true; onSkipNext(); skipFromTrackEnd = false }
        }
    }

    fun playFromQueue(index: Int) {
        val state = _uiState.value
        val track = state.queue.getOrNull(index) ?: return
        currentlyPlayingId = null
        _uiState.value = state.copy(track = track, queueIndex = index)
        loadTrack(track.id)
        viewModelScope.launch { queueRepository.setCurrentItem(track.id) }
    }

    fun removeFromQueue(index: Int) {
        val state    = _uiState.value
        val newQueue = state.queue.toMutableList().also { it.removeAt(index) }
        val newIndex = when {
            index < state.queueIndex  -> state.queueIndex - 1
            index == state.queueIndex -> state.queueIndex.coerceAtMost(newQueue.size - 1)
            else                      -> state.queueIndex
        }
        _uiState.value = state.copy(queue = newQueue, queueIndex = newIndex)
        viewModelScope.launch {
            state.queue.getOrNull(index)?.let { queueRepository.removeFromQueue(it.id) }
        }
        prefetchNextTrack()
    }

    fun releaseController() { mediaController = null }
}