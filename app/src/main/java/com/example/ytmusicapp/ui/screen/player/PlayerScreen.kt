package com.example.ytmusicapp.ui.screen.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi

private val BgDeep      = Color(0xFF0E0E0E)
private val Surface     = Color(0xFF1C1C1C)
private val OnBg        = Color(0xFFEEEEEE)
private val Subtle      = Color(0xFF666666)
private val BorderColor = Color(0xFF333333)

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun PlayerScreen(
    trackId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(trackId) { viewModel.loadTrack(trackId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlayerHeader(
                title      = uiState.track?.title,
                artist     = uiState.track?.artist,
                onBack     = onBack,
                onShare    = { viewModel.showSheet(PlayerSheet.SHARE) }
            )

            SeekRow(
                positionMs = uiState.positionMs,
                durationMs = uiState.durationMs,
                onSeek     = viewModel::onSeek
            )

            // Vinyl area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.isLoading  -> CircularProgressIndicator(color = OnBg, strokeWidth = 2.dp)
                    uiState.error != null -> Text(
                        text = uiState.error!!,
                        color = Color(0xFFCF6679),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                    else -> VinylPlayerView(
                        albumArtUrl = uiState.track?.thumbnailUrl,
                        isPlaying   = uiState.isPlaying,
                        size        = 272.dp
                    )
                }
            }

            ActionRow(
                isLiked           = uiState.isLiked,
                repeatMode        = uiState.repeatMode,
                isShuffle         = uiState.isShuffle,
                onLike            = viewModel::onToggleLike,
                onInfo            = { viewModel.showSheet(PlayerSheet.INFO) },
                onAddToPlaylist   = { viewModel.showSheet(PlayerSheet.MORE) },
                onMore            = { viewModel.showSheet(PlayerSheet.MORE) },
                onToggleRepeat    = viewModel::onToggleRepeat,
                onToggleShuffle   = viewModel::onToggleShuffle
            )

            TransportRow(
                isPlaying   = uiState.isPlaying,
                onPrevious  = viewModel::onSkipPrevious,
                onPlayPause = viewModel::onPlayPause,
                onNext      = viewModel::onSkipNext,
                onQueue     = viewModel::toggleQueueExpanded
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Queue sheet
        if (uiState.isQueueExpanded) {
            QueueSheet(uiState = uiState, viewModel = viewModel)
        }

        // Contextual sheets
        val track = uiState.track
        if (track != null) {
            when (uiState.activeSheet) {
                PlayerSheet.SHARE -> ShareBottomSheet(
                    track           = track,
                    onDismiss       = viewModel::dismissSheet,
                    onGenerateCard  = { viewModel.onGenerateListeningCard() }
                )
                PlayerSheet.INFO  -> InfoBottomSheet(
                    track     = track,
                    onDismiss = viewModel::dismissSheet
                )
                PlayerSheet.MORE  -> MoreBottomSheet(
                    track          = track,
                    uiState        = uiState,
                    onDismiss      = viewModel::dismissSheet,
                    onAddToQueue   = { viewModel.onAddToQueue() },
                    onPlayNext     = { viewModel.onPlayNext() },
                    onSleepTimer   = { viewModel.onSleepTimer() },
                    onSpeedPitch   = { viewModel.onSpeedPitch() },
                    onLyrics       = { viewModel.onLyrics() },
                    onRadioMode    = { viewModel.onRadioMode() },
                    onEqualizer    = { viewModel.onEqualizer() },
                    onOpenYouTube  = { viewModel.onOpenYouTube() }
                )
                PlayerSheet.LYRICS -> LyricsBottomSheet(
                    track     = track,
                    lyrics    = uiState.lyrics,
                    isLoading = uiState.isLyricsLoading,
                    error     = uiState.lyricsError,
                    onDismiss = viewModel::dismissSheet
                )
                PlayerSheet.SLEEP_TIMER -> SleepTimerBottomSheet(
                    isActive           = uiState.sleepTimerEndAtMs != null || uiState.sleepTimerPauseAtTrackEnd,
                    isEndOfTrack       = uiState.sleepTimerPauseAtTrackEnd,
                    endAtMs            = uiState.sleepTimerEndAtMs,
                    onSelectMinutes    = { minutes -> viewModel.startSleepTimer(minutes) },
                    onSelectEndOfTrack = { viewModel.startSleepTimerAtTrackEnd() },
                    onCancel           = { viewModel.cancelSleepTimer() },
                    onDismiss          = viewModel::dismissSheet
                )
                PlayerSheet.SPEED_PITCH -> SpeedPitchBottomSheet(
                    speed         = uiState.playbackSpeed,
                    pitch         = uiState.playbackPitch,
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onPitchChange = { viewModel.setPlaybackPitch(it) },
                    onReset       = { viewModel.resetPlaybackParameters() },
                    onDismiss     = viewModel::dismissSheet
                )
                PlayerSheet.EQUALIZER -> EqualizerBottomSheet(
                    loadState    = { viewModel.getEqualizerState() },
                    onSetBand    = { band, level -> viewModel.setEqualizerBand(band, level) },
                    onSetEnabled = { viewModel.setEqualizerEnabled(it) },
                    onDismiss    = viewModel::dismissSheet
                )
                null -> Unit
            }
        }
    }
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun PlayerHeader(
    title: String?,
    artist: String?,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back",
                tint = OnBg, modifier = Modifier.size(28.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title ?: "···",
                color = OnBg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (!artist.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = artist,
                    color = Subtle,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Default.IosShare, contentDescription = "Share",
                tint = OnBg, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Seek row ─────────────────────────────────────────────────────────────────

@Composable
private fun SeekRow(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formatMs(positionMs), color = Subtle, fontSize = 11.sp,
            modifier = Modifier.width(36.dp), textAlign = TextAlign.Start)
        Slider(
            value = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            onValueChange = { fraction -> onSeek((fraction * durationMs).toLong()) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = OnBg, activeTrackColor = OnBg, inactiveTrackColor = BorderColor)
        )
        Text(formatMs(durationMs), color = Subtle, fontSize = 11.sp,
            modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
    }
}

// ── Action row ───────────────────────────────────────────────────────────────

@Composable
private fun ActionRow(
    isLiked: Boolean,
    repeatMode: RepeatMode,
    isShuffle: Boolean,
    onLike: () -> Unit,
    onInfo: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onMore: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onLike) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Like",
                tint = if (isLiked) Color(0xFFE05C7A) else Subtle,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onInfo) {
            Icon(Icons.Default.Info, contentDescription = "Info", tint = Subtle, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist", tint = Subtle, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "More", tint = Subtle, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleRepeat) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "Repeat",
                tint = if (repeatMode != RepeatMode.NONE) OnBg else Subtle,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onToggleShuffle) {
            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle",
                tint = if (isShuffle) OnBg else Subtle, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Transport row ─────────────────────────────────────────────────────────────

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onQueue) {
            Icon(Icons.Default.QueueMusic, contentDescription = "Queue",
                tint = Subtle, modifier = Modifier.size(22.dp))
        }
        TransportButton(size = 52, onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous",
                tint = OnBg, modifier = Modifier.size(24.dp))
        }
        TransportButton(size = 62, onClick = onPlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = OnBg, modifier = Modifier.size(30.dp)
            )
        }
        TransportButton(size = 52, onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next",
                tint = OnBg, modifier = Modifier.size(24.dp))
        }
        Icon(Icons.Default.VolumeDown, contentDescription = "Volume",
            tint = Subtle, modifier = Modifier.size(22.dp))
    }
}

// ── Transport button ──────────────────────────────────────────────────────────

@Composable
private fun TransportButton(size: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .border(1.dp, BorderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}