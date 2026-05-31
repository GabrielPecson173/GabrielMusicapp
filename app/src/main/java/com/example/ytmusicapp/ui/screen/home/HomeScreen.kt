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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytmusicapp.data.repository.GENRE_DISPLAY_NAMES
import com.example.ytmusicapp.data.repository.HomeSection
import com.example.ytmusicapp.data.tracker.EventTracker
import com.example.ytmusicapp.ui.component.TrackListItem
import com.example.ytmusicapp.ui.screen.player.PlayerViewModel

// ── Palette ──────────────────────────────────────────────────────────────────
private val LimeGreen       = Color(0xFF84CC16)
private val LimeGreenBright = Color(0xFFBEF264)
private val LimeGreenMid    = Color(0xFF4D7C0F)
private val DarkBg          = Color(0xFF0C0C0C)
private val Surface2        = Color(0xFF1E1E1E)
private val Surface3        = Color(0xFF252525)
private val TextPrimary     = Color(0xFFEFEFEF)
private val TextSecondary   = Color(0xFF888888)

// Maps normalized genre key → display label for chips
private fun genreLabel(genre: String): String =
    if (genre == "all") "All" else GENRE_DISPLAY_NAMES[genre] ?: genre.replaceFirstChar { it.uppercase() }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Good evening", fontSize = 12.sp, color = TextSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Gabriel ",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Pecson",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = LimeGreen
                    )
                }
            }
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LimeGreenMid),
                contentAlignment = Alignment.Center
            ) {
                Text("GP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LimeGreenBright)
            }
        }

        // ── Search Bar ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .clickable { onSearchClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Artists, songs, podcasts...",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface3)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Filters", color = TextSecondary, fontSize = 12.sp)
            }
        }

        // ── Genre Chips — dynamic from VM ────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.availableGenres, key = { it }) { genre ->
                val selected = genre == uiState.selectedGenre
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) LimeGreen else Surface2)
                        .clickable { viewModel.selectGenre(genre) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = genreLabel(genre),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.Black else TextSecondary
                    )
                }
            }
        }

        // ── Feed ─────────────────────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f)
        ) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = LimeGreen
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

// ── FeedContent ───────────────────────────────────────────────────────────────
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

    val indexToSectionId = remember(sections) {
        buildMap<Int, String> {
            var idx = 0
            sections.forEach { section ->
                put(idx, section.id)
                idx++
                repeat(section.tracks.size) {
                    put(idx, section.id)
                    idx++
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        sections.forEach { section ->
            item(key = "header_${section.id}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text("See all →", fontSize = 12.sp, color = LimeGreen)
                }
            }

            items(
                items = section.tracks,
                key   = { track -> "${section.id}_${track.id}" }
            ) { track ->
                TrackListItem(
                    track = track,
                    onClick = {
                        eventTracker.logSectionClick(section.id)
                        onSectionClick(section.id)
                        playerViewModel.setTrack(track)
                        playerViewModel.setQueue(listOf(track), 0)
                        onTrackClick(track.id)
                    },
                    onDownloadClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}