package com.example.ytmusicapp.ui.screen.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.ui.theme.LocalAppPalette
import com.example.ytmusicapp.ui.theme.activeIndicatorColor
import com.example.ytmusicapp.ui.theme.modularIcon
import kotlinx.coroutines.delay

// ── Queue Sheet ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(uiState: PlayerUiState, viewModel: PlayerViewModel) {
    val palette = LocalAppPalette.current
    ModalBottomSheet(
        onDismissRequest = { viewModel.toggleQueueExpanded() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.onSurface,
        dragHandle = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (uiState.queue.isEmpty()) "Queue" else "Up Next (${uiState.queue.size})",
                color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.toggleQueueExpanded() }) {
                Icon(modularIcon(Icons.Default.Close, Icons.Outlined.Close), contentDescription = "Close", tint = palette.subtleText)
            }
        }
        HorizontalDivider(color = palette.border)

        if (uiState.queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { Text("Loading related songs…", color = palette.subtleText, fontSize = 13.sp) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                itemsIndexed(uiState.queue) { index, track ->
                    val isCurrent = index == uiState.queueIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playFromQueue(index) }
                            .background(if (isCurrent) palette.surface else palette.background)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCurrent) palette.onSurface else palette.onSurface.copy(alpha = 0.75f),
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(track.artist, color = palette.subtleText, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { viewModel.removeFromQueue(index) }) {
                            Icon(modularIcon(Icons.Default.Close, Icons.Outlined.Close), contentDescription = "Remove",
                                tint = palette.subtleText, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = palette.border,
                        modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

// ── Share Sheet ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareBottomSheet(
    track: Track,
    onDismiss: () -> Unit,
    onGenerateCard: () -> Unit
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val youtubeUrl = "https://music.youtube.com/watch?v=${track.id}"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.onSurface,
        dragHandle = null
    ) {
        SheetTitle("Share")
        HorizontalDivider(color = palette.border)
        SheetItem(modularIcon(Icons.Default.Link, Icons.Outlined.Link), "Copy YouTube Music link") {
            copyToClipboard(context, "YouTube Music link", youtubeUrl); onDismiss()
        }
        SheetItem(modularIcon(Icons.Default.ContentCopy, Icons.Outlined.ContentCopy), "Copy \"${track.title} — ${track.artist}\"") {
            copyToClipboard(context, "Song", "${track.title} — ${track.artist}"); onDismiss()
        }
        SheetItem(modularIcon(Icons.Default.OpenInNew, Icons.Outlined.OpenInNew), "Open in YouTube Music") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))); onDismiss()
        }
        SheetItem(modularIcon(Icons.Default.Share, Icons.Outlined.Share), "Share link via…") {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, youtubeUrl)
            }
            context.startActivity(Intent.createChooser(intent, null)); onDismiss()
        }
        SheetItem(modularIcon(Icons.Default.MusicNote, Icons.Outlined.MusicNote), "Generate Listening Card") {
            onGenerateCard(); onDismiss()
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Info Sheet ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfoBottomSheet(track: Track, onDismiss: () -> Unit) {
    val palette = LocalAppPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.onSurface,
        dragHandle = null
    ) {
        SheetTitle("Song Info")
        HorizontalDivider(color = palette.border)
        InfoRow("Title",    track.title)
        InfoRow("Artist",   track.artist)
        InfoRow("Album",    track.album.ifBlank { "—" })
        InfoRow("Duration", formatMs(track.durationMs))
        InfoRow("Video ID", track.id)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── More Sheet ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreBottomSheet(
    track: Track,
    uiState: PlayerUiState,
    onDismiss: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onSleepTimer: () -> Unit,
    onSpeedPitch: () -> Unit,
    onLyrics: () -> Unit,
    onRadioMode: () -> Unit,
    onEqualizer: () -> Unit,
    onOpenYouTube: () -> Unit
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val youtubeUrl = "https://music.youtube.com/watch?v=${track.id}"

    val sleepTimerSubtitle = when {
        uiState.sleepTimerPauseAtTrackEnd -> "Off at end of track"
        uiState.sleepTimerEndAtMs != null -> "Off in ${formatMs((uiState.sleepTimerEndAtMs - System.currentTimeMillis()).coerceAtLeast(0L))}"
        else -> null
    }
    val speedPitchSubtitle = if (uiState.playbackSpeed != 1f || uiState.playbackPitch != 1f) {
        "Speed ${"%.2f".format(uiState.playbackSpeed)}x · Pitch ${"%.2f".format(uiState.playbackPitch)}x"
    } else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.onSurface,
        dragHandle = null
    ) {
        SheetTitle("More Options")
        HorizontalDivider(color = palette.border)

        SheetSectionLabel("Playback")
        SheetItem(modularIcon(Icons.Default.MusicNote, Icons.Outlined.MusicNote), "Lyrics") { onLyrics() }
        SheetItem(modularIcon(Icons.Default.Timer, Icons.Outlined.Timer), "Sleep Timer", subtitle = sleepTimerSubtitle) { onSleepTimer() }
        SheetItem(modularIcon(Icons.Default.Speed, Icons.Outlined.Speed), "Speed & Pitch", subtitle = speedPitchSubtitle) { onSpeedPitch() }
        SheetItem(modularIcon(Icons.Default.Equalizer, Icons.Outlined.Equalizer), "Equalizer") { onEqualizer() }

        HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 4.dp))
        SheetSectionLabel("Discovery")
        SheetItem(modularIcon(Icons.Default.Radio, Icons.Outlined.Radio), "Song Radio") { onRadioMode(); onDismiss() }

        HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 4.dp))
        SheetSectionLabel("Organization")
        SheetItem(modularIcon(Icons.Default.PlaylistAdd, Icons.Outlined.PlaylistAdd), "Add to Queue") { onAddToQueue(); onDismiss() }
        SheetItem(modularIcon(Icons.Default.PlaylistAdd, Icons.Outlined.PlaylistAdd), "Play Next")    { onPlayNext(); onDismiss() }

        HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 4.dp))
        SheetSectionLabel("Utility")
        SheetItem(modularIcon(Icons.Default.OpenInNew, Icons.Outlined.OpenInNew), "Open in YouTube Music") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))); onDismiss()
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Lyrics Sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsBottomSheet(
    track: Track,
    lyrics: String?,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.onSurface,
        dragHandle = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lyrics", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(track.title, color = palette.subtleText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDismiss) {
                Icon(modularIcon(Icons.Default.Close, Icons.Outlined.Close), contentDescription = "Close", tint = palette.subtleText)
            }
        }
        HorizontalDivider(color = palette.border)

        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 460.dp).padding(20.dp),
            contentAlignment = if (isLoading || error != null) Alignment.Center else Alignment.TopStart
        ) {
            when {
                isLoading    -> CircularProgressIndicator(color = palette.onSurface, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                error != null -> Text(error, color = palette.subtleText, fontSize = 13.sp, textAlign = TextAlign.Center)
                lyrics != null -> Text(
                    text = lyrics,
                    color = palette.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── Sleep Timer Sheet ────────────────────────────────────────────────────────

private val SLEEP_TIMER_OPTIONS = listOf(5, 10, 15, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerBottomSheet(
    isActive: Boolean,
    isEndOfTrack: Boolean,
    endAtMs: Long?,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.onSurface,
        dragHandle = null
    ) {
        SheetTitle("Sleep Timer")
        HorizontalDivider(color = palette.border)

        if (isActive) {
            var remaining by remember(endAtMs) {
                mutableLongStateOf((endAtMs?.minus(System.currentTimeMillis()))?.coerceAtLeast(0L) ?: 0L)
            }
            LaunchedEffect(endAtMs) {
                if (endAtMs == null) return@LaunchedEffect
                while (remaining > 0L) {
                    delay(1000)
                    remaining = (endAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = if (isEndOfTrack) "Playback will pause at the end of this track" else "Playback will pause in ${formatMs(remaining)}",
                    color = palette.subtleText, fontSize = 13.sp
                )
            }
            SheetItem(modularIcon(Icons.Default.Close, Icons.Outlined.Close), "Turn Off Timer") { onCancel(); onDismiss() }
        } else {
            SLEEP_TIMER_OPTIONS.forEach { minutes ->
                SheetItem(modularIcon(Icons.Default.Timer, Icons.Outlined.Timer), "$minutes minutes") { onSelectMinutes(minutes); onDismiss() }
            }
            SheetItem(modularIcon(Icons.Default.Timer, Icons.Outlined.Timer), "End of track") { onSelectEndOfTrack(); onDismiss() }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── Speed & Pitch Sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedPitchBottomSheet(
    speed: Float,
    pitch: Float,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.onSurface,
        dragHandle = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Speed & Pitch", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("Reset", color = palette.onSurface, fontSize = 13.sp) }
        }
        HorizontalDivider(color = palette.border)

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Speed", color = palette.subtleText, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = palette.activeIndicatorColor,
                        activeTrackColor = palette.activeIndicatorColor,
                        inactiveTrackColor = palette.border
                    )
                )
                Text("%.2fx".format(speed), color = palette.onSurface, fontSize = 13.sp, modifier = Modifier.width(52.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Pitch", color = palette.subtleText, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = pitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.5f..2f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = palette.activeIndicatorColor,
                        activeTrackColor = palette.activeIndicatorColor,
                        inactiveTrackColor = palette.border
                    )
                )
                Text("%.2fx".format(pitch), color = palette.onSurface, fontSize = 13.sp, modifier = Modifier.width(52.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Equalizer Sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerBottomSheet(
    loadState: suspend () -> EqualizerState?,
    onSetBand: (band: Int, level: Int) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    var isLoading by remember { mutableStateOf(true) }
    var state by remember { mutableStateOf<EqualizerState?>(null) }
    var levels by remember { mutableStateOf<List<Int>>(emptyList()) }
    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = loadState()
        state = loaded
        levels = loaded?.levels ?: emptyList()
        enabled = loaded?.enabled ?: false
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.onSurface,
        dragHandle = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Equalizer", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (state != null) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; onSetEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.onSurface,
                        checkedTrackColor = if (palette.isModular) palette.accent else palette.subtleText
                    )
                )
            }
        }
        HorizontalDivider(color = palette.border)

        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(20.dp),
            contentAlignment = if (isLoading || state == null) Alignment.Center else Alignment.TopStart
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = palette.onSurface, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                state == null -> Text(
                    "Equalizer not available. Start playing a track first.",
                    color = palette.subtleText, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                else -> {
                    val eq = state!!
                    Column(modifier = Modifier.fillMaxWidth()) {
                        eq.centerFreqsHz.forEachIndexed { index, freqHz ->
                            val level = levels.getOrElse(index) { 0 }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    formatFreqLabel(freqHz), color = palette.subtleText, fontSize = 11.sp,
                                    modifier = Modifier.width(48.dp)
                                )
                                Slider(
                                    value = level.toFloat(),
                                    onValueChange = { v ->
                                        levels = levels.toMutableList().also { it[index] = v.toInt() }
                                        onSetBand(index, v.toInt())
                                    },
                                    valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat(),
                                    enabled = enabled,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.activeIndicatorColor,
                                        activeTrackColor = palette.activeIndicatorColor,
                                        inactiveTrackColor = palette.border
                                    )
                                )
                                Text(
                                    "%.1fdB".format(level / 100f), color = palette.onSurface, fontSize = 12.sp,
                                    modifier = Modifier.width(56.dp), textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatFreqLabel(hz: Int): String =
    if (hz >= 1000) "%.1fkHz".format(hz / 1000f) else "${hz}Hz"

// ── Shared primitives ─────────────────────────────────────────────────────────

@Composable
private fun SheetTitle(text: String) {
    val palette = LocalAppPalette.current
    Text(
        text = text,
        color = palette.onSurface,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
    )
}

@Composable
private fun SheetSectionLabel(text: String) {
    val palette = LocalAppPalette.current
    Text(
        text = text,
        color = palette.subtleText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, subtitle: String? = null, onClick: () -> Unit) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.onSurface, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, color = palette.onSurface, fontSize = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = palette.subtleText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalAppPalette.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(label, color = palette.subtleText, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = palette.onSurface, fontSize = 14.sp)
    }
    HorizontalDivider(color = palette.border, modifier = Modifier.padding(horizontal = 20.dp))
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
