package com.example.ytmusicapp.ui.screen.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytmusicapp.domain.model.Artist
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.ui.component.TrackListItem
import com.example.ytmusicapp.ui.screen.player.PlayerViewModel
import com.example.ytmusicapp.ui.theme.LocalAppPalette
import com.example.ytmusicapp.ui.theme.modularIcon

/** Section keys used by [Screen.ArtistSection] — kept as plain strings since the route just
 *  needs a serializable value, not a shared enum touching unrelated code. */
object ArtistSectionType {
    const val POPULAR = "popular"
    const val SINGLES = "singles"
    const val ALL = "all"
}

private fun sectionTitle(section: String): String = when (section) {
    ArtistSectionType.POPULAR -> "Popular"
    ArtistSectionType.SINGLES -> "Singles"
    else -> "All Songs"
}

private fun songsForSection(artist: Artist, section: String): List<Track> =
    when (section) {
        ArtistSectionType.POPULAR -> artist.popularityRanked
        ArtistSectionType.SINGLES -> artist.singles
        else -> artist.allSongs
    }

@Composable
fun ArtistSectionScreen(
    artistKey: String,
    section: String,
    onBack: () -> Unit,
    onTrackClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalAppPalette.current

    LaunchedEffect(artistKey) { viewModel.load(artistKey) }

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
                text = uiState.error ?: "Something went wrong",
                color = palette.subtleText,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
            uiState.artist != null -> {
                val artist = uiState.artist!!
                val tracks = songsForSection(artist, section)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    modularIcon(Icons.Default.KeyboardArrowLeft, Icons.Outlined.KeyboardArrowLeft),
                                    contentDescription = "Back",
                                    tint = palette.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = sectionTitle(section),
                                    color = palette.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = artist.name,
                                    color = palette.subtleText,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    if (tracks.isEmpty()) {
                        item {
                            Text(
                                text = "Nothing here yet.",
                                color = palette.subtleText,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        itemsIndexed(tracks, key = { i, t -> "${t.id}_$i" }) { index, track ->
                            TrackListItem(
                                track = track,
                                index = index + 1,
                                isActive = playerState.track?.id == track.id,
                                onClick = { playTracks(tracks, index) },
                                onArtistClick = onArtistClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
        }
    }
}
