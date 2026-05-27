package com.example.ytmusicapp.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ytmusicapp.ui.component.MiniPlayer
import com.example.ytmusicapp.ui.screen.downloads.DownloadsScreen
import com.example.ytmusicapp.ui.screen.home.HomeScreen
import com.example.ytmusicapp.ui.screen.library.LibraryScreen
import com.example.ytmusicapp.ui.screen.player.PlayerScreen
import com.example.ytmusicapp.ui.screen.player.PlayerViewModel
import com.example.ytmusicapp.ui.screen.search.SearchScreen

private data class BottomNavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val screen: Screen
)

private val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Default.Home, Screen.Home),
    BottomNavItem("Search", Icons.Default.Search, Screen.Search),
    BottomNavItem("Library", Icons.Default.LibraryMusic, Screen.Library),
    BottomNavItem("Downloads", Icons.Default.Download, Screen.Downloads)
)

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AppNavGraph(
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isPlayerScreen = currentDestination?.hasRoute(Screen.Player::class) == true

    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

    // Derive 0f..1f progress from positionMs / durationMs
    val progress = if (playerState.durationMs > 0L) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Scaffold(
        bottomBar = {
            if (!isPlayerScreen) {
                Column {
                    // MiniPlayer only when a track is loaded
                    playerState.track?.let { track ->
                        MiniPlayer(
                            track = track,
                            isPlaying = playerState.isPlaying,
                            progress = progress,
                            onPlayPause = playerViewModel::onPlayPause,
                            onSkipNext = { /* no queue yet — no-op */ },
                            onExpand = {
                                navController.navigate(Screen.Player(track.id))
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
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Screen.Home> {
                HomeScreen(
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    }
                )
            }
            composable<Screen.Search> {
                SearchScreen(
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    }
                )
            }
            composable<Screen.Library> {
                LibraryScreen(
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    }
                )
            }
            composable<Screen.Downloads> {
                DownloadsScreen(
                    onTrackClick = { trackId ->
                        navController.navigate(Screen.Player(trackId))
                    }
                )
            }
            composable<Screen.Player> { backStackEntry ->
                val player: Screen.Player = backStackEntry.toRoute()
                PlayerScreen(
                    trackId = player.trackId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}