package com.harper.ava.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.domain.model.Song
import com.harper.ava.ui.components.DeleteSongOutcomeToasts
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.components.SongDeleteConfirmDialog
import com.harper.ava.ui.components.SongRow
import com.harper.ava.ui.theme.AvaTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    app: AvaApplication,
    playlistId: Long,
    onBack: () -> Unit,
    onOpenSongDetails: (Long) -> Unit = {},
) {
    val viewModel: PlaylistDetailViewModel = viewModel(
        factory = PlaylistDetailViewModel.factory(
            playlistId,
            app.container.musicRepository,
            app.container.playerRepository,
        ),
    )
    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    // 0.7.2 playlist-add fix: rows inside a playlist can now be added to other playlists.
    val playlists by viewModel.playlists.collectAsState()

    // 0.7.2 song delete: in-app confirm dialog + outcome Toasts.
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    DeleteSongOutcomeToasts()
    SongDeleteConfirmDialog(
        song = songToDelete,
        onDismiss = { songToDelete = null },
        onConfirm = { song -> viewModel.deleteSong(song); songToDelete = null },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(playlist?.name ?: "") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
            },
            actions = {
                if (songs.isNotEmpty()) {
                    IconButton(onClick = { viewModel.shuffleAll(songs) }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = stringResource(R.string.favorites_shuffle_all),
                        )
                    }
                    IconButton(onClick = { viewModel.playAll(songs) }) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.playlists_play_all),
                        )
                    }
                }
            },
        )
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.playlist_detail_empty_title),
                    body = stringResource(R.string.playlist_detail_empty_body),
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.playAll(songs.dropWhile { it.id != song.id }) },
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        onRemoveFromPlaylist = { viewModel.removeSong(song) },
                        playlists = playlists,
                        onAddToPlaylist = { id -> viewModel.addToPlaylist(song, id) },
                        onCreatePlaylistAndAdd = { name -> viewModel.createPlaylistAndAdd(song, name) },
                        onOpenDetails = { onOpenSongDetails(song.id) },
                        onDeleteSong = { songToDelete = it },
                        modifier = Modifier.padding(horizontal = AvaTokens.space8),
                    )
                }
                item { Spacer(modifier = Modifier.height(AvaTokens.space16)) }
            }
        }
    }
}
