package com.harper.ava.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.effectiveAlbumArtUri
import com.harper.ava.core.util.formatDuration
import com.harper.ava.core.util.formatFileSize
import com.harper.ava.data.media.formatOf
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.settings.SettingsInfoRow
import com.harper.ava.ui.settings.SettingsSectionHeader
import com.harper.ava.ui.settings.SettingsSpacer
import com.harper.ava.ui.theme.AvaTokens

/**
 * Song Details: file name, format, size, duration, bitrate and sample
 * rate when they can be extracted, plus the stored metadata (artist,
 * album, genre, year, track, folder). Anything unknowable is shown as
 * the localized "unknown" — never a made-up value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsScreen(app: AvaApplication, songId: Long, onBack: () -> Unit) {
    val viewModel: SongDetailsViewModel = viewModel(
        factory = SongDetailsViewModel.factory(app, songId, app.container.musicRepository),
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        // Edge-to-edge (Harper 2026-11-13): this nested Scaffold owns no
        // system insets — the outer Scaffold already pads for the bottom
        // bar, and the TopAppBar owns the status bar. The default
        // contentWindowInsets (system bars) added a second navigation-bar
        // height of dead space at the bottom of this screen's content.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            SongDetailsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            SongDetailsUiState.NotFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = stringResource(R.string.details_not_found_title),
                        body = stringResource(R.string.details_not_found_body),
                    )
                }
            }
            is SongDetailsUiState.Loaded -> {
                val song = current.song
                val info = current.fileInfo
                val unknown = stringResource(R.string.details_unknown)
                // Frontend + notification round (2026-11-14): same
                // 0.3-feedback fix as GroupRow — a stored broken
                // albums/ uri reads as "no art"
                // ([effectiveAlbumArtUri]) instead of attempting a
                // load that cannot succeed.
                val effectiveArtUri = effectiveAlbumArtUri(song.albumArtUri)
                var artFailed by remember(effectiveArtUri) { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(AvaTokens.space16))
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(AvaTokens.radiusMedium))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (effectiveArtUri != null && !artFailed) {
                            AsyncImage(
                                model = effectiveArtUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                onError = { artFailed = true },
                                modifier = Modifier.size(96.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AvaTokens.space12))
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AvaTokens.space24),
                    )
                    Text(
                        text = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AvaTokens.space24),
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsSectionHeader(stringResource(R.string.details_file_title))
                        SettingsInfoRow(
                            label = stringResource(R.string.details_file_name),
                            value = info.fileName ?: unknown,
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.details_format),
                            value = formatOf(info.mimeType, info.fileName) ?: unknown,
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.details_size),
                            value = info.sizeBytes?.let { formatFileSize(it) } ?: unknown,
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.details_duration),
                            value = formatDuration(song.durationMs),
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.details_bitrate),
                            value = info.bitrateKbps?.let { "$it kbps" } ?: unknown,
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.details_sample_rate),
                            value = info.sampleRateHz?.let { hz ->
                                "%.1f kHz".format(hz / 1000f)
                            } ?: unknown,
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.details_folder),
                            value = info.folder ?: unknown,
                        )

                        SettingsSectionHeader(stringResource(R.string.details_metadata_title))
                        SettingsInfoRow(
                            label = stringResource(R.string.details_album),
                            value = song.album.ifBlank { unknown },
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.details_genre),
                            value = song.genre?.ifBlank { unknown } ?: unknown,
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.details_year),
                            value = song.year?.toString() ?: unknown,
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.details_track),
                            value = song.trackNumber?.toString() ?: unknown,
                        )
                    }
                    SettingsSpacer()
                }
            }
        }
    }
}
