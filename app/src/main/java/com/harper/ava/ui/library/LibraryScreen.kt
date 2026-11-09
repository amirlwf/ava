package com.harper.ava.ui.library

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.data.repository.SongSort
import com.harper.ava.domain.model.Song
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.components.SongRow
import com.harper.ava.ui.scan.ScanUiState
import com.harper.ava.ui.scan.rememberScanLauncher
import com.harper.ava.ui.theme.AvaTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    app: AvaApplication,
    onOpenSongDetails: (Long) -> Unit = {},
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(app.container.musicRepository, app.container.playerRepository),
    )
    val songs by viewModel.songs.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val groupSongs by viewModel.groupSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val openGroup by viewModel.openGroup.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    var sortMenuOpen by remember { mutableStateOf(false) }

    val requestScan = rememberScanLauncher(
        app = app,
        onScanStarted = viewModel::onScanStarted,
        onScanResult = viewModel::onScanResult,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.library_title)) },
            actions = {
                IconButton(onClick = requestScan) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.scan_rescan),
                    )
                }
                if (tab == LibraryTab.SONGS && openGroup == null) {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.library_sort_label),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sort_title)) },
                                onClick = { viewModel.setSort(SongSort.TITLE); sortMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sort_artist)) },
                                onClick = { viewModel.setSort(SongSort.ARTIST); sortMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sort_date)) },
                                onClick = { viewModel.setSort(SongSort.DATE_ADDED); sortMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sort_duration)) },
                                onClick = { viewModel.setSort(SongSort.DURATION); sortMenuOpen = false },
                            )
                        }
                    }
                }
            },
        )
        when (scanState) {
            ScanUiState.Scanning -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.scan_scanning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space4),
                )
            }
            is ScanUiState.Done -> {
                val done = scanState as ScanUiState.Done
                Text(
                    text = stringResource(R.string.scan_found, done.found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space4),
                )
            }
            ScanUiState.Error -> {
                Text(
                    text = stringResource(R.string.scan_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space4),
                )
            }
            else -> {}
        }

        if (openGroup != null) {
            val group = openGroup!!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AvaTokens.space8, vertical = AvaTokens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.closeGroup() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
                Text(
                    text = when (group) {
                        is LibraryGroup.Artist -> group.name.ifBlank {
                            stringResource(R.string.unknown_artist)
                        }
                        is LibraryGroup.Album -> group.name.ifBlank {
                            stringResource(R.string.unknown_album)
                        }
                        is LibraryGroup.Folder -> com.harper.ava.core.util
                            .folderDisplayName(group.path)
                            ?: stringResource(R.string.unknown_folder)
                        is LibraryGroup.Genre -> group.name
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SongList(
                songs = groupSongs,
                viewModel = viewModel,
                playlists = playlists,
                onOpenSongDetails = onOpenSongDetails,
            )
            return@Column
        }

        // Five tabs: scrollable so labels never ellipsize on narrow screens.
        PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
            Tab(
                selected = tab == LibraryTab.SONGS,
                onClick = { viewModel.setTab(LibraryTab.SONGS) },
                text = { Text(stringResource(R.string.library_tab_songs)) },
            )
            Tab(
                selected = tab == LibraryTab.ARTISTS,
                onClick = { viewModel.setTab(LibraryTab.ARTISTS) },
                text = { Text(stringResource(R.string.library_tab_artists)) },
            )
            Tab(
                selected = tab == LibraryTab.ALBUMS,
                onClick = { viewModel.setTab(LibraryTab.ALBUMS) },
                text = { Text(stringResource(R.string.library_tab_albums)) },
            )
            Tab(
                selected = tab == LibraryTab.FOLDERS,
                onClick = { viewModel.setTab(LibraryTab.FOLDERS) },
                text = { Text(stringResource(R.string.library_tab_folders)) },
            )
            Tab(
                selected = tab == LibraryTab.GENRES,
                onClick = { viewModel.setTab(LibraryTab.GENRES) },
                text = { Text(stringResource(R.string.library_tab_genres)) },
            )
        }

        when (tab) {
            LibraryTab.SONGS -> {
                if (songs.isEmpty()) {
                    LibraryEmpty()
                } else {
                    Text(
                        text = stringResource(R.string.library_songs_count, songs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space4),
                    )
                    SongList(
                        songs = songs,
                        viewModel = viewModel,
                        playlists = playlists,
                        onOpenSongDetails = onOpenSongDetails,
                    )
                }
            }
            LibraryTab.ARTISTS -> {
                if (artists.isEmpty()) {
                    LibraryEmpty()
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(artists, key = { it.name }) { artist ->
                            GroupRow(
                                name = artist.name.ifBlank {
                                    stringResource(R.string.unknown_artist)
                                },
                                subtitle = stringResource(
                                    R.string.library_songs_count,
                                    artist.songCount,
                                ),
                                artUri = artist.albumArtUri,
                                fallbackIcon = Icons.Filled.Person,
                                onClick = { viewModel.openGroup(LibraryGroup.Artist(artist.name)) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(AvaTokens.space16)) }
                    }
                }
            }
            LibraryTab.ALBUMS -> {
                if (albums.isEmpty()) {
                    LibraryEmpty()
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(albums, key = { "${it.artist}|${it.name}" }) { album ->
                            GroupRow(
                                name = album.name.ifBlank {
                                    stringResource(R.string.unknown_album)
                                },
                                subtitle = album.artist.ifBlank {
                                    stringResource(R.string.unknown_artist)
                                } + " • " + stringResource(
                                    R.string.library_songs_count,
                                    album.songCount,
                                ),
                                artUri = album.albumArtUri,
                                fallbackIcon = Icons.Filled.Album,
                                onClick = { viewModel.openGroup(LibraryGroup.Album(album.name)) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(AvaTokens.space16)) }
                    }
                }
            }
            LibraryTab.FOLDERS -> {
                if (folders.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = stringResource(R.string.library_folders_empty_title),
                            body = stringResource(R.string.library_folders_empty_body),
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(folders, key = { it.name }) { folder ->
                            GroupRow(
                                name = com.harper.ava.core.util.folderDisplayName(folder.name)
                                    ?: stringResource(R.string.unknown_folder),
                                subtitle = stringResource(
                                    R.string.library_songs_count,
                                    folder.songCount,
                                ),
                                artUri = folder.albumArtUri,
                                fallbackIcon = Icons.Filled.Folder,
                                onClick = { viewModel.openGroup(LibraryGroup.Folder(folder.name)) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(AvaTokens.space16)) }
                    }
                }
            }
            LibraryTab.GENRES -> {
                if (genres.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = stringResource(R.string.library_genres_empty_title),
                            body = stringResource(R.string.library_genres_empty_body),
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(genres, key = { it.name }) { genre ->
                            GroupRow(
                                name = genre.name,
                                subtitle = stringResource(
                                    R.string.library_songs_count,
                                    genre.songCount,
                                ),
                                artUri = genre.albumArtUri,
                                fallbackIcon = Icons.Filled.MusicNote,
                                onClick = { viewModel.openGroup(LibraryGroup.Genre(genre.name)) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(AvaTokens.space16)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmpty() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body),
        )
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    viewModel: LibraryViewModel,
    playlists: List<com.harper.ava.domain.model.Playlist>,
    onOpenSongDetails: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                onClick = { viewModel.play(song, songs) },
                onToggleFavorite = { viewModel.toggleFavorite(song) },
                playlists = playlists,
                onAddToPlaylist = { playlistId -> viewModel.addToPlaylist(song, playlistId) },
                onAddToQueue = { viewModel.addToQueue(song, playNext = false) },
                onPlayNext = { viewModel.addToQueue(song, playNext = true) },
                onOpenDetails = { onOpenSongDetails(song.id) },
                modifier = Modifier.padding(horizontal = AvaTokens.space8),
            )
        }
        item { Spacer(modifier = Modifier.height(AvaTokens.space16)) }
    }
}

@Composable
private fun GroupRow(
    name: String,
    subtitle: String,
    artUri: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    var artFailed by remember(artUri) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AvaTokens.songRowHeight)
            .clip(RoundedCornerShape(AvaTokens.radiusMedium))
            .clickable(onClick = onClick)
            .padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvaTokens.coverSmall)
                .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (artUri != null && !artFailed) {
                AsyncImage(
                    model = artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { artFailed = true },
                    modifier = Modifier.size(AvaTokens.coverSmall),
                )
            } else {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(AvaTokens.space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
