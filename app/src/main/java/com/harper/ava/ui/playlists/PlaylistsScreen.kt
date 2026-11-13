package com.harper.ava.ui.playlists

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.harper.ava.domain.model.Playlist
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.theme.AvaTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(app: AvaApplication, onOpenPlaylist: (Long) -> Unit) {
    val viewModel: PlaylistsViewModel = viewModel(
        factory = PlaylistsViewModel.factory(app.container.musicRepository, app.container.playerRepository),
    )
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    // 0.7.4: add songs straight from a playlist row — the picker needs
    // the library and the ids already in that playlist.
    val allSongs by viewModel.allSongs.collectAsState()
    var addSongsTarget by remember { mutableStateOf<Playlist?>(null) }
    var addSongsExisting by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.playlists_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.playlists_create))
            }
        },
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                // 0.1: the empty state used to say "create a playlist"
                // with no button in sight — only the floating action
                // button did it. The action is now right here, under
                // the words that ask for it.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        title = stringResource(R.string.playlists_empty_title),
                        body = stringResource(R.string.playlists_empty_body),
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                    )
                    Spacer(modifier = Modifier.height(AvaTokens.space16))
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(AvaTokens.space8))
                        Text(stringResource(R.string.playlists_create))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        onPlayAll = { viewModel.playPlaylist(playlist.id) },
                        // 0.7.4: open the add-songs picker for this
                        // playlist without entering its detail screen.
                        onAddSongs = {
                            scope.launch {
                                addSongsExisting = viewModel.playlistSongIds(playlist.id)
                                addSongsTarget = playlist
                            }
                        },
                        // Stage 6: deleting a playlist is destructive and
                        // used to happen on one tap with no confirmation.
                        onDelete = { playlistToDelete = playlist },
                        modifier = Modifier.animateItem(),
                    )
                }
                item { Spacer(modifier = Modifier.height(AvaTokens.space16)) }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
        )
    }

    val toDelete = playlistToDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.playlists_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.playlists_delete_confirm_body, toDelete.name),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(toDelete.id)
                        playlistToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.playlists_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.playlists_cancel))
                }
            },
        )
    }

    val addTarget = addSongsTarget
    if (addTarget != null) {
        AddSongsToPlaylistDialog(
            playlistName = addTarget.name,
            allSongs = allSongs,
            alreadyAddedIds = addSongsExisting,
            onDismiss = { addSongsTarget = null },
            onConfirm = { songIds ->
                scope.launch {
                    val added = viewModel.addSongsToPlaylist(addTarget.id, songIds)
                    if (added > 0) {
                        Toast.makeText(
                            context,
                            context.resources.getQuantityString(
                                R.plurals.playlist_add_songs_added,
                                added,
                                added,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    addSongsTarget = null
                }
            },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlayAll: () -> Unit,
    onAddSongs: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var artFailed by remember(playlist.coverArtUri) { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AvaTokens.space12),
    ) {
        // Stage 6: real cover art (first song's art) + song count — the row
        // used to show only the name, with no art and no count.
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.library_songs_count, playlist.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAddSongs) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.playlist_add_songs),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPlayAll) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.playlists_play_all),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.playlists_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlists_create_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text(stringResource(R.string.playlists_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name) },
            ) {
                Text(stringResource(R.string.playlists_create_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.playlists_cancel))
            }
        },
    )
}
