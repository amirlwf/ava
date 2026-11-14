package com.harper.ava.ui.home

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
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.harper.ava.ui.common.SkeletonList
import com.harper.ava.ui.components.DeleteSongOutcomeToasts
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.components.SongDeleteConfirmDialog
import com.harper.ava.ui.components.SongRow
import com.harper.ava.ui.scan.ScanUiState
import com.harper.ava.ui.scan.openAppSettings
import com.harper.ava.ui.scan.rememberScanLauncher
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.INACTIVE_TRACK_ALPHA
import com.harper.ava.ui.common.AvaMotion

@Composable
fun HomeScreen(
    app: AvaApplication,
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSongDetails: (Long) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenRingtoneEditor: (Long) -> Unit = {},
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

    // 0.7.2 song delete: in-app confirm dialog + outcome Toasts.
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    DeleteSongOutcomeToasts()
    SongDeleteConfirmDialog(
        song = songToDelete,
        onDismiss = { songToDelete = null },
        onConfirm = { song -> viewModel.deleteSong(song); songToDelete = null },
    )

    LazyColumn(
        // Edge-to-edge (Harper 2026-11-13): Home has no TopAppBar, so
        // it owns its own status-bar inset — the outer Scaffold no
        // longer adds it (TopAppBar screens get it from the bar).
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        verticalArrangement = Arrangement.Top,
    ) {
    // Run 13 (perf): contentType on every item — this list mixes a
    // greeting header, permission card, empty state, section headers,
    // song rows, playlist rows and a spacer, and without a type
    // Compose reuses one composition pool for all of them, so a
    // header slot recycled for a song row recomposes from scratch
    // while scrolling. (Android docs: always specify contentType
    // for heterogeneous lists.)
    item(contentType = "header") {
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
            // 0.6.0: a little more air under the greeting so the
            // header breathes instead of crowding the first section.
            Spacer(modifier = Modifier.height(AvaTokens.space16))
            when (scanState) {
                ScanUiState.Scanning -> {
                    Text(
                        text = stringResource(R.string.scan_scanning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(AvaTokens.space8))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        // 0.2-fix (Track 2): the M3 default track is
                        // surfaceVariant — invisible on pure black in
                        // AMOLED. An explicit onSurface track at
                        // INACTIVE_TRACK_ALPHA stays visible.
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_TRACK_ALPHA),
                    )
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
        item(contentType = "permission") {
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
        item(contentType = "empty") {
            // 0.7.0: while the first MediaStore scan is still running
            // the library is genuinely loading — shimmer rows, not a
            // "no songs" state that would flash away a second later.
            if (scanState is ScanUiState.Scanning) {
                SkeletonList()
            } else {
                // Run 6 (UX): the note used to say "tap Rescan" with
                // no button in sight — the action is now right here,
                // next to the words that ask for it.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        note = stringResource(R.string.home_empty_note),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AvaTokens.space48),
                    )
                    Spacer(modifier = Modifier.height(AvaTokens.space16))
                    Button(onClick = requestScan) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(AvaTokens.space8))
                        Text(stringResource(R.string.scan_rescan))
                    }
                }
            }
        }
    } else {
        if (recentlyPlayed.isNotEmpty()) {
            item(contentType = "section-header") {
                SectionTitle(stringResource(R.string.home_recently_played))
            }
            // 0.5.6: keys are section-prefixed — the same song is in
            // recentlyPlayed AND recentlyAdded as soon as it is played
            // once, and a bare id key crashed 0.5.5 (duplicate key).
            items(recentlyPlayed, key = { homeSectionSongKey("played", it.id) }, contentType = { "song" }) { song ->
                HomeSongRow(
                    viewModel, playlists, song, recentlyPlayed, onOpenSongDetails, onOpenRingtoneEditor,
                    modifier = Modifier.padding(horizontal = AvaTokens.space8).animateItem(placementSpec = AvaMotion.listItemPlacement),
                ) { songToDelete = it }
            }
        }
        if (recentlyAdded.isNotEmpty()) {
            item(contentType = "section-header") {
                SectionTitle(stringResource(R.string.home_recently_added))
            }
            items(recentlyAdded, key = { homeSectionSongKey("added", it.id) }, contentType = { "song" }) { song ->
                HomeSongRow(
                    viewModel, playlists, song, recentlyAdded, onOpenSongDetails, onOpenRingtoneEditor,
                    modifier = Modifier.padding(horizontal = AvaTokens.space8).animateItem(placementSpec = AvaMotion.listItemPlacement),
                ) { songToDelete = it }
            }
        }
        if (favorites.isNotEmpty()) {
            item(contentType = "favorites-header") {
                // Stage 6: the section header opens the full Favorites
                // screen (Play All / Shuffle All) instead of being dead text.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenFavorites)
                        .padding(
                            start = AvaTokens.space20,
                            end = AvaTokens.space20,
                            top = AvaTokens.space20,
                            bottom = AvaTokens.space12,
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
            items(favorites, key = { homeSectionSongKey("fav", it.id) }, contentType = { "song" }) { song ->
                HomeSongRow(
                    viewModel, playlists, song, favorites, onOpenSongDetails, onOpenRingtoneEditor,
                    modifier = Modifier.padding(horizontal = AvaTokens.space8).animateItem(placementSpec = AvaMotion.listItemPlacement),
                ) { songToDelete = it }
            }
        }
        if (playlists.isNotEmpty()) {
            item(contentType = "section-header") {
                SectionTitle(stringResource(R.string.home_your_playlists))
            }
            // Playlists share this LazyColumn's key namespace with the
            // song sections above; their ids come from another table.
            items(playlists, key = { "playlist:${it.id}" }, contentType = { "playlist" }) { playlist ->
                HomePlaylistRow(
                    playlist, onOpenPlaylist,
                    modifier = Modifier.animateItem(placementSpec = AvaMotion.listItemPlacement),
                )
            }
        }
    }

    item(contentType = "spacer") { Spacer(modifier = Modifier.height(AvaTokens.space24)) }
    }
}

