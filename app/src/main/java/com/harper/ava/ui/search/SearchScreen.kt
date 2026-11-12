package com.harper.ava.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.harper.ava.ui.common.SkeletonList
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.components.SongRow
import com.harper.ava.ui.theme.AvaTokens
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    app: AvaApplication,
    onOpenSongDetails: (Long) -> Unit = {},
) {
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(
            app.container.musicRepository,
            app.container.playerRepository,
            app.container.userPreferences,
        ),
    )
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()

    // 0.7.0: results lag the typed query by the 200ms debounce plus the
    // Room query, so while the query has moved on but the visible results
    // still belong to the previous query, shimmer rows stand in.
    // `resultsQuery` tracks the query the visible results were computed
    // for: it follows every results emission, and an 800ms cap guarantees
    // the skeleton can never stick when two queries return the same
    // (empty) list and no emission arrives to clear it.
    var resultsQuery by remember { mutableStateOf("") }
    LaunchedEffect(results) { resultsQuery = query }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            resultsQuery = ""
        } else {
            delay(800)
            resultsQuery = query
        }
    }
    val isSearching = query.isNotBlank() && query != resultsQuery

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = AvaTokens.space20,
                top = AvaTokens.space16,
                bottom = AvaTokens.space12,
            ),
        )
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AvaTokens.space20),
        )
        Spacer(modifier = Modifier.height(AvaTokens.space12))
        when {
            query.isBlank() -> {
                if (recentSearches.isNotEmpty()) {
                    // Stage 6: recent searches (spec §7) — tap to re-run,
                    // × on a chip removes just that entry.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AvaTokens.space20),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.search_history_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                Text(stringResource(R.string.search_history_clear))
                            }
                        }
                        Spacer(modifier = Modifier.height(AvaTokens.space8))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AvaTokens.space8),
                            verticalArrangement = Arrangement.spacedBy(AvaTokens.space8),
                        ) {
                            recentSearches.forEach { past ->
                                InputChip(
                                    selected = false,
                                    onClick = { viewModel.onQueryChange(past) },
                                    label = { Text(past) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.removeSearchFromHistory(past) },
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = stringResource(
                                                    R.string.search_history_clear,
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = stringResource(R.string.search_empty_title),
                            body = stringResource(R.string.search_empty_body),
                            icon = Icons.Filled.Search,
                        )
                    }
                }
            }
            results.isEmpty() -> {
                // 0.7.0: stale results (debounce window) shimmer; a
                // settled empty result is a real "no matches" state.
                if (isSearching) {
                    SkeletonList()
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = stringResource(R.string.search_no_results_title),
                            body = stringResource(R.string.search_no_results_body, query.trim()),
                            icon = Icons.Filled.Search,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            onClick = { viewModel.play(song, results) },
                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                            playlists = playlists,
                            onAddToPlaylist = { playlistId ->
                                viewModel.addToPlaylist(song, playlistId)
                            },
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
}
