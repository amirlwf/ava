package com.harper.ava.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.dragTargetIndex
import com.harper.ava.core.util.effectiveAlbumArtUri
import com.harper.ava.core.util.rememberAvaHaptics
import com.harper.ava.core.util.formatDuration
import com.harper.ava.core.util.queueRowKey
import com.harper.ava.domain.model.Song
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.common.AvaMotion

/**
 * The playback queue: the current song on top, the upcoming songs below.
 *
 * Reordering is real drag & drop: long-press the handle and drag. The drop
 * target is computed from the drag distance divided by the fixed row
 * height ([dragTargetIndex] — unit-tested pure logic), then applied with
 * [com.harper.ava.data.repository.PlayerRepository.moveQueueItem], so the
 * player and the list stay in sync. This has not been verified on a real
 * device yet (no device run is possible in this sandbox).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    app: AvaApplication,
    onBack: () -> Unit,
    // 0.1: the now-playing row used to be a dead tap — it now opens
    // Now Playing, like every other now-playing surface in the app.
    onOpenNowPlaying: () -> Unit = {},
    // Run 11 (UX): the empty state tells the user to play a song
    // but offers no way there — Queue is full-screen (no bottom bar),
    // so this opens the Library root.
    onBrowseLibrary: () -> Unit = {},
) {
    val viewModel: QueueViewModel = viewModel(
        factory = QueueViewModel.factory(app.container.playerRepository),
    )
    val state by viewModel.playback.collectAsState()
    val queue = state.queue
    val currentIndex = state.queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    val currentSong = state.currentSong
    val upcoming = if (queue.isEmpty()) emptyList() else queue.drop(currentIndex + 1)

    var draggedPos by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var lastTickTarget by remember { mutableStateOf<Int?>(null) }
    val haptics = rememberAvaHaptics()
    val rowHeightPx = with(LocalDensity.current) { AvaTokens.queueRowHeight.toPx() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.queue_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
            },
            actions = {
                if (upcoming.isNotEmpty()) {
                    // Icon buttons (not text buttons): the TopAppBar must not
                    // overflow on narrow screens in either language.
                    IconButton(onClick = { viewModel.shuffleQueue() }) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = stringResource(R.string.queue_shuffle),
                        )
                    }
                    IconButton(onClick = { viewModel.clearQueue() }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.queue_clear),
                        )
                    }
                }
            },
        )

        if (queue.isEmpty() || currentSong == null) {
            // Run 11 (UX): the only way out used to be the back arrow —
            // the action is now right here, next to the words that ask
            // for it (same button-under-empty-state pattern as run 6).
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.queue_empty_title),
                    body = stringResource(R.string.queue_empty_body),
                )
                Spacer(modifier = Modifier.height(AvaTokens.space16))
                Button(onClick = onBrowseLibrary) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                    Spacer(modifier = Modifier.width(AvaTokens.space8))
                    Text(stringResource(R.string.empty_go_to_library))
                }
            }
            return
        }

        Text(
            text = stringResource(R.string.queue_now_playing),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = AvaTokens.space20,
                top = AvaTokens.space8,
                bottom = AvaTokens.space4,
            ),
        )
        QueueRow(
            song = currentSong,
            isCurrent = true,
            onClick = onOpenNowPlaying,
            onRemove = null,
            modifier = Modifier.padding(horizontal = AvaTokens.space8),
        )

        if (upcoming.isNotEmpty()) {
            Text(
                text = stringResource(R.string.queue_up_next),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    start = AvaTokens.space20,
                    top = AvaTokens.space16,
                    bottom = AvaTokens.space4,
                ),
            )
            Text(
                text = stringResource(R.string.queue_drag_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AvaTokens.space20, bottom = AvaTokens.space8),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // 0.5.6: the key includes the position — the same song may
                // legitimately appear twice in a queue (add-to-queue /
                // play-next / restored checkpoint) and a bare id key would
                // crash with a duplicate key, exactly like Home in 0.5.5.
                itemsIndexed(upcoming, key = { pos, song -> queueRowKey(pos, song.id) }) { pos, song ->
                    val absoluteIndex = currentIndex + 1 + pos
                    val isDragged = draggedPos == pos
                    QueueRow(
                        song = song,
                        isCurrent = false,
                        onClick = { viewModel.playAt(absoluteIndex) },
                        onRemove = { viewModel.removeAt(absoluteIndex) },
                        modifier = Modifier
                            .padding(horizontal = AvaTokens.space8)
                            .animateItem(placementSpec = AvaMotion.listItemPlacement)
                            .graphicsLayer {
                                translationY = if (isDragged) dragOffsetY else 0f
                                shadowElevation = if (isDragged) 8f else 0f
                            },
                        dragHandle = {
                            Box(
                                modifier = Modifier.pointerInput(pos) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedPos = pos
                                            dragOffsetY = 0f
                                            lastTickTarget = pos
                                        },
                                        onDragEnd = {
                                            val targetPos = dragTargetIndex(
                                                pos,
                                                dragOffsetY,
                                                rowHeightPx,
                                                upcoming.size,
                                            )
                                            if (targetPos != pos) {
                                                viewModel.moveItem(
                                                    absoluteIndex,
                                                    currentIndex + 1 + targetPos,
                                                )
                                            }
                                            draggedPos = null
                                            dragOffsetY = 0f
                                            lastTickTarget = null
                                        },
                                        onDragCancel = {
                                            draggedPos = null
                                            dragOffsetY = 0f
                                            lastTickTarget = null
                                        },
                                        onDrag = { _, dragAmount ->
                                            dragOffsetY += dragAmount.y
                                            val target = dragTargetIndex(
                                                pos,
                                                dragOffsetY,
                                                rowHeightPx,
                                                upcoming.size,
                                            )
                                            if (target != lastTickTarget) {
                                                lastTickTarget = target
                                                haptics.dragTick()
                                            }
                                        },
                                    )
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
                item { Spacer(modifier = Modifier.height(AvaTokens.space16)) }
            }
        }
    }
}

@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    // Frontend + notification round (2026-11-14): same 0.3-feedback
    // fix as GroupRow — a stored broken albums/ uri reads as "no
    // art" ([effectiveAlbumArtUri]) instead of attempting a load
    // that cannot succeed.
    val effectiveArtUri = effectiveAlbumArtUri(song.albumArtUri)
    var artFailed by remember(effectiveArtUri) { mutableStateOf(false) }
    val artist = song.artist.ifBlank { stringResource(R.string.unknown_artist) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AvaTokens.queueRowHeight)
            .clip(RoundedCornerShape(AvaTokens.radiusMedium))
            .then(
                if (isCurrent) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AvaTokens.space12, vertical = AvaTokens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvaTokens.coverSmall)
                .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (effectiveArtUri != null && !artFailed) {
                AsyncImage(
                    model = effectiveArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { artFailed = true },
                    modifier = Modifier.size(AvaTokens.coverSmall),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(AvaTokens.space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$artist • ${formatDuration(song.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.Equalizer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (dragHandle != null) {
            dragHandle()
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.queue_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
