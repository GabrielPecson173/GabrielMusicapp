package com.example.ytmusicapp.ui.component

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ytmusicapp.domain.model.Track

private val SheetBg         = Color(0xFF1C1C1C)
private val SheetFg         = Color(0xFFEEEEEE)
private val SheetSubtle     = Color(0xFF666666)
private val SheetBorder     = Color(0xFF333333)
private val LimeGreenBright = Color(0xFFBEF264)

private data class DownloadRowSpec(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val onClick: () -> Unit
)

/**
 * Reusable "..." context menu for a track in a list — start radio, play next, add to
 * queue, download, share, details. Same visual language as the player screen's sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    track: Track,
    onDismiss: () -> Unit,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    downloadViewModel: DownloadStatusViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showDetails by remember { mutableStateOf(false) }
    val downloadStatus by remember(track.id) { downloadViewModel.statusFlow(track.id) }
        .collectAsStateWithLifecycle(initialValue = DownloadUiStatus.NOT_DOWNLOADED)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        contentColor = SheetFg,
        dragHandle = null
    ) {
        if (showDetails) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showDetails = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = SheetFg)
                }
                Text("Details", color = SheetFg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            HorizontalDivider(color = SheetBorder)
            InfoRow("Title", track.title)
            InfoRow("Artist", track.artist)
            InfoRow("Album", track.album.ifBlank { "—" })
            InfoRow("Duration", formatDuration(track.durationMs))
            InfoRow("Video ID", track.id)
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title, color = SheetFg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist, color = SheetSubtle, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = SheetSubtle)
                }
            }
            HorizontalDivider(color = SheetBorder)

            SheetItem(Icons.Default.Radio, "Start Radio") { onStartRadio(); onDismiss() }
            SheetItem(Icons.Default.PlaylistPlay, "Play Next") { onPlayNext(); onDismiss() }
            SheetItem(Icons.Default.PlaylistAdd, "Add to Queue") { onAddToQueue(); onDismiss() }

            HorizontalDivider(color = SheetBorder, modifier = Modifier.padding(vertical = 4.dp))

            val (downloadIcon, downloadLabel, downloadTint, downloadAction) = when (downloadStatus) {
                DownloadUiStatus.NOT_DOWNLOADED -> DownloadRowSpec(
                    Icons.Default.Download, "Download", SheetFg
                ) { downloadViewModel.download(track) }
                DownloadUiStatus.DOWNLOADING -> DownloadRowSpec(
                    Icons.Default.Download, "Downloading… (tap to cancel)", LimeGreenBright
                ) { downloadViewModel.cancel(track.id) }
                DownloadUiStatus.DOWNLOADED -> DownloadRowSpec(
                    Icons.Default.DownloadDone, "Downloaded (tap to remove)", LimeGreenBright
                ) { downloadViewModel.remove(track.id) }
                DownloadUiStatus.FAILED -> DownloadRowSpec(
                    Icons.Default.ErrorOutline, "Download failed — tap to retry", Color(0xFFE57373)
                ) { downloadViewModel.download(track) }
            }
            SheetItem(downloadIcon, downloadLabel, downloadTint) { downloadAction(); onDismiss() }
            SheetItem(Icons.Default.Share, "Share") {
                val youtubeUrl = "https://music.youtube.com/watch?v=${track.id}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, youtubeUrl)
                }
                context.startActivity(Intent.createChooser(intent, null))
                onDismiss()
            }
            SheetItem(Icons.Default.Info, "Details") { showDetails = true }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(label, color = SheetSubtle, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = SheetFg, fontSize = 14.sp)
    }
    HorizontalDivider(color = SheetBorder, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, tint: Color = SheetFg, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 14.sp)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
