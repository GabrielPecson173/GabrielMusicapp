package com.example.ytmusicapp.ui.screen.artist

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ytmusicapp.domain.model.Artist
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.ui.component.AlbumCard
import com.example.ytmusicapp.ui.component.TrackListItem
import com.example.ytmusicapp.ui.screen.player.PlayerViewModel
import com.example.ytmusicapp.ui.theme.LocalAppPalette
import com.example.ytmusicapp.ui.theme.modularIcon

private val HERO_HEIGHT = 420.dp

@Composable
fun ArtistScreen(
    artistKey: String,
    onBack: () -> Unit,
    onTrackClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onExpandSection: (String) -> Unit = {},
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        ArtistHero(artist = artist, onBack = onBack)
                    }
                    item {
                        ArtistControlsRow(
                            artist = artist,
                            isFollowing = uiState.isFollowing,
                            onPlay = { playTracks(artist.allSongs, 0) },
                            onShuffle = { playTracks(artist.allSongs.shuffled(), 0) },
                            onToggleFollow = viewModel::toggleFollow,
                            onRadio = { playerViewModel.startArtistRadio(artistKey, artist.name) }
                        )
                    }

                    if (artist.popular.isNotEmpty()) {
                        item {
                            PagedSongSection(
                                title = "Popular",
                                tracks = artist.popular,
                                activeTrackId = playerState.track?.id,
                                onExpand = { onExpandSection(ArtistSectionType.POPULAR) },
                                onTrackClick = ::playTracks,
                                onArtistClick = onArtistClick
                            )
                        }
                    }

                    if (artist.albums.isNotEmpty()) {
                        item { SectionHeader("Albums") }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(artist.albums, key = { it.id }) { album ->
                                    AlbumCard(
                                        album = album,
                                        onClick = { onAlbumClick(artistKey, album.title) }
                                    )
                                }
                            }
                        }
                    }
                    if (!uiState.albumDebugInfo.isNullOrBlank()) {
                        // TEMPORARY diagnostic — remove once the live album lookup is confirmed
                        // working. Always shown (not just when Albums is empty) so a *partial*
                        // result — some albums missing while others show — is diagnosable too,
                        // without needing adb/logcat access.
                        item {
                            Text(
                                text = "Albums debug: ${uiState.albumDebugInfo}",
                                color = palette.subtleText,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (artist.singles.isNotEmpty()) {
                        item {
                            PagedSongSection(
                                title = "Singles",
                                tracks = artist.singles,
                                activeTrackId = playerState.track?.id,
                                onExpand = { onExpandSection(ArtistSectionType.SINGLES) },
                                onTrackClick = ::playTracks,
                                onArtistClick = onArtistClick
                            )
                        }
                    }

                    if (artist.allSongs.isNotEmpty()) {
                        item {
                            PagedSongSection(
                                title = "All Songs",
                                tracks = artist.allSongs,
                                activeTrackId = playerState.track?.id,
                                onExpand = { onExpandSection(ArtistSectionType.ALL) },
                                onTrackClick = ::playTracks,
                                onArtistClick = onArtistClick
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "No songs found for this artist yet.",
                                color = palette.subtleText,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
        }
    }
}

// ── Hero ─────────────────────────────────────────────────────────────────────

@Composable
private fun ArtistHero(artist: Artist, onBack: () -> Unit) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
    ) {
        AsyncImage(
            model = artist.thumbnailUrl,
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    modularIcon(Icons.Default.KeyboardArrowLeft, Icons.Outlined.KeyboardArrowLeft),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        modularIcon(Icons.Default.MoreVert, Icons.Outlined.MoreVert),
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Share artist") },
                        onClick = {
                            showMenu = false
                            val query = Uri.encode(artist.name)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/search?q=$query")
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = artist.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (artist.popular.isNotEmpty()) "Best Hits" else "${artist.songCount} songs",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp
            )
        }
    }
}

// ── Controls row ─────────────────────────────────────────────────────────────

@Composable
private fun ArtistControlsRow(
    artist: Artist,
    isFollowing: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFollow: () -> Unit,
    onRadio: () -> Unit
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
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
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onRadio) {
            Icon(
                modularIcon(Icons.Default.Radio, Icons.Outlined.Radio),
                contentDescription = "Artist Radio",
                tint = palette.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onToggleFollow) {
            Icon(
                imageVector = if (isFollowing)
                    modularIcon(Icons.Default.Favorite, Icons.Outlined.Favorite)
                else
                    modularIcon(Icons.Default.FavoriteBorder, Icons.Outlined.FavoriteBorder),
                contentDescription = if (isFollowing) "Unfollow" else "Follow",
                tint = if (isFollowing) Color(0xFFE05C7A) else palette.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${artist.songCount} songs",
            color = palette.subtleText,
            fontSize = 13.sp
        )
    }
}

// ── Section header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, onExpand: (() -> Unit)? = null) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = palette.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        if (onExpand != null) {
            Text(
                text = "Expand",
                color = palette.subtleText,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onExpand).padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
    }
}

// ── Paged song section (Popular / Singles / All Songs) ───────────────────────
// 5 rows per page via HorizontalPager, so the main Artist Page never grows into one long
// vertical list — "Expand" is the escape hatch to a full vertical list of everything.

private const val SONGS_PER_PAGE = 5
private val PAGE_ROW_HEIGHT = 72.dp

@Composable
private fun PagedSongSection(
    title: String,
    tracks: List<Track>,
    activeTrackId: String?,
    onExpand: () -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val pages = remember(tracks) { tracks.chunked(SONGS_PER_PAGE) }

    Column {
        SectionHeader(title = title, onExpand = onExpand)

        if (pages.size <= 1) {
            Column {
                pages.firstOrNull().orEmpty().forEachIndexed { index, track ->
                    TrackListItem(
                        track = track,
                        index = index + 1,
                        isActive = track.id == activeTrackId,
                        onClick = { onTrackClick(tracks, index) },
                        onArtistClick = onArtistClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { pages.size })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PAGE_ROW_HEIGHT * SONGS_PER_PAGE)
            ) { pageIndex ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    pages[pageIndex].forEachIndexed { rowIndex, track ->
                        val absoluteIndex = pageIndex * SONGS_PER_PAGE + rowIndex
                        TrackListItem(
                            track = track,
                            index = absoluteIndex + 1,
                            isActive = track.id == activeTrackId,
                            onClick = { onTrackClick(tracks, absoluteIndex) },
                            onArtistClick = onArtistClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            PageIndicator(pageCount = pages.size, currentPage = pagerState.currentPage)
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (page == currentPage) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (page == currentPage) palette.accent else palette.border)
            )
        }
    }
}
