package com.harper.ava.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.components.SongRow
import com.harper.ava.ui.theme.AvaTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    app: AvaApplication,
    onBack: () -> Unit,
    onOpenSongDetails: (Long) -> Unit = {},
) {
    val viewModel: FavoritesViewModel = viewModel(
        factory = FavoritesViewModel.factory(app.container.musicRepository, app.container.playerRepository),
    )
    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.favorites_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
            },
        )
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.favorites_empty_title),
                    body = stringResource(R.string.favorites_empty_body),
                    icon = Icons.Filled.Favorite,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space8),
                horizontalArrangement = Arrangement.spacedBy(AvaTokens.space8),
            ) {
                Button(
                    onClick = { viewModel.playAll(favorites) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.playlists_play_all))
                }
                OutlinedButton(
                    onClick = { viewModel.shuffleAll(favorites) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Text(stringResource(R.string.favorites_shuffle_all))
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(favorites, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.play(song, favorites) },
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
    }
}
