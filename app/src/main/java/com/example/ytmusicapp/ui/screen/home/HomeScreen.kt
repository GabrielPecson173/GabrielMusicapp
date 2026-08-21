package com.example.ytmusicapp.ui.screen.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.ytmusicapp.ui.theme.AppPalette
import com.example.ytmusicapp.ui.theme.LocalAppPalette
import com.example.ytmusicapp.ui.theme.modularIcon
import kotlin.math.cos
import kotlin.math.sin

private val GlowViolet = Color(0xFF7C3AED)
private val GlowPink   = Color(0xFFEC4899)
private val GlowAmber  = Color(0xFFF59E0B)

private fun genreLabel(genre: String): String =
    if (genre == "all") "All" else GENRE_DISPLAY_NAMES[genre] ?: genre.replaceFirstChar { it.uppercase() }

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

// ── Modular decoration ──────────────────────────────────────────────────────
// A loose recreation of the reference's abstract mark — a ringed circle with a broken accent
// arc and a dot grid. Three distinct variants so a handful of these placed down the page read
// as a small design system, not one shape copy-pasted repeatedly.
//
// Deliberately confined to standalone spots (the header, and occasional section headers) rather
// than stitched together across every song row into one continuous canvas — that approach was
// tried and kept breaking: motifs overlapped into a muddy mess, and keeping them seamlessly
// aligned across independently-scrolling list items turned out to fight against itself no
// matter how the spacing/opacity/clipping were tuned. A handful of clean, well-placed shapes is
// the version that actually worked.

