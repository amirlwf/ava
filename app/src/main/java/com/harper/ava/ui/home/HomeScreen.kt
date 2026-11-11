package com.harper.ava.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.homeSectionSongKey
import com.harper.ava.data.media.AudioPermission
import com.harper.ava.domain.model.Playlist
import com.harper.ava.domain.model.Song
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.components.SongRow
import com.harper.ava.ui.scan.ScanUiState
import com.harper.ava.ui.scan.openAppSettings
import com.harper.ava.ui.scan.rememberScanLauncher
import com.harper.ava.ui.theme.AvaTokens

@Composable
fun HomeScreen(
    app: AvaApplication,
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSongDetails: (Long) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(app.container.musicRepository, app.container.playerRepository),
    )
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val songCount by viewModel.songCount.collectAsState()
    val scanState by viewModel.scanState.collectAsState()

    val context = LocalContext.current
    val hasPermission = AudioPermission.isGranted(context)
    val requestScan = rememberScanLauncher(
        app = app,
        onScanStarted = viewModel::onScanStarted,
        onScanResult = viewModel::onScanResult,
    )

    val greetingRes = when (viewModel.greetingHour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..16 -> R.string.home_greeting_afternoon
        in 17..19 -> R.string.home_greeting_evening
        else -> R.string.home_greeting_night
    }

    val isEmpty = songCount == 0 && playlists.isEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = AvaTokens.space20)) {
                Spacer(modifier = Modifier.height(AvaTokens.space16))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(greetingRes),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(AvaTokens.space4))
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = requestScan) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.scan_rescan),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AvaTokens.space12))
                when (scanState) {
                    ScanUiState.Scanning -> {
                        Text(
                            text = stringResource(R.string.scan_scanning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(AvaTokens.space8))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(AvaTokens.space12))
                    }
                    is ScanUiState.Done -> {
                        val done = scanState as ScanUiState.Done
                        Text(
                            text = if (done.removedStale > 0) {
                                stringResource(
                                    R.string.scan_found_removed,
                                    done.found,
                                    done.removedStale,
                                )
                            } else {
                                stringResource(R.string.scan_found, done.found)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(AvaTokens.space12))
                    }
                    ScanUiState.Error -> {
                        Text(
                            text = stringResource(R.string.scan_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(AvaTokens.space12))
                    }
                    else -> {
                        Spacer(modifier = Modifier.height(AvaTokens.space12))
                    }
                }
            }
        }

        if (!hasPermission) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space8),
                ) {
                    Text(
                        text = stringResource(R.string.scan_permission_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(AvaTokens.space4))
                    Text(
                        text = stringResource(R.string.scan_permission_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(AvaTokens.space12))
                    Row(horizontalArrangement = Arrangement.spacedBy(AvaTokens.space8)) {
                        Button(onClick = requestScan) {
                            Text(stringResource(R.string.scan_permission_grant))
                        }
                        if (scanState is ScanUiState.PermissionDenied) {
                            OutlinedButton(onClick = { openAppSettings(context) }) {
                                Text(stringResource(R.string.scan_permission_settings))
                            }
                        }
                    }
                    if (scanState is ScanUiState.PermissionDenied) {
                        Spacer(modifier = Modifier.height(AvaTokens.space8))
                        Text(
                            text = stringResource(R.string.scan_permission_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (isEmpty) {
            item {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    note = stringResource(R.string.home_empty_note),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AvaTokens.space48),
                )
            }
        } else {
            if (recentlyPlayed.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.home_recently_played))
                }
                // 0.5.6: keys are section-prefixed — the same song is in
                // recentlyPlayed AND recentlyAdded as soon as it is played
                // once, and a bare id key crashed 0.5.5 (duplicate key).
                items(recentlyPlayed, key = { homeSectionSongKey("played", it.id) }) { song ->
                    HomeSongRow(viewModel, playlists, song, recentlyPlayed, onOpenSongDetails)
                }
            }
            if (recentlyAdded.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.home_recently_added))
                }
                items(recentlyAdded, key = { homeSectionSongKey("added", it.id) }) { song ->
                    HomeSongRow(viewModel, playlists, song, recentlyAdded, onOpenSongDetails)
                }
            }
            if (favorites.isNotEmpty()) {
                item {
                    // Stage 6: the section header opens the full Favorites
                    // screen (Play All / Shuffle All) instead of being dead text.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenFavorites)
                            .padding(
                                start = AvaTokens.space20,
                                end = AvaTokens.space20,
                                top = AvaTokens.space16,
                                bottom = AvaTokens.space8,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_favorites),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.home_show_all),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(favorites, key = { homeSectionSongKey("fav", it.id) }) { song ->
                    HomeSongRow(viewModel, playlists, song, favorites, onOpenSongDetails)
                }
            }
            if (playlists.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.home_your_playlists))
                }
                // Playlists share this LazyColumn's key namespace with the
                // song sections above; their ids come from another table.
                items(playlists, key = { "playlist:${it.id}" }) { playlist ->
                    HomePlaylistRow(playlist, onOpenPlaylist)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(AvaTokens.space24)) }
    }
}

@Composable
private fun HomeSongRow(
    viewModel: HomeViewModel,
    playlists: List<com.harper.ava.domain.model.Playlist>,
    song: Song,
    section: List<Song>,
    onOpenSongDetails: (Long) -> Unit,
) {
    SongRow(
        song = song,
        onClick = { viewModel.playInList(song, section) },
        onToggleFavorite = { viewModel.toggleFavorite(song) },
        playlists = playlists,
        onAddToPlaylist = { playlistId -> viewModel.addToPlaylist(song, playlistId) },
        onAddToQueue = { viewModel.addToQueue(song, playNext = false) },
        onPlayNext = { viewModel.addToQueue(song, playNext = true) },
        onOpenDetails = { onOpenSongDetails(song.id) },
        modifier = Modifier.padding(horizontal = AvaTokens.space8),
    )
}

/**
 * Playlist row with cover art: the first song's art, or a tonal queue-music
 * placeholder. Stage 6 closes the "playlists show no cover art" P3.
 */
@Composable
private fun HomePlaylistRow(playlist: Playlist, onOpenPlaylist: (Long) -> Unit) {
    var artFailed by remember(playlist.coverArtUri) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPlaylist(playlist.id) }
            .padding(
                horizontal = AvaTokens.space20,
                vertical = AvaTokens.space12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvaTokens.coverSmall)
                .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (playlist.coverArtUri != null && !artFailed) {
                AsyncImage(
                    model = playlist.coverArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { artFailed = true },
                    modifier = Modifier.size(AvaTokens.coverSmall),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(AvaTokens.space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.library_songs_count,
                    playlist.songCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(
            start = AvaTokens.space20,
            end = AvaTokens.space20,
            top = AvaTokens.space16,
            bottom = AvaTokens.space8,
        ),
    )
}