@Composable
private fun HomeSongRow(
    viewModel: HomeViewModel,
    playlists: List<com.harper.ava.domain.model.Playlist>,
    song: Song,
    section: List<Song>,
    onOpenSongDetails: (Long) -> Unit,
    onOpenRingtoneEditor: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onDeleteSong: (Song) -> Unit,
) {
    SongRow(
        song = song,
        onClick = { viewModel.playInList(song, section) },
        onToggleFavorite = { viewModel.toggleFavorite(song) },
        playlists = playlists,
        onAddToPlaylist = { playlistId -> viewModel.addToPlaylist(song, playlistId) },
        onCreatePlaylistAndAdd = { name -> viewModel.createPlaylistAndAdd(song, name) },
        onAddToQueue = { viewModel.addToQueue(song, playNext = false) },
        onPlayNext = { viewModel.addToQueue(song, playNext = true) },
        onOpenDetails = { onOpenSongDetails(song.id) },
        onDeleteSong = onDeleteSong,
        onSetAsRingtone = { onOpenRingtoneEditor(song.id) },
        modifier = modifier,
    )
}

/**
 * Playlist row with cover art: the first song's art, or a tonal queue-music
 * placeholder. Stage 6 closes the "playlists show no cover art" P3.
 */
@Composable
private fun HomePlaylistRow(
    playlist: Playlist,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var artFailed by remember(playlist.coverArtUri) { mutableStateOf(false) }
    Row(
        modifier = modifier
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
        // 0.6.0: same soft language as SongRow — a breath between the
        // playlist name and its song count.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AvaTokens.space2),
        ) {
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
    // 0.6.0: more air above/below section titles — sections read as
    // calm groups, not a dense list.
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(
            start = AvaTokens.space20,
            end = AvaTokens.space20,
            top = AvaTokens.space20,
            bottom = AvaTokens.space12,
        ),
    )
}