@Composable
private fun ModularAbstractShape(
    palette: AppPalette,
    modifier: Modifier = Modifier,
    circleBias: Offset = Offset(0.74f, 0.42f),
    variant: Int = 0
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val thin  = 1.5.dp.toPx()
        val thick = 3.dp.toPx()
        val radius = minOf(w, h) * 0.56f
        val center = Offset(w * circleBias.x, h * circleBias.y)

        // A soft radial glow behind the ring gives it a sense of floating above the page
        // rather than being drawn flat onto it — a cheap but effective depth cue.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.accent.copy(alpha = 0.10f), Color.Transparent),
                center = center, radius = radius * 1.6f
            ),
            radius = radius * 1.6f, center = center
        )

        when (variant % 3) {
            0 -> {
                // Full composition: disc + two rings + two arcs + dot grid + two lines.
                drawCircle(color = palette.surface.copy(alpha = 0.22f), radius = radius, center = center)
                drawCircle(color = palette.border.copy(alpha = 0.6f), radius = radius * 1.12f, center = center, style = Stroke(width = thin))
                drawCircle(color = palette.border.copy(alpha = 0.35f), radius = radius * 0.7f, center = center, style = Stroke(width = thin))
                drawArc(
                    color = palette.accent.copy(alpha = 0.85f),
                    startAngle = -70f, sweepAngle = 120f, useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = thick, cap = StrokeCap.Round)
                )
                drawArc(
                    color = palette.accent.copy(alpha = 0.45f),
                    startAngle = 140f, sweepAngle = 50f, useCenter = false,
                    topLeft = Offset(center.x - radius * 1.12f, center.y - radius * 1.12f),
                    size = Size(radius * 2.24f, radius * 2.24f),
                    style = Stroke(width = thin, cap = StrokeCap.Round)
                )
                val dotSpacing = 12.dp.toPx()
                val dotRadius  = 1.4.dp.toPx()
                val gridOrigin = Offset(w * 0.06f, h * 0.12f)
                for (row in 0 until 5) for (col in 0 until 5) {
                    drawCircle(
                        color = palette.border.copy(alpha = 0.5f), radius = dotRadius,
                        center = Offset(gridOrigin.x + col * dotSpacing, gridOrigin.y + row * dotSpacing)
                    )
                }
                drawLine(
                    color = palette.accent.copy(alpha = 0.55f),
                    start = Offset(w * 0.02f, h * 0.72f), end = Offset(w * 0.14f, h * 0.58f),
                    strokeWidth = thin, cap = StrokeCap.Round
                )
                drawLine(
                    color = palette.border.copy(alpha = 0.5f),
                    start = Offset(center.x - radius * 1.3f, center.y + radius * 0.2f),
                    end   = Offset(center.x - radius * 1.05f, center.y + radius * 0.45f),
                    strokeWidth = thin
                )
            }
            1 -> {
                // Arc-forward: a single ring, one bold arc, and clock-face tick marks.
                val r = radius * 0.8f
                drawCircle(color = palette.border.copy(alpha = 0.55f), radius = r, center = center, style = Stroke(width = thin))
                drawArc(
                    color = palette.accent.copy(alpha = 0.8f),
                    startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = thick, cap = StrokeCap.Round)
                )
                repeat(6) { i ->
                    val angle = Math.toRadians((i * 60).toDouble())
                    val inner = r * 1.15f
                    val outer = r * 1.32f
                    drawLine(
                        color = palette.border.copy(alpha = 0.45f),
                        start = Offset(center.x + (inner * cos(angle)).toFloat(), center.y + (inner * sin(angle)).toFloat()),
                        end   = Offset(center.x + (outer * cos(angle)).toFloat(), center.y + (outer * sin(angle)).toFloat()),
                        strokeWidth = thin
                    )
                }
            }
            else -> {
                // Minimal corner mark: a small ring, a short arc, a tight dot cluster, one line.
                val r = radius * 0.55f
                drawCircle(color = palette.border.copy(alpha = 0.5f), radius = r, center = center, style = Stroke(width = thin))
                drawArc(
                    color = palette.accent.copy(alpha = 0.7f),
                    startAngle = -40f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = thick * 0.8f, cap = StrokeCap.Round)
                )
                val dotSpacing = 10.dp.toPx()
                val dotRadius  = 1.2.dp.toPx()
                val gridOrigin = Offset(w * 0.6f, h * 0.62f)
                for (row in 0 until 3) for (col in 0 until 3) {
                    drawCircle(
                        color = palette.border.copy(alpha = 0.45f), radius = dotRadius,
                        center = Offset(gridOrigin.x + col * dotSpacing, gridOrigin.y + row * dotSpacing)
                    )
                }
                drawLine(
                    color = palette.accent.copy(alpha = 0.5f),
                    start = Offset(w * 0.05f, h * 0.9f), end = Offset(w * 0.2f, h * 0.72f),
                    strokeWidth = thin, cap = StrokeCap.Round
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (String) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    eventTracker: EventTracker,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalAppPalette.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Ambient decoration behind all content — soft glow blobs for every other theme.
        if (!palette.isModular) {
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
        }

        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            FeedContent(
                isLoading = uiState.isLoading,
                error = uiState.error,
                sections = uiState.sections,
                availableGenres = uiState.availableGenres,
                selectedGenre = uiState.selectedGenre,
                onSelectGenre = viewModel::selectGenre,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                eventTracker = eventTracker,
                playerViewModel = playerViewModel,
                onTrackClick = onTrackClick,
                onArtistClick = onArtistClick,
                onSectionImpression = viewModel::onSectionImpression,
                onSectionClick = viewModel::onSectionClick
            )
        }
    }
}

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onSearchClick: () -> Unit, onSettingsClick: () -> Unit) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(if (palette.isModular) RoundedCornerShape(10.dp) else CircleShape)
                .background(palette.accent)
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(modularIcon(Icons.Default.Person, Icons.Outlined.Person), contentDescription = "Settings", tint = palette.onAccent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        CircleIconButton(icon = modularIcon(Icons.Default.Search, Icons.Outlined.Search), contentDescription = "Search", onClick = onSearchClick)
        Spacer(modifier = Modifier.width(10.dp))
        CircleIconButton(icon = modularIcon(Icons.Default.FavoriteBorder, Icons.Outlined.FavoriteBorder), contentDescription = "Favorites", onClick = {})
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val palette = LocalAppPalette.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(if (palette.isModular) RoundedCornerShape(10.dp) else CircleShape)
            .background(palette.surface)
            .then(if (palette.isModular) Modifier.border(1.dp, palette.border, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = palette.onSurface, modifier = Modifier.size(20.dp))
    }
}

// How much slower the decoration moves than the content scrolling past it — 1f would mean no
// parallax (moves exactly with content); smaller values read as "sitting further back."
// Safe to use here because every decorated spot (the hero, an occasional section header) is
// a standalone accent that never has to stay pixel-aligned with a decorated neighbor — unlike
// the continuous-canvas approach this replaced, where independently-parallaxed adjacent slices
// drifted apart and broke the illusion entirely. One shape lagging behind its own content has
// no neighbor to fall out of sync with.
private const val PARALLAX_FACTOR = 0.55f

