package com.harper.ava.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.harper.ava.R
import com.harper.ava.core.util.formatDuration
import com.harper.ava.core.util.rememberAvaHaptics
import com.harper.ava.domain.model.Playlist
import com.harper.ava.domain.model.Song
import com.harper.ava.ui.common.AvaMotion
import com.harper.ava.ui.navigation.LocalAvaAnimatedVisibilityScope
import com.harper.ava.ui.navigation.LocalAvaSharedTransitionScope
import com.harper.ava.ui.navigation.songCoverSharedKey
import com.harper.ava.ui.theme.AvaTokens

/**
 * One library song row: cover (or a tonal placeholder), title, artist •
 * duration, and a working favorite toggle. Tapping the row starts playback.
 *
 * The overflow menu is only shown when the caller wires the queue /
 * playlist actions — a row never shows a menu that does nothing. Cover
 * art that fails to load falls back to the music-note placeholder instead
 * of a blank square.
 *
 * 0.7.0 motion: the cover is a shared element — tapping a row flies it
 * into the Now Playing cover ([songCoverSharedKey]) whenever the row
 * lives in a destination that provides the shared-transition locals
 * (Home/Library/Search/Favorites); elsewhere the row is unchanged. The
 * heart pops on a spring and buzzes when tapped.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    playlists: List<Playlist>? = null,
    onAddToPlaylist: ((playlistId: Long) -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var artFailed by remember(song.albumArtUri) { mutableStateOf(false) }
    val showMenu = playlists != null || onAddToQueue != null || onPlayNext != null ||
        onOpenDetails != null
    val artist = song.artist.ifBlank { stringResource(R.string.unknown_artist) }
    val haptics = rememberAvaHaptics()

    // Shared-element cover: only when the enclosing destination provides
    // the transition scopes (Home/Library/Search/Favorites via the nav
    // graph). Anywhere else the locals are null and the row is plain.
    val sharedScope = LocalAvaSharedTransitionScope.current
    val visibilityScope = LocalAvaAnimatedVisibilityScope.current
    val coverModifier = if (sharedScope != null && visibilityScope != null) {
        with(sharedScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = songCoverSharedKey(song.id)),
                animatedVisibilityScope = visibilityScope,
                boundsTransform = AvaMotion.coverBoundsTransform,
            )
        }
    } else {
        Modifier
    }

    // Heart pop: a spring overshoot on every favorite tap, driven by a
    // counter so it replays even when the state toggles back and forth.
    val heartScale = remember { Animatable(1f) }
    var heartPop by remember { mutableIntStateOf(0) }
    LaunchedEffect(heartPop) {
        if (heartPop > 0) {
            heartScale.animateTo(1.3f, AvaMotion.pressSpring)
            heartScale.animateTo(1f, AvaMotion.pressSpring)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AvaTokens.songRowHeight)
            .clip(RoundedCornerShape(AvaTokens.radiusMedium))
            .clickable(onClick = onClick)
            .padding(horizontal = AvaTokens.space12, vertical = AvaTokens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvaTokens.coverSmall)
                .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                .then(coverModifier)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (song.albumArtUri != null && !artFailed) {
                AsyncImage(
                    model = song.albumArtUri,
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
        // 0.6.0: rounder cover (16dp via the token), a breath between
        // title and artist • duration, and a slightly taller row (68dp)
        // so the 52dp cover is not squeezed. Same taps, same menu.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AvaTokens.space2),
        ) {
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
        IconButton(onClick = { haptics.like(); heartPop++; onToggleFavorite() }) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(
                    if (song.isFavorite) R.string.player_favorite_remove else R.string.player_favorite_add,
                ),
                tint = if (song.isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    scaleX = heartScale.value
                    scaleY = heartScale.value
                },
            )
        }
        if (showMenu) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.song_more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.song_play_next)) },
                            onClick = { onPlayNext(); menuOpen = false },
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.song_add_to_queue)) },
                            onClick = { onAddToQueue(); menuOpen = false },
                        )
                    }
                    if (onOpenDetails != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.song_details)) },
                            onClick = { onOpenDetails(); menuOpen = false },
                        )
                    }
                    if (playlists != null && onAddToPlaylist != null) {
                        if (playlists.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.song_no_playlists)) },
                                onClick = { menuOpen = false },
                                enabled = false,
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.song_add_to_playlist)) },
                                onClick = { menuOpen = false },
                                enabled = false,
                            )
                            playlists.forEach { playlist ->
                                DropdownMenuItem(
                                    text = { Text(playlist.name) },
                                    onClick = { onAddToPlaylist(playlist.id); menuOpen = false },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (onRemoveFromPlaylist != null) {
            IconButton(onClick = onRemoveFromPlaylist) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.playlist_remove_song),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
