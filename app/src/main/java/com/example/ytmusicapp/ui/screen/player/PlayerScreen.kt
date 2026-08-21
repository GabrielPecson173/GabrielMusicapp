package com.example.ytmusicapp.ui.screen.player

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.VolumeDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.example.ytmusicapp.ui.theme.AppPalette
import com.example.ytmusicapp.ui.theme.LocalAppPalette
import com.example.ytmusicapp.ui.theme.activeIndicatorColor
import com.example.ytmusicapp.ui.theme.modularIcon

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun PlayerScreen(
    trackId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel,
    onArtistClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalAppPalette.current

    LaunchedEffect(trackId) { viewModel.loadTrack(trackId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
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
                onShare    = { viewModel.showSheet(PlayerSheet.SHARE) },
                onArtistClick = onArtistClick
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
                    uiState.isLoading  -> CircularProgressIndicator(color = palette.onBackground, strokeWidth = 2.dp)
                    uiState.error != null -> Text(
                        text = uiState.error!!,
                        color = Color(0xFFCF6679),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                    else -> Box(contentAlignment = Alignment.Center) {
                        if (palette.isModular) {
                            ModularVinylRing(size = 300.dp, palette = palette)
                        }
                        VinylPlayerView(
                            albumArtUrl = uiState.track?.thumbnailUrl,
                            isPlaying   = uiState.isPlaying,
                            size        = 272.dp
                        )
                    }
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
    onShare: () -> Unit,
    onArtistClick: (String) -> Unit = {}
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(modularIcon(Icons.Default.KeyboardArrowDown, Icons.Outlined.KeyboardArrowDown), contentDescription = "Back",
                tint = palette.onBackground, modifier = Modifier.size(28.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title ?: "···",
                color = palette.onBackground,
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
                    color = palette.subtleText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { onArtistClick(artist) }
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(modularIcon(Icons.Default.IosShare, Icons.Outlined.IosShare), contentDescription = "Share",
                tint = palette.onBackground, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Modular decorative ring ────────────────────────────────────────────────────
// Thin geometric ring + technical index mark around the vinyl — Modular-only accent,
// per the theme's "circular framing / thin geometric rings" visual language.

@Composable
private fun ModularVinylRing(size: Dp, palette: AppPalette) {
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = 1.5.dp.toPx()
        val radius = this.size.minDimension / 2f
        drawCircle(color = palette.border, radius = radius, style = Stroke(width = strokeWidth))
        drawLine(
            color = palette.accent,
            start = Offset(this.size.width / 2f, 0f),
            end   = Offset(this.size.width / 2f, strokeWidth * 5),
            strokeWidth = strokeWidth * 2
        )
    }
}

// ── Seek row ─────────────────────────────────────────────────────────────────

@Composable
private fun SeekRow(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formatMs(positionMs), color = palette.subtleText, fontSize = 11.sp,
            modifier = Modifier.width(36.dp), textAlign = TextAlign.Start)
        Slider(
            value = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            onValueChange = { fraction -> onSeek((fraction * durationMs).toLong()) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = palette.activeIndicatorColor,
                activeTrackColor = palette.activeIndicatorColor,
                inactiveTrackColor = palette.border
            )
        )
        Text(formatMs(durationMs), color = palette.subtleText, fontSize = 11.sp,
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
    val palette = LocalAppPalette.current
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
                tint = if (isLiked) Color(0xFFE05C7A) else palette.subtleText,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onInfo) {
            Icon(modularIcon(Icons.Default.Info, Icons.Outlined.Info), contentDescription = "Info", tint = palette.subtleText, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(modularIcon(Icons.Default.PlaylistAdd, Icons.Outlined.PlaylistAdd), contentDescription = "Add to playlist", tint = palette.subtleText, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onMore) {
            Icon(modularIcon(Icons.Default.MoreHoriz, Icons.Outlined.MoreHoriz), contentDescription = "More", tint = palette.subtleText, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleRepeat) {
            Icon(
                imageVector = modularIcon(
                    if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    if (repeatMode == RepeatMode.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat
                ),
                contentDescription = "Repeat",
                tint = if (repeatMode != RepeatMode.NONE) palette.activeIndicatorColor else palette.subtleText,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onToggleShuffle) {
            Icon(modularIcon(Icons.Default.Shuffle, Icons.Outlined.Shuffle), contentDescription = "Shuffle",
                tint = if (isShuffle) palette.activeIndicatorColor else palette.subtleText, modifier = Modifier.size(22.dp))
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
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onQueue) {
            Icon(modularIcon(Icons.Default.QueueMusic, Icons.Outlined.QueueMusic), contentDescription = "Queue",
                tint = palette.subtleText, modifier = Modifier.size(22.dp))
        }
        TransportButton(size = 52, onClick = onPrevious) {
            Icon(modularIcon(Icons.Default.SkipPrevious, Icons.Outlined.SkipPrevious), contentDescription = "Previous",
                tint = palette.onBackground, modifier = Modifier.size(24.dp))
        }
        // The center play/pause button is the strongest visual emphasis point in Modular —
        // filled with the accent color, like the reference's primary geometric focal shape.
        TransportButton(size = 62, onClick = onPlayPause, emphasized = true) {
            Icon(
                imageVector = modularIcon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow
                ),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (palette.isModular) palette.onAccent else palette.onBackground,
                modifier = Modifier.size(30.dp)
            )
        }
        TransportButton(size = 52, onClick = onNext) {
            Icon(modularIcon(Icons.Default.SkipNext, Icons.Outlined.SkipNext), contentDescription = "Next",
                tint = palette.onBackground, modifier = Modifier.size(24.dp))
        }
        Icon(modularIcon(Icons.Default.VolumeDown, Icons.Outlined.VolumeDown), contentDescription = "Volume",
            tint = palette.subtleText, modifier = Modifier.size(22.dp))
    }
}

// ── Transport button ──────────────────────────────────────────────────────────

@Composable
private fun TransportButton(
    size: Int,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = LocalAppPalette.current
    val filled = emphasized && palette.isModular
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(palette.accent) else Modifier)
            .border(1.dp, palette.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}