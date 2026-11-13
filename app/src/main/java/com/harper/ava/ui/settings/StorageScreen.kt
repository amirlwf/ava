package com.harper.ava.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.formatDuration
import com.harper.ava.core.util.formatFileSize
import com.harper.ava.ui.scan.ScanUiState
import com.harper.ava.ui.scan.rememberScanLauncher
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.INACTIVE_TRACK_ALPHA
import com.harper.ava.ui.theme.glassAwareScaffoldContainerColor
import com.harper.ava.ui.theme.glassAwareTopBarContainerColor

/**
 * Storage & library: real counts from Room (songs, total play time,
 * playlists, favorites), the real cover-cache size, a working rescan,
 * and a working cache clear that reports how much it freed.
 *
 * Music folders (stage 5): the user can pick folders with the Storage
 * Access Framework picker. Picked folders are listed with a real audio
 * file count; when the persisted permission is gone the row says so
 * plainly and offers a re-pick instead of crashing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(app: AvaApplication, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            app,
            app.container.userPreferences,
            app.container.musicRepository,
            app.container.playerRepository,
            app.container.safFolderScanner,
        ),
    )
    val stats by viewModel.storageStats.collectAsState()
    val cacheSize by viewModel.cacheSizeBytes.collectAsState()
    val cacheFreed by viewModel.cacheFreedBytes.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val musicFolders by viewModel.musicFolders.collectAsState()

    val requestScan = rememberScanLauncher(
        app = app,
        onScanStarted = viewModel::onScanStarted,
        onScanResult = viewModel::onScanResult,
    )
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) viewModel.pickMusicFolder(treeUri)
    }

    Scaffold(
        // Edge-to-edge (Harper 2026-11-13): this nested Scaffold owns no
        // system insets — the outer Scaffold already pads for the bottom
        // bar, and the TopAppBar owns the status bar. The default
        // contentWindowInsets (system bars) added a second navigation-bar
        // height of dead space at the bottom of this screen's content.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = glassAwareScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = glassAwareTopBarContainerColor()),
                title = { Text(stringResource(R.string.storage_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader(stringResource(R.string.storage_library_title))
            SettingsInfoRow(
                label = stringResource(R.string.storage_songs),
                value = stats.songCount.toString(),
            )
            SettingsInfoRow(
                label = stringResource(R.string.storage_duration),
                value = formatDuration(stats.totalDurationMs),
            )
            SettingsInfoRow(
                label = stringResource(R.string.storage_playlists),
                value = stats.playlistCount.toString(),
            )
            SettingsInfoRow(
                label = stringResource(R.string.storage_favorites),
                value = stats.favoriteCount.toString(),
            )

            SettingsSectionHeader(stringResource(R.string.storage_cache_title))
            SettingsInfoRow(
                label = stringResource(R.string.storage_cache_size),
                value = formatFileSize(cacheSize),
            )
            Column(modifier = Modifier.padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space8)) {
                OutlinedButton(onClick = { viewModel.clearCoverCache() }) {
                    Text(stringResource(R.string.storage_clear_cache))
                }
                if (cacheFreed != null) {
                    Spacer(modifier = Modifier.height(AvaTokens.space8))
                    Text(
                        text = stringResource(R.string.storage_cleared, formatFileSize(cacheFreed ?: 0L)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSectionHeader(stringResource(R.string.storage_source_title))
            SettingsNote(stringResource(R.string.storage_source_body))
            Column(modifier = Modifier.padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space8)) {
                Button(onClick = requestScan) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(AvaTokens.space8))
                    Text(stringResource(R.string.scan_rescan))
                }
                if (scanState is ScanUiState.Scanning) {
                    Spacer(modifier = Modifier.height(AvaTokens.space12))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        // 0.2-fix (Track 2): the M3 default track is
                        // surfaceVariant — invisible on pure black in
                        // AMOLED. An explicit onSurface track at
                        // INACTIVE_TRACK_ALPHA stays visible.
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_TRACK_ALPHA),
                    )
                }
                if (scanState is ScanUiState.Done) {
                    val done = scanState as ScanUiState.Done
                    Spacer(modifier = Modifier.height(AvaTokens.space8))
                    Text(
                        text = if (done.removedStale > 0) {
                            stringResource(R.string.scan_found_removed, done.found, done.removedStale)
                        } else {
                            stringResource(R.string.scan_found, done.found)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (scanState is ScanUiState.Error) {
                    Spacer(modifier = Modifier.height(AvaTokens.space8))
                    Text(
                        text = stringResource(R.string.scan_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            SettingsSectionHeader(stringResource(R.string.storage_folders_title))
            if (musicFolders.isEmpty()) {
                SettingsNote(stringResource(R.string.storage_folders_empty))
            } else {
                musicFolders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(AvaTokens.space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    folder.accessLost -> stringResource(
                                        R.string.storage_folders_lost,
                                    )
                                    folder.audioFileCount != null -> stringResource(
                                        R.string.storage_folders_audio_count,
                                        folder.audioFileCount,
                                    )
                                    // Stage 6: an unreadable folder is not a
                                    // lost permission — say so plainly.
                                    else -> stringResource(R.string.storage_folders_unreadable)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (folder.accessLost) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { viewModel.removeMusicFolder(folder.treeUri) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(
                                    R.string.storage_folders_remove,
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space8)) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(AvaTokens.space8))
                    Text(stringResource(R.string.storage_folders_pick))
                }
            }
            SettingsNote(stringResource(R.string.storage_folders_note))
            SettingsSpacer()
        }
    }
}
