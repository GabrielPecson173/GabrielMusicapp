package com.example.ytmusicapp.ui.screen.library

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytmusicapp.data.db.dao.PlayEventDao
import com.example.ytmusicapp.data.repository.RecommendationRepository
import com.example.ytmusicapp.domain.engine.TrackScoringEngine
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.domain.usecase.GetLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MS_7D                 = 604_800_000L
private const val SMART_SHUFFLE_LIMIT   = 25
private const val SMART_SHUFFLE_MIN_SCORE = 0.40f

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val smartShuffleTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getLibraryUseCase: GetLibraryUseCase,
    private val recommendationRepository: RecommendationRepository,
    private val playEventDao: PlayEventDao,
    private val scoringEngine: TrackScoringEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        val offline = !isNetworkAvailable()
        _uiState.update { it.copy(isOffline = offline) }
        observeLibrary()
        buildSmartShuffle()
    }

    // ── Library ───────────────────────────────────────────────────────────────

    private fun observeLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                getLibraryUseCase().collect { tracks ->
                    _uiState.update { it.copy(tracks = tracks, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Smart Shuffle ─────────────────────────────────────────────────────────
    // Rule: downloaded + high score + not played in last 7 days, order by score DESC

    private fun buildSmartShuffle() {
        viewModelScope.launch {
            try {
                val nowMs        = System.currentTimeMillis()
                val cutoff7d     = nowMs - MS_7D
                val recentIds    = playEventDao.getRecent(1000)
                    .filter { it.timestamp >= cutoff7d }
                    .map { it.videoId }
                    .toSet()

                val candidates   = recommendationRepository.getCandidates()
                val recentPlays  = playEventDao.getRecent(500)
                val artistMap    = candidates.associate { it.videoId to it.artistId }
                val playsByVideo  = recentPlays.groupBy { it.videoId }
                val playsByArtist = recentPlays.groupBy { artistMap[it.videoId] ?: "" }

                val smartTracks = candidates
                    .filter { it.isDownloaded && it.videoId !in recentIds }
                    .map { candidate ->
                        val ctx = TrackScoringEngine.ScoringContext(
                            candidate          = candidate,
                            playEvents         = playsByVideo[candidate.videoId] ?: emptyList(),
                            skipEvents         = emptyList(),
                            artistPlayEvents   = playsByArtist[candidate.artistId] ?: emptyList(),
                            artistSkipEvents   = emptyList(),
                            recentSearchTokens = emptyList(),
                            nowMs              = nowMs
                        )
                        candidate to scoringEngine.score(ctx)
                    }
                    .filter { (_, score) -> score >= SMART_SHUFFLE_MIN_SCORE }
                    .sortedByDescending { (_, score) -> score }
                    .take(SMART_SHUFFLE_LIMIT)
                    .map { (candidate, _) ->
                        Track(
                            id           = candidate.videoId,
                            title        = candidate.title,
                            artist       = candidate.artistName,
                            album        = candidate.albumName,
                            thumbnailUrl = "",
                            durationMs   = candidate.durationMs,
                            isDownloaded = true
                        )
                    }

                _uiState.update { it.copy(smartShuffleTracks = smartTracks) }
            } catch (_: Exception) {
                // Smart Shuffle is best-effort — silent fail
            }
        }
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun refresh() {
        val offline = !isNetworkAvailable()
        _uiState.update { it.copy(isOffline = offline) }
        observeLibrary()
        buildSmartShuffle()
    }
}