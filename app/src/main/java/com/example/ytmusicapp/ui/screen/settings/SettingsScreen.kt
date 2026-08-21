package com.example.ytmusicapp.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytmusicapp.data.datastore.AppColorMode
import com.example.ytmusicapp.data.datastore.AppTheme
import com.example.ytmusicapp.data.datastore.DefaultRepeatMode
import com.example.ytmusicapp.ui.theme.LocalAppPalette
import com.example.ytmusicapp.ui.theme.modularIcon

private data class SettingsSection(val title: String, val content: @Composable () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(modularIcon(Icons.AutoMirrored.Filled.ArrowBack, Icons.AutoMirrored.Outlined.ArrowBack), contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            item { SectionHeader("Theme") }
            item {
                ThemeFamilyRow(settings.theme, viewModel::setTheme)
            }

            item { SectionDivider() }
            item { SectionHeader("Appearance") }
            item {
                ColorModeRow(settings.colorMode, viewModel::setColorMode)
            }
            item {
                SwitchRow(
                    title = "Dynamic color",
                    subtitle = "Use colors from your wallpaper (Android 12+)",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }
            item {
                SwitchRow(
                    title = "Compact mode",
                    subtitle = "Tighter spacing in track lists",
                    checked = settings.compactMode,
                    onCheckedChange = viewModel::setCompactMode
                )
            }

            item { SectionDivider() }
            item { SectionHeader("Playback") }
            item {
                SwitchRow(
                    title = "Remember playback position",
                    subtitle = "Continue songs from where you last stopped listening",
                    checked = settings.rememberPosition,
                    onCheckedChange = viewModel::setRememberPosition
                )
            }
            item {
                SwitchRow(
                    title = "Resume where you left off",
                    subtitle = "Reopen the app to the same song, queue, shuffle and repeat state",
                    checked = settings.restorePlayerState,
                    onCheckedChange = viewModel::setRestorePlayerState
                )
            }
            item {
                SwitchRow(
                    title = "Autoplay recommendations",
                    subtitle = "Automatically extend the queue with similar songs",
                    checked = settings.autoplayEnabled,
                    onCheckedChange = viewModel::setAutoplayEnabled
                )
            }
            item {
                SwitchRow(
                    title = "Shuffle by default",
                    subtitle = "Start new sessions with shuffle turned on",
                    checked = settings.defaultShuffle,
                    onCheckedChange = viewModel::setDefaultShuffle
                )
            }
            item {
                RepeatModeRow(settings.defaultRepeatMode, viewModel::setDefaultRepeatMode)
            }

            item { SectionDivider() }
            item { SectionHeader("Audio") }
            item {
                SwitchRow(
                    title = "Volume normalization",
                    subtitle = "Even out loudness differences between songs",
                    checked = settings.volumeNormalization,
                    onCheckedChange = viewModel::setVolumeNormalization
                )
            }

            item { SectionDivider() }
            item { SectionHeader("Downloads") }
            item {
                SwitchRow(
                    title = "Download over Wi-Fi only",
                    subtitle = "Avoid using mobile data for downloads",
                    checked = settings.wifiOnlyDownloads,
                    onCheckedChange = viewModel::setWifiOnlyDownloads
                )
            }
            item {
                ActionRow(
                    title = "Clear all downloads",
                    subtitle = "Remove every downloaded song from this device",
                    actionLabel = "Clear",
                    onClick = viewModel::clearAllDownloads
                )
            }

            item { SectionDivider() }
            item { SectionHeader("Lyrics") }
            item {
                SwitchRow(
                    title = "Auto-open lyrics",
                    subtitle = "Show lyrics automatically when a song starts playing",
                    checked = settings.lyricsAutoOpen,
                    onCheckedChange = viewModel::setLyricsAutoOpen
                )
            }

            item { SectionDivider() }
            item { SectionHeader("Privacy") }
            item {
                SwitchRow(
                    title = "Listening history & diagnostics",
                    subtitle = "Used to power your recommendations and home feed",
                    checked = settings.diagnosticsEnabled,
                    onCheckedChange = viewModel::setDiagnosticsEnabled
                )
            }
            item {
                ActionRow(
                    title = "Clear listening history",
                    subtitle = "Delete play history, search history and remembered positions",
                    actionLabel = "Clear",
                    onClick = viewModel::clearListeningHistory
                )
            }
            item {
                ActionRow(
                    title = "Clear cache",
                    subtitle = "Free up space used by streamed audio and images",
                    actionLabel = "Clear",
                    onClick = viewModel::clearCache
                )
            }
            item { ModularFooterAccent() }
        }
    }
}

/** A single subtle geometric accent for the otherwise-empty space below the list — only
 *  rendered for the Modular theme, per its technical/industrial visual language. */
@Composable
private fun ModularFooterAccent() {
    val palette = LocalAppPalette.current
    if (!palette.isModular) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).border(1.dp, palette.accent, CircleShape))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(palette.border))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "MODULAR",
            style = MaterialTheme.typography.labelSmall,
            color = palette.subtleText,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClick) { Text(actionLabel) }
    }
}

// Visual design system — independent of light/dark. Adding a future theme means adding one
// more chip here, never a new axis alongside Appearance's Light/Dark Mode below.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeFamilyRow(current: AppTheme, onSelect: (AppTheme) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
            GenericChip("Classic", AppTheme.CLASSIC, current, onSelect)
            GenericChip("Modular", AppTheme.MODULAR, current, onSelect)
        }
    }
}

// Light/dark appearance — independent of which AppTheme is selected above.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorModeRow(current: AppColorMode, onSelect: (AppColorMode) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Color mode", style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.padding(top = 8.dp)) {
            GenericChip("Light Mode", AppColorMode.LIGHT, current, onSelect)
            GenericChip("Dark Mode", AppColorMode.DARK, current, onSelect)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> GenericChip(
    label: String,
    value: T,
    current: T,
    onSelect: (T) -> Unit
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        modifier = Modifier.padding(end = 8.dp),
        colors = FilterChipDefaults.filterChipColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatModeRow(current: DefaultRepeatMode, onSelect: (DefaultRepeatMode) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Repeat by default", style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.padding(top = 8.dp)) {
            RepeatChip("Off", DefaultRepeatMode.OFF, current, onSelect)
            RepeatChip("All", DefaultRepeatMode.ALL, current, onSelect)
            RepeatChip("One", DefaultRepeatMode.ONE, current, onSelect)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatChip(
    label: String,
    value: DefaultRepeatMode,
    current: DefaultRepeatMode,
    onSelect: (DefaultRepeatMode) -> Unit
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        modifier = Modifier.padding(end = 8.dp)
    )
}
