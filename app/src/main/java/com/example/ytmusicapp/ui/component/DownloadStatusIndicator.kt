package com.example.ytmusicapp.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytmusicapp.data.db.entity.DownloadEntity
import com.example.ytmusicapp.data.repository.DownloadRepository
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.domain.usecase.DownloadTrackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The four states a track's *permanent, offline-playable* download can be in — distinct
 * from ExoPlayer's transient streaming cache, which is never surfaced here.
 */
enum class DownloadUiStatus { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED }

private fun DownloadEntity?.toUiStatus(): DownloadUiStatus = when (this?.status) {
    DownloadRepository.STATUS_COMPLETED -> DownloadUiStatus.DOWNLOADED
    DownloadRepository.STATUS_QUEUED,
    DownloadRepository.STATUS_DOWNLOADING -> DownloadUiStatus.DOWNLOADING
    DownloadRepository.STATUS_FAILED -> DownloadUiStatus.FAILED
    else -> DownloadUiStatus.NOT_DOWNLOADED // no row, or a cancelled one
}

@HiltViewModel
class DownloadStatusViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val downloadTrackUseCase: DownloadTrackUseCase
) : ViewModel() {

    /** Live status for one track, sourced straight from the downloads table — persists
     *  across restarts and updates immediately as Media3 reports progress. */
    fun statusFlow(trackId: String): Flow<DownloadUiStatus> =
        downloadRepository.getDownloadByTrackId(trackId).map { it.toUiStatus() }

    /** Starts (or retries) a download. No-ops if already downloading/downloaded. */
    fun download(track: Track) {
        viewModelScope.launch {
            val current = downloadRepository.getDownloadByTrackId(track.id).first().toUiStatus()
            if (current == DownloadUiStatus.DOWNLOADING || current == DownloadUiStatus.DOWNLOADED) return@launch
            try { downloadTrackUseCase(track) } catch (_: Exception) { /* status flips to FAILED via the listener */ }
        }
    }

    fun cancel(trackId: String) {
        viewModelScope.launch { downloadRepository.cancel(trackId) }
    }

    fun remove(trackId: String) {
        viewModelScope.launch { downloadRepository.remove(trackId) }
    }
}

/**
 * Subtle, tap-to-manage download status icon for a track: not-downloaded (outline arrow,
 * tap to start), downloading (spinner, tap to cancel), downloaded (check, tap to remove),
 * failed (error mark, tap to retry). Self-contained — reads/writes the real downloads
 * table directly by track id, so it reflects the actual on-disk state, not a stale flag.
 */
@Composable
fun DownloadStatusIndicator(
    track: Track,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    neutralColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    errorColor: Color = MaterialTheme.colorScheme.error,
    viewModel: DownloadStatusViewModel = hiltViewModel()
) {
    val status by remember(track.id) { viewModel.statusFlow(track.id) }
        .collectAsStateWithLifecycle(initialValue = DownloadUiStatus.NOT_DOWNLOADED)
    var showManageMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.size(iconSize + 10.dp),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            DownloadUiStatus.NOT_DOWNLOADED -> Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = neutralColor,
                modifier = Modifier.size(iconSize).clickable { viewModel.download(track) }
            )

            DownloadUiStatus.DOWNLOADING -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = accentColor,
                modifier = Modifier.size(iconSize).clickable { viewModel.cancel(track.id) }
            )

            DownloadUiStatus.DOWNLOADED -> {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = "Downloaded — tap to manage",
                    tint = accentColor,
                    modifier = Modifier.size(iconSize).clickable { showManageMenu = true }
                )
                DropdownMenu(expanded = showManageMenu, onDismissRequest = { showManageMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove download") },
                        onClick = { viewModel.remove(track.id); showManageMenu = false }
                    )
                }
            }

            DownloadUiStatus.FAILED -> Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Download failed — tap to retry",
                tint = errorColor,
                modifier = Modifier.size(iconSize).clickable { viewModel.download(track) }
            )
        }
    }
}
