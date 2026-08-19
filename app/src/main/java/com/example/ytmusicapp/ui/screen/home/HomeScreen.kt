package com.example.ytmusicapp.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ytmusicapp.data.repository.GENRE_DISPLAY_NAMES
import com.example.ytmusicapp.data.repository.HomeSection
import com.example.ytmusicapp.data.tracker.EventTracker
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.ui.component.DownloadStatusIndicator
import com.example.ytmusicapp.ui.component.TrackOptionsBottomSheet
import com.example.ytmusicapp.ui.screen.player.PlayerViewModel

private val LimeGreen       = Color(0xFF84CC16)
private val LimeGreenBright = Color(0xFFBEF264)
private val LimeGreenMid    = Color(0xFF4D7C0F)
private val DarkBg          = Color(0xFF0B0A0D)
private val Surface2        = Color(0xFF1E1E1E)
private val TextPrimary     = Color(0xFFF2F2F2)
private val TextSecondary   = Color(0xFF9A9A9A)

private val GlowViolet = Color(0xFF7C3AED)
private val GlowPink   = Color(0xFFEC4899)
private val GlowAmber  = Color(0xFFF59E0B)

private fun genreLabel(genre: String): String =
    if (genre == "all") "All" else GENRE_DISPLAY_NAMES[genre] ?: genre.replaceFirstChar { it.uppercase() }

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    eventTracker: EventTracker,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Ambient glow blobs — purely decorative, sit behind all content.
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-60).dp)
                .background(Brush.radialGradient(listOf(GlowViolet.copy(alpha = 0.35f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-40).dp)
                .background(Brush.radialGradient(listOf(GlowPink.copy(alpha = 0.22f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 140.dp)
                .background(Brush.radialGradient(listOf(GlowAmber.copy(alpha = 0.16f), Color.Transparent)))
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(onSearchClick = onSearchClick)

            Text(
                text = "Hi, Gabriel",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.availableGenres, key = { it }) { genre ->
                    val selected = genre == uiState.selectedGenre
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) LimeGreenBright else Surface2)
                            .clickable { viewModel.selectGenre(genre) }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = genreLabel(genre),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.Black else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f)
            ) {
                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = LimeGreenBright
                        )
                    }

                    uiState.error != null -> Box(Modifier.fillMaxSize()) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }

                    else -> FeedContent(
                        sections = uiState.sections,
                        eventTracker = eventTracker,
                        playerViewModel = playerViewModel,
                        onTrackClick = onTrackClick,
                        onSectionImpression = viewModel::onSectionImpression,
                        onSectionClick = viewModel::onSectionClick
                    )
                }
            }
        }
    }
}

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onSearchClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(LimeGreenMid, LimeGreen))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.Black, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        CircleIconButton(icon = Icons.Default.Search, contentDescription = "Search", onClick = onSearchClick)
        Spacer(modifier = Modifier.width(10.dp))
        CircleIconButton(icon = Icons.Default.FavoriteBorder, contentDescription = "Favorites", onClick = {})
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}

// ── Feed ─────────────────────────────────────────────────────────────────────

@Composable
private fun FeedContent(
    sections: List<HomeSection>,
    eventTracker: EventTracker,
    playerViewModel: PlayerViewModel,
    onTrackClick: (String) -> Unit,
    onSectionImpression: (String) -> Unit,
    onSectionClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var optionsTrack by remember { mutableStateOf<Track?>(null) }

    // First section renders as one big horizontal carousel item; the rest render as
    // one row-item per track — the index map below mirrors that exactly.
    val indexToSectionId = remember(sections) {
        buildMap<Int, String> {
            var idx = 0
            sections.forEachIndexed { sectionIndex, section ->
                put(idx, section.id); idx++
                if (sectionIndex == 0) {
                    put(idx, section.id); idx++
                } else {
                    repeat(section.tracks.size) {
                        put(idx, section.id); idx++
                    }
                }
            }
        }
    }

    val impressedSections = remember { mutableSetOf<String>() }

    val visibleSectionIds by remember {
        derivedStateOf {
            val layoutInfo    = listState.layoutInfo
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd   = layoutInfo.viewportEndOffset

            layoutInfo.visibleItemsInfo
                .filter { item ->
                    val itemStart = item.offset
                    val itemEnd   = item.offset + item.size
                    val visible   = minOf(itemEnd, viewportEnd) - maxOf(itemStart, viewportStart)
                    visible.toFloat() / item.size.toFloat() >= 0.50f
                }
                .mapNotNull { item -> indexToSectionId[item.index] }
                .toSet()
        }
    }

    LaunchedEffect(visibleSectionIds) {
        visibleSectionIds.forEach { sectionId ->
            if (impressedSections.add(sectionId)) {
                eventTracker.logImpression(sectionId)
                onSectionImpression(sectionId)
            }
        }
    }

    fun playTrack(section: HomeSection, track: Track) {
        eventTracker.logSectionClick(section.id)
        onSectionClick(section.id)
        playerViewModel.setTrack(track)
        playerViewModel.setQueue(listOf(track), 0)
        onTrackClick(track.id)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        sections.forEachIndexed { sectionIndex, section ->
            item(key = "header_${section.id}") {
                SectionHeader(title = section.title, showSeeAll = sectionIndex != 0)
            }

            if (sectionIndex == 0) {
                item(key = "carousel_${section.id}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(section.tracks, key = { "${section.id}_carousel_${it.id}" }) { track ->
                            CuratedCard(
                                track = track,
                                onClick = { playTrack(section, track) },
                                onMoreClick = { optionsTrack = track }
                            )
                        }
                    }
                }
            } else {
                items(
                    items = section.tracks,
                    key   = { track -> "${section.id}_${track.id}" }
                ) { track ->
                    PlaylistRow(
                        track = track,
                        onClick = { playTrack(section, track) },
                        onMoreClick = { optionsTrack = track }
                    )
                }
            }
        }
    }

    optionsTrack?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            onDismiss = { optionsTrack = null },
            onStartRadio = { playerViewModel.startRadioWith(track); onTrackClick(track.id) },
            onPlayNext = { playerViewModel.queueTrackNext(track) },
            onAddToQueue = { playerViewModel.queueTrackAtEnd(track) }
        )
    }
}

@Composable
private fun SectionHeader(title: String, showSeeAll: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        if (showSeeAll) Text("See all", color = TextSecondary, fontSize = 13.sp)
    }
}

// ── Curated carousel card ────────────────────────────────────────────────────

@Composable
private fun CuratedCard(track: Track, onClick: () -> Unit, onMoreClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(270.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A2E5C), Color(0xFF6B4FA0))))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(
                track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                track.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(LimeGreenBright)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(16.dp))
                DownloadStatusIndicator(
                    track = track,
                    iconSize = 19.dp,
                    accentColor = LimeGreenBright,
                    neutralColor = Color.White,
                    errorColor = Color(0xFFFF6B6B)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    Icons.Default.MoreHoriz, contentDescription = "More options", tint = Color.White,
                    modifier = Modifier.size(19.dp).clickable(onClick = onMoreClick)
                )
            }
        }
    }
}

// ── Playlist-style row ───────────────────────────────────────────────────────

@Composable
private fun PlaylistRow(track: Track, onClick: () -> Unit, onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "By ${track.artist} • ${formatDuration(track.durationMs)}",
                color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        DownloadStatusIndicator(
            track = track,
            accentColor = LimeGreenBright,
            neutralColor = TextSecondary,
            errorColor = Color(0xFFFF6B6B)
        )
        IconButton(onClick = onMoreClick) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = TextSecondary)
        }
    }
}