/**
 * Wraps decoration in a fixed, clipped window and lets the [content] drift inside it at
 * [PARALLAX_FACTOR] of the list's actual scroll speed. Two nested boxes, not one box with both
 * `clip` and `translationY` on the same graphicsLayer: a layer's own clip and its own
 * translation move together as one rigid unit, so that alone doesn't contain anything — it just
 * relocates the whole clipped rectangle. Here the *outer* box's clip is fixed by layout and
 * never moves; only the *inner* box (matching its size) translates, so content can drift within
 * the window but is always clipped by the stationary outer bounds — it can never bleed onto
 * whatever renders next in the list.
 */
@Composable
private fun ParallaxWindow(
    listState: LazyListState,
    itemKey: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.clipToBounds()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
                    translationY = if (info != null) -info.offset * (1f - PARALLAX_FACTOR) else 0f
                }
        ) {
            content()
        }
    }
}

// ── Hero header ──────────────────────────────────────────────────────────────
// TopBar + greeting + genre chips, as one LazyColumn item — living in the same scrolling list
// as everything else (not a separate static Column above it) so its decoration is visibly part
// of the same page, not disconnected chrome.

@Composable
private fun HeroHeader(
    availableGenres: List<String>,
    selectedGenre: String,
    onSelectGenre: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    listState: LazyListState
) {
    val palette = LocalAppPalette.current
    Box(modifier = Modifier.fillMaxWidth()) {
        if (palette.isModular) {
            ParallaxWindow(
                listState = listState,
                itemKey = "hero",
                modifier = Modifier.fillMaxWidth().height(210.dp)
            ) {
                ModularAbstractShape(
                    palette = palette,
                    modifier = Modifier.fillMaxSize(),
                    circleBias = Offset(0.74f, 0.44f),
                    variant = 0
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            TopBar(onSearchClick = onSearchClick, onSettingsClick = onSettingsClick)

            Text(
                text = "Hi, Gabriel",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = palette.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableGenres, key = { it }) { genre ->
                    val selected = genre == selectedGenre
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) palette.accent else palette.surface)
                            .clickable { onSelectGenre(genre) }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = genreLabel(genre),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) palette.onAccent else palette.subtleText
                        )
                    }
                }
            }
        }
    }
}

// ── Feed ─────────────────────────────────────────────────────────────────────

