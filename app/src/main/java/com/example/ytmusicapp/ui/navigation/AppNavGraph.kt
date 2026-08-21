package com.example.ytmusicapp.ui.navigation

import android.content.ComponentName
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ytmusicapp.data.tracker.EventTracker
import com.example.ytmusicapp.playback.AudioPlaybackService
import com.example.ytmusicapp.ui.component.MiniPlayer
import com.example.ytmusicapp.ui.screen.album.AlbumScreen
import com.example.ytmusicapp.ui.screen.artist.ArtistScreen
import com.example.ytmusicapp.ui.screen.artist.ArtistSectionScreen
import com.example.ytmusicapp.ui.screen.downloads.DownloadsScreen
import com.example.ytmusicapp.ui.screen.home.HomeScreen
import com.example.ytmusicapp.ui.screen.library.LibraryScreen
import com.example.ytmusicapp.ui.screen.player.PlayerScreen
import com.example.ytmusicapp.ui.screen.player.PlayerViewModel
import com.example.ytmusicapp.ui.screen.search.SearchScreen
import com.example.ytmusicapp.ui.screen.settings.SettingsScreen
import com.example.ytmusicapp.ui.theme.modularIcon
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// ── Thin ViewModel — exposes EventTracker to the nav graph ───────────────────
@HiltViewModel
class EventTrackerViewModel @Inject constructor(
    val eventTracker: EventTracker
) : ViewModel()

private data class BottomNavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val outlinedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val screen: Screen
)

private val bottomNavItems = listOf(
    BottomNavItem("Home",      Icons.Default.Home,         Icons.Outlined.Home,         Screen.Home),
    BottomNavItem("Search",    Icons.Default.Search,       Icons.Outlined.Search,       Screen.Search),
    BottomNavItem("Library",   Icons.Default.LibraryMusic, Icons.Outlined.LibraryMusic, Screen.Library),
    BottomNavItem("Downloads", Icons.Default.Download,     Icons.Outlined.Download,     Screen.Downloads)
)

@ExperimentalMaterial3Api
@Composable
fun AppNavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isPlayerScreen = currentDestination?.hasRoute(Screen.Player::class) == true ||
        currentDestination?.hasRoute(Screen.Artist::class) == true ||
        currentDestination?.hasRoute(Screen.Album::class) == true ||
        currentDestination?.hasRoute(Screen.ArtistSection::class) == true

    val playerViewModel: PlayerViewModel       = hiltViewModel()
    val etViewModel: EventTrackerViewModel     = hiltViewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val eventTracker = etViewModel.eventTracker

    // MediaController lives here — survives navigation
    DisposableEffect(Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioPlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            playerViewModel.setMediaController(controller)
        }, MoreExecutors.directExecutor())

        onDispose { MediaController.releaseFuture(controllerFuture) }
    }

    val progress = if (playerState.durationMs > 0L)
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    else 0f

    Scaffold(
        bottomBar = {
            if (!isPlayerScreen) {
                Column {
                    playerState.track?.let { track ->
                        MiniPlayer(
                            track       = track,
                            isPlaying   = playerState.isPlaying,
                            progress    = progress,
                            onPlayPause = playerViewModel::onPlayPause,
                            onSkipNext  = {},
                            onExpand    = {
                                navController.navigate(Screen.Player(track.id)) {
                                    launchSingleTop = true
                                }
                            },
                            onArtistClick = { artist ->
                                navController.navigate(Screen.Artist(artist))
                            }
                        )
                    }
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentDestination?.hasRoute(item.screen::class) == true,
                                onClick = {
                                    navController.navigate(item.screen) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                },
                                icon  = { Icon(modularIcon(item.icon, item.outlinedIcon), contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Home,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable<Screen.Home> {
                HomeScreen(
                    playerViewModel = playerViewModel,
                    eventTracker    = eventTracker,
                    onTrackClick    = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    },
                    onArtistClick = { artist ->
                        navController.navigate(Screen.Artist(artist))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    onSettingsClick = { navController.navigate(Screen.Settings) }
                )
            }
            composable<Screen.Search> {
                SearchScreen(
                    onTrackClick    = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    },
                    onArtistClick = { artist ->
                        navController.navigate(Screen.Artist(artist))
                    },
                    playerViewModel = playerViewModel,
                    eventTracker    = eventTracker
                )
            }
            composable<Screen.Library> {
                LibraryScreen(
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    },
                    onArtistClick = { artist ->
                        navController.navigate(Screen.Artist(artist))
                    }
                )
            }
            composable<Screen.Downloads> {
                DownloadsScreen(
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    },
                    onArtistClick = { artist ->
                        navController.navigate(Screen.Artist(artist))
                    },
                    playerViewModel = playerViewModel
                )
            }
            composable<Screen.Settings> {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable<Screen.Player> { backStackEntry ->
                val player: Screen.Player = backStackEntry.toRoute()
                PlayerScreen(
                    trackId  = player.trackId,
                    onBack   = { navController.popBackStack() },
                    onArtistClick = { artist ->
                        navController.navigate(Screen.Artist(artist))
                    },
                    viewModel = playerViewModel
                )
            }
            composable<Screen.Artist> { backStackEntry ->
                val args: Screen.Artist = backStackEntry.toRoute()
                ArtistScreen(
                    artistKey = args.artistKey,
                    onBack    = { navController.popBackStack() },
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    },
                    onArtistClick = { artist ->
                        navController.navigate(Screen.Artist(artist))
                    },
                    onAlbumClick = { artistKey, albumName ->
                        navController.navigate(Screen.Album(artistKey, albumName))
                    },
                    onExpandSection = { section ->
                        navController.navigate(Screen.ArtistSection(args.artistKey, section))
                    },
                    playerViewModel = playerViewModel
                )
            }
            composable<Screen.ArtistSection> { backStackEntry ->
                val args: Screen.ArtistSection = backStackEntry.toRoute()
                ArtistSectionScreen(
                    artistKey = args.artistKey,
                    section   = args.section,
                    onBack    = { navController.popBackStack() },
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    },
                    onArtistClick = { artist ->
                        navController.navigate(Screen.Artist(artist))
                    },
                    playerViewModel = playerViewModel
                )
            }
            composable<Screen.Album> { backStackEntry ->
                val args: Screen.Album = backStackEntry.toRoute()
                AlbumScreen(
                    artistKey = args.artistKey,
                    albumName = args.albumName,
                    onBack    = { navController.popBackStack() },
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    },
                    onArtistClick = { artist ->
                        navController.navigate(Screen.Artist(artist))
                    },
                    playerViewModel = playerViewModel
                )
            }
        }
    }
}