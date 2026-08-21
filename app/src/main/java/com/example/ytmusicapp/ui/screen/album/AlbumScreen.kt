package com.example.ytmusicapp.ui.screen.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ytmusicapp.domain.model.Album
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.ui.component.TrackListItem
import com.example.ytmusicapp.ui.screen.player.PlayerViewModel
import com.example.ytmusicapp.ui.theme.LocalAppPalette
import com.example.ytmusicapp.ui.theme.modularIcon

@Composable
fun AlbumScreen(
    artistKey: String,
    albumName: String,
    onBack: () -> Unit,
    onTrackClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalAppPalette.current

    LaunchedEffect(artistKey, albumName) { viewModel.load(artistKey, albumName) }

    fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        playerViewModel.setQueue(tracks, startIndex)
        playerViewModel.setTrack(tracks[startIndex])
        onTrackClick(tracks[startIndex].id)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        when {
            uiState.isLoading -> CircularProgressIndicator(
                color = palette.onBackground,
                modifier = Modifier.align(Alignment.Center)
            )
            uiState.error != null -> Text(
                text = uiState.error ?: "Album not found",
                color = palette.subtleText,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
            uiState.album != null -> {
                val album = uiState.album!!
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        AlbumHero(album = album, onBack = onBack, onArtistClick = onArtistClick)
                    }
                    item {
                        AlbumControlsRow(
                            trackCount = album.tracks.size,
                            onPlay = { playTracks(album.tracks, 0) },
                            onShuffle = { playTracks(album.tracks.shuffled(), 0) }
                        )
                    }
                    itemsIndexed(album.tracks, key = { i, t -> "${t.id}_$i" }) { index, track ->
                        TrackListItem(
                            track = track,
                            index = index + 1,
                            isActive = playerState.track?.id == track.id,
                            onClick = { playTracks(album.tracks, index) },
                            onArtistClick = onArtistClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AlbumHero(album: Album, onBack: () -> Unit, onArtistClick: (String) -> Unit) {
    val palette = LocalAppPalette.current
    Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
        AsyncImage(
            model = album.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.25f),
                        1.0f to palette.background
                    )
                )
        )
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) {
            Icon(
                modularIcon(Icons.Default.KeyboardArrowLeft, Icons.Outlined.KeyboardArrowLeft),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = album.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(album.artist)
                    album.year?.let { append(" • $it") }
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onArtistClick(album.artist) }
            )
        }
    }
}

@Composable
private fun AlbumControlsRow(trackCount: Int, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(palette.accent)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modularIcon(Icons.Default.PlayArrow, Icons.Outlined.PlayArrow),
                contentDescription = "Play",
                tint = palette.onAccent,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = onShuffle) {
            Icon(
                modularIcon(Icons.Default.Shuffle, Icons.Outlined.Shuffle),
                contentDescription = "Shuffle",
                tint = palette.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "$trackCount tracks", color = palette.subtleText, fontSize = 13.sp)
    }
}
