package com.example.ytmusicapp.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ytmusicapp.ui.component.TrackListItem
import com.example.ytmusicapp.ui.screen.player.PlayerViewModel

private val LimeGreen       = Color(0xFF84CC16)
private val LimeGreenBright = Color(0xFFBEF264)
private val LimeGreenMid    = Color(0xFF4D7C0F)
private val DarkBg          = Color(0xFF0C0C0C)
private val Surface2        = Color(0xFF1E1E1E)
private val Surface3        = Color(0xFF252525)
private val TextPrimary     = Color(0xFFEFEFEF)
private val TextSecondary   = Color(0xFF888888)

private val GENRE_SECTIONS = mapOf(
    "All"      to null,
    "Trending" to "Trending Now",
    "Phonk"    to "High Energy",
    "Lo-fi"    to "Late Night",
    "Rap"      to "Viral Hits",
    "R&B"      to "Late Night"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedGenre by remember { mutableStateOf("All") }

    val visibleSections = if (selectedGenre == "All") {
        uiState.sections
    } else {
        val targetTitle = GENRE_SECTIONS[selectedGenre]
        uiState.sections.filter { it.title == targetTitle }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // ── Top Bar ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Good evening", fontSize = 12.sp, color = TextSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gabriel ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Pecson", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = LimeGreen)
                }
            }
            Icon(Icons.Default.Notifications, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(LimeGreenMid),
                contentAlignment = Alignment.Center
            ) {
                Text("AK", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LimeGreenBright)
            }
        }

        // ── Search Bar — tappable, navigates to Search tab ──
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
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Artists, songs, podcasts...", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface3)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Filters", color = TextSecondary, fontSize = 12.sp)
            }
        }

        // ── Genre Chips — clickable, filter sections ─────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(GENRE_SECTIONS.keys.toList()) { genre ->
                val selected = genre == selectedGenre
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) LimeGreen else Surface2)
                        .clickable { selectedGenre = genre }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = genre,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.Black else TextSecondary
                    )
                }
            }
        }

        // ── Track List ───────────────────────────────────
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f)
        ) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = LimeGreen)
                }
                uiState.error != null -> Box(Modifier.fillMaxSize()) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    visibleSections.forEach { section ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("See all →", fontSize = 12.sp, color = LimeGreen)
                            }
                        }
                        items(section.tracks) { track ->
                            TrackListItem(
                                track = track,
                                onClick = {
                                    playerViewModel.setTrack(track)
                                    onTrackClick(track.id)
                                },
                                onDownloadClick = {},
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}