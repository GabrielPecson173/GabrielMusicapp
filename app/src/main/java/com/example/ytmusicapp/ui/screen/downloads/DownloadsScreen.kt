package com.example.ytmusicapp.ui.screen.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ytmusicapp.data.db.entity.DownloadEntity
import com.example.ytmusicapp.data.repository.DownloadRepository
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.ui.screen.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onTrackClick: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun playDownload(download: DownloadEntity) {
        val track = Track(
            id = download.trackId,
            title = download.title,
            artist = download.artist,
            album = "",
            thumbnailUrl = download.thumbnailUrl,
            durationMs = 0L,
            isDownloaded = true
        )
        playerViewModel.setTrack(track)
        playerViewModel.setQueue(listOf(track), 0)
        onTrackClick(download.trackId)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Downloads") })
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.active.isEmpty() && uiState.completed.isEmpty()) {
                Text(
                    text = "No downloads",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {

                    if (uiState.active.isNotEmpty()) {
                        item {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(uiState.active) { download ->
                            DownloadItem(
                                download = download,
                                onClick = { playDownload(download) },
                                onCancel = { viewModel.cancel(download.trackId) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    }

                    if (uiState.completed.isNotEmpty()) {
                        item {
                            Text(
                                text = "Completed",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(uiState.completed) { download ->
                            DownloadItem(
                                download = download,
                                onClick = { playDownload(download) },
                                onCancel = { viewModel.remove(download.trackId) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(
    download: DownloadEntity,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = download.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = download.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = download.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = download.status,
                style = MaterialTheme.typography.labelSmall,
                color = when (download.status) {
                    DownloadRepository.STATUS_COMPLETED -> MaterialTheme.colorScheme.primary
                    DownloadRepository.STATUS_FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        IconButton(onClick = onCancel) {
            Icon(
                imageVector = if (download.status == DownloadRepository.STATUS_COMPLETED)
                    Icons.Default.Delete
                else
                    Icons.Default.Close,
                contentDescription = "Remove"
            )
        }
    }
}