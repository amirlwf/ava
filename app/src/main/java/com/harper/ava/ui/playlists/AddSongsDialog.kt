package com.harper.ava.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harper.ava.R
import com.harper.ava.domain.model.Song
import com.harper.ava.ui.scan.ScanUiState
import com.harper.ava.ui.scan.openAppSettings
import com.harper.ava.ui.theme.AvaTokens

/**
 * 0.7.4: multi-select song picker for adding songs from inside a
 * playlist — the gap Harper reported ("add songs from the playlist
 * section"). Before this, adding was only possible from a song's own
 * menu; a playlist itself offered no way in.
 *
 * The picker lists the whole library (title order) with a search box
 * (title / artist / album, Arabic ي/ك normalized to Persian ی/ک).
 * Songs already in the playlist show checked + disabled with an
 * "already in playlist" label, so they can never be re-added. The
 * confirm button carries the live selection count and stays disabled
 * until at least one new song is picked.
 */
@Composable
fun AddSongsToPlaylistDialog(
    playlistName: String,
    /**
     * Run 43 (perf): null until the library flow's first emission —
     * the picker collects the flow only while it is open, so the
     * first frame has no list yet. Null shows a loading line; a
     * non-null empty list is the real empty library (scan offered).
     */
    allSongs: List<Song>?,
    alreadyAddedIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
    /**
     * Run 31 (UX): the scan state + trigger for the empty-library
     * branch. Null (the default) keeps the old text-only state for
     * callers that cannot scan; both real callers pass the shared
     * launcher, so the picker can scan from inside the dialog.
     */
    scanState: ScanUiState = ScanUiState.Idle,
    onScanLibrary: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    val normalizedQuery = remember(query) { normalizeForSearch(query) }
    val visibleSongs = remember(allSongs, normalizedQuery) {
        val songs = allSongs ?: emptyList()
        if (normalizedQuery.isBlank()) {
            songs
        } else {
            songs.filter { song ->
                normalizeForSearch(song.title).contains(normalizedQuery) ||
                    normalizeForSearch(song.artist).contains(normalizedQuery) ||
                    normalizeForSearch(song.album).contains(normalizedQuery)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_add_songs_title, playlistName)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text(stringResource(R.string.playlist_add_songs_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.search_history_clear),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(AvaTokens.space12))
                when {
                    allSongs == null -> {
                        // Run 43 (perf): the library is still loading
                        // — a plain line, never the empty-library
                        // state with its scan button.
                        Text(
                            text = stringResource(R.string.playlist_add_songs_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    allSongs.isEmpty() -> {
                        // Run 31 (UX): the picker used to say "scan your
                        // music first" with no way to do it from here —
                        // Cancel was the only exit. The scan action now
                        // lives under the words that ask for it, following
                        // Home's empty-state pattern; the picker fills up
                        // on its own once the scan lands (allSongs is a
                        // Room flow), so the dialog never needs to close.
                        val context = LocalContext.current
                        Column {
                            Text(
                                text = stringResource(R.string.playlist_add_songs_empty_library),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (onScanLibrary != null) {
                                Spacer(modifier = Modifier.height(AvaTokens.space12))
                                when (scanState) {
                                    ScanUiState.Scanning -> {
                                        Text(
                                            text = stringResource(R.string.scan_scanning),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    ScanUiState.Error -> {
                                        Text(
                                            text = stringResource(R.string.scan_error),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(modifier = Modifier.height(AvaTokens.space12))
                                        Button(onClick = onScanLibrary) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(AvaTokens.space8))
                                            Text(stringResource(R.string.scan_rescan))
                                        }
                                    }
                                    ScanUiState.PermissionDenied -> {
                                        Text(
                                            text = stringResource(R.string.scan_permission_denied),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.height(AvaTokens.space12))
                                        OutlinedButton(onClick = { openAppSettings(context) }) {
                                            Text(stringResource(R.string.scan_permission_settings))
                                        }
                                    }
                                    else -> {
                                        Button(onClick = onScanLibrary) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(AvaTokens.space8))
                                            Text(stringResource(R.string.scan_rescan))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    visibleSongs.isEmpty() -> {
                        Text(
                            text = stringResource(
                                R.string.playlist_add_songs_no_results,
                                query.trim(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                        ) {
                            items(visibleSongs, key = { it.id }) { song ->
                                val alreadyAdded = song.id in alreadyAddedIds
                                val selected = song.id in selectedIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !alreadyAdded) {
                                            selectedIds = if (selected) {
                                                selectedIds - song.id
                                            } else {
                                                selectedIds + song.id
                                            }
                                        }
                                        .padding(vertical = AvaTokens.space4),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = selected || alreadyAdded,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) {
                                                selectedIds + song.id
                                            } else {
                                                selectedIds - song.id
                                            }
                                        },
                                        enabled = !alreadyAdded,
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = AvaTokens.space8),
                                    ) {
                                        Text(
                                            text = song.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = song.artist.ifBlank {
                                                stringResource(R.string.unknown_artist)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (alreadyAdded) {
                                        Text(
                                            text = stringResource(
                                                R.string.playlist_add_songs_already_added,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                start = AvaTokens.space8,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIds.isNotEmpty(),
                onClick = { onConfirm(selectedIds.toList()) },
            ) {
                Text(stringResource(R.string.playlist_add_songs_confirm, selectedIds.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.playlists_cancel))
            }
        },
    )
}

/** Lowercase + Arabic ي/ك folded to Persian ی/ک, so Persian search matches either spelling. */
private fun normalizeForSearch(value: String): String =
    value.trim().lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