@Composable
private fun FeedContent(
    isLoading: Boolean,
    error: String?,
    sections: List<HomeSection>,
    availableGenres: List<String>,
    selectedGenre: String,
    onSelectGenre: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    eventTracker: EventTracker,
    playerViewModel: PlayerViewModel,
    onTrackClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onSectionImpression: (String) -> Unit,
    onSectionClick: (String) -> Unit
) {
    val palette = LocalAppPalette.current
    val listState = rememberLazyListState()
    var optionsTrack by remember { mutableStateOf<Track?>(null) }

    // First section renders as one big horizontal carousel item; the rest render as
    // one row-item per track. Index 0 is always the hero header now, so every section index
    // below is offset by one — the map only needs to stay internally consistent for the
    // impression-tracking lookup, so the +1 offset is baked in here rather than threaded
    // through every call site.
    val indexToSectionId = remember(sections) {
        buildMap<Int, String> {
            var idx = 1
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
        item(key = "hero") {
            HeroHeader(
                availableGenres = availableGenres,
                selectedGenre = selectedGenre,
                onSelectGenre = onSelectGenre,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                listState = listState
            )
        }

        when {
            isLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = palette.accent)
                }
            }

            error != null -> item(key = "error") {
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
            }

            else -> sections.forEachIndexed { sectionIndex, section ->
                item(key = "header_${section.id}") {
                    SectionHeader(
                        title = section.title,
                        showSeeAll = sectionIndex != 0,
                        // Every other section gets a small accent, cycling through the three
                        // variants — occasional, varied, and never adjacent to another one.
                        accentVariant = if (sectionIndex == 0 || sectionIndex % 2 == 0) null else sectionIndex % 3,
                        listState = listState,
                        itemKey = "header_${section.id}"
                    )
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
                                    onMoreClick = { optionsTrack = track },
                                    onArtistClick = onArtistClick
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
                            onMoreClick = { optionsTrack = track },
                            onArtistClick = onArtistClick
                        )
                    }
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
            onAddToQueue = { playerViewModel.queueTrackAtEnd(track) },
            onGoToArtist = onArtistClick
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    showSeeAll: Boolean,
    accentVariant: Int? = null,
    listState: LazyListState? = null,
    itemKey: Any? = null
) {
    val palette = LocalAppPalette.current
    // A minimum, not a fixed height: gives the accent a defined area to fill on the (common)
    // single-line case without risking clipping a title that wraps to two lines.
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) {
        if (palette.isModular && accentVariant != null && listState != null && itemKey != null) {
            val onEnd = accentVariant % 2 == 0
            val accentWidth = when (accentVariant % 3) { 0 -> 130.dp; 1 -> 150.dp; else -> 110.dp }
            ParallaxWindow(
                listState = listState,
                itemKey = itemKey,
                modifier = Modifier
                    .align(if (onEnd) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(accentWidth)
                    .fillMaxHeight()
            ) {
                ModularAbstractShape(
                    palette = palette,
                    modifier = Modifier.fillMaxSize(),
                    circleBias = Offset(0.5f, 0.5f),
                    variant = accentVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (palette.isModular) {
                Box(modifier = Modifier.width(3.dp).height(14.dp).background(palette.accent))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(title, color = palette.onBackground, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
            if (showSeeAll) Text("See all", color = palette.subtleText, fontSize = 13.sp)
        }
    }
}

// ── Curated carousel card ────────────────────────────────────────────────────

@Composable
private fun CuratedCard(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onArtistClick: (String) -> Unit = {}
) {
    val palette = LocalAppPalette.current
    Box(
        modifier = Modifier
            .width(270.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(if (palette.isModular) 12.dp else 28.dp))
            .background(
                if (palette.isModular) SolidColor(palette.surface)
                else Brush.linearGradient(listOf(Color(0xFF3A2E5C), Color(0xFF6B4FA0)))
            )
            .then(
                if (palette.isModular)
                    Modifier.border(1.dp, palette.border, RoundedCornerShape(12.dp))
                else Modifier
            )
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
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onArtistClick(track.artist) }
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(if (palette.isModular) RoundedCornerShape(8.dp) else CircleShape)
                        .background(palette.accent)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(modularIcon(Icons.Default.PlayArrow, Icons.Outlined.PlayArrow), contentDescription = "Play", tint = palette.onAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Icon(modularIcon(Icons.Default.FavoriteBorder, Icons.Outlined.FavoriteBorder), contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(16.dp))
                DownloadStatusIndicator(
                    track = track,
                    iconSize = 19.dp,
                    accentColor = palette.accent,
                    neutralColor = Color.White,
                    errorColor = Color(0xFFFF6B6B)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    modularIcon(Icons.Default.MoreHoriz, Icons.Outlined.MoreHoriz), contentDescription = "More options", tint = Color.White,
                    modifier = Modifier.size(19.dp).clickable(onClick = onMoreClick)
                )
            }
        }
    }
}

// ── Playlist-style row ───────────────────────────────────────────────────────
// Modular gets a translucent panel — surface color at partial alpha, not the fully opaque
// background that was explicitly ruled out earlier. The page's decorative background still
// shows through it, just tinted, so rows read as having some presence/color instead of text
// floating directly on flat page background. Classic/Light/Dark are untouched (same Modifier
// chain, just with all the Modular-only steps becoming no-ops).

@Composable
private fun PlaylistRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onArtistClick: (String) -> Unit = {}
) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (palette.isModular) Modifier.padding(horizontal = 12.dp, vertical = 4.dp) else Modifier)
            .then(
                if (palette.isModular) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surface.copy(alpha = 0.32f))
                        .border(1.dp, palette.border.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (palette.isModular) 12.dp else 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(if (palette.isModular) 8.dp else 14.dp))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title, color = palette.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                Text(
                    "By ",
                    color = palette.subtleText, fontSize = 12.sp, maxLines = 1
                )
                Text(
                    track.artist,
                    color = palette.subtleText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onArtistClick(track.artist) }
                )
                Text(
                    " • ${formatDuration(track.durationMs)}",
                    color = palette.subtleText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        DownloadStatusIndicator(
            track = track,
            accentColor = palette.accent,
            neutralColor = palette.subtleText,
            errorColor = Color(0xFFFF6B6B)
        )
        IconButton(onClick = onMoreClick) {
            Icon(modularIcon(Icons.Default.MoreVert, Icons.Outlined.MoreVert), contentDescription = "More options", tint = palette.subtleText)
        }
    }
}
