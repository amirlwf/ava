package com.harper.ava.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import com.harper.ava.R
import com.harper.ava.data.repository.PlaybackState
import com.harper.ava.ui.theme.AvaTokens

/**
 * Mini player shown above the bottom bar whenever something is loaded in the
 * player. Play/pause and next are fully wired to the PlayerRepository, and
 * tapping the song info opens the full Now Playing screen.
 */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit = {},
) {
    val song = state.currentSong ?: return
    var artFailed by remember(song.albumArtUri) { mutableStateOf(false) }
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(
                start = AvaTokens.space12,
                end = AvaTokens.space8,
                top = AvaTokens.space8,
            ),
    ) {
        // Stage 6: the whole row opens Now Playing (the cover used to be a
        // dead tap target), and the song info cross-fades on track change
        // instead of swapping instantly. A blank artist now shows the
        // localized "Unknown artist" instead of an empty line.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onOpenNowPlaying),
        ) {
            AnimatedContent(
                targetState = song,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "mini-player-song",
            ) { current ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current.albumArtUri != null && !artFailed) {
                        AsyncImage(
                            model = current.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onError = { artFailed = true },
                            modifier = Modifier.size(44.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(AvaTokens.space12))
            AnimatedContent(
                targetState = song,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "mini-player-titles",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = current.artist.ifBlank {
                            stringResource(R.string.unknown_artist)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (state.isPlaying) R.string.player_pause else R.string.player_play,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(modifier = Modifier.height(AvaTokens.space8))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )
    }
}
