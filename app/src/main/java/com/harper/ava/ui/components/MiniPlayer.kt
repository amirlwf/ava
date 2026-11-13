package com.harper.ava.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.harper.ava.R
import com.harper.ava.core.util.rememberAvaHaptics
import com.harper.ava.data.repository.PlaybackState
import com.harper.ava.ui.common.AvaMotion
import com.harper.ava.ui.common.rememberPressScale
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.glassPanel
import kotlinx.coroutines.flow.StateFlow

/**
 * Mini player shown above the bottom bar whenever something is loaded in the
 * player. Play/pause and next are fully wired to the PlayerRepository, and
 * tapping the song info opens the full Now Playing screen.
 *
 * 0.6.0 design round: the old full-width flat strip is now a floating
 * pill — rounded (28dp), softly lifted (8dp shadow), with breathing
 * room around it. The cover is bigger (52dp) with a soft 16dp radius,
 * the title is SemiBold over a quieter artist line, and play/pause is
 * a filled primary circle (48dp) so the one control people reach for
 * is unmistakable. The progress line is inset, thin and round-ended.
 * Nothing was removed: same callbacks, same tap-to-open, same
 * cross-fade on track change.
 *
 * 0.7.0 motion: the pill springs up from 28dp below with a fade the
 * first time it appears (real spring, [AvaMotion.miniPlayerEnterSpring]),
 * the play button squashes to 0.88x on a spring while pressed, and
 * play/pause + next buzz through [rememberAvaHaptics].
 *
 * Run 8 (performance): the progress line reads the live position from
 * its own flow ([positionMs]) inside [MiniPlayerProgress], so the 500 ms
 * tick recomposes only that thin line — the pill itself only recomposes
 * on real playback changes (play/pause, track change).
 */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    positionMs: StateFlow<Long>,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit = {},
) {
    val song = state.currentSong ?: return
    var artFailed by remember(song.albumArtUri) { mutableStateOf(false) }
    val haptics = rememberAvaHaptics()
    // Spring entrance: the pill rises into place whenever it (re)appears
    // — e.g. the first play after launch — instead of popping in.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterOffset by animateDpAsState(
        targetValue = if (entered) 0.dp else 28.dp,
        animationSpec = AvaMotion.miniPlayerEnterSpring,
        label = "mini-player-enter-offset",
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = AvaMotion.miniPlayerEnterFade,
        label = "mini-player-enter-alpha",
    )
    // Press squash on the hero play button: 0.88x while held, springing
    // back on release.
    val playInteraction = remember { MutableInteractionSource() }
    val playScale = rememberPressScale(playInteraction, pressedScale = 0.88f)
    val pillShape = RoundedCornerShape(AvaTokens.radiusExtraLarge)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = enterOffset)
            .graphicsLayer { alpha = enterAlpha }
            // Floating pill: air around it, a soft shadow, no hard edge.
            .padding(
                horizontal = AvaTokens.space12,
                vertical = AvaTokens.space8,
            )
            .shadow(AvaTokens.elevationSoft, pillShape)
            .clip(pillShape)
            // 0.1: on the glass theme this is a frosted pill (translucent
            // + hairline border); on every other theme it is exactly the
            // same solid surface as before.
            .glassPanel(pillShape, MaterialTheme.colorScheme.surfaceContainer)
            .padding(
                start = AvaTokens.space12,
                end = AvaTokens.space6,
                top = AvaTokens.space8,
                bottom = AvaTokens.space8,
            ),
    ) {
        // Stage 6: the whole row opens Now Playing (the cover used to be a
        // dead tap target), and the song info cross-fades on track change
        // instead of swapping instantly. A blank artist now shows the
        // localized "Unknown artist" instead of an empty line.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = { haptics.tap(); onOpenNowPlaying() }),
        ) {
            AnimatedContent(
                targetState = song,
                transitionSpec = { fadeIn(AvaMotion.crossFade) togetherWith fadeOut(AvaMotion.crossFade) },
                label = "mini-player-song",
            ) { current ->
                Box(
                    modifier = Modifier
                        .size(AvaTokens.coverSmall)
                        .clip(RoundedCornerShape(AvaTokens.radiusMedium))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current.albumArtUri != null && !artFailed) {
                        AsyncImage(
                            model = current.albumArtUri,
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
            }
            Spacer(modifier = Modifier.width(AvaTokens.space12))
            AnimatedContent(
                targetState = song,
                transitionSpec = { fadeIn(AvaMotion.crossFade) togetherWith fadeOut(AvaMotion.crossFade) },
                label = "mini-player-titles",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(verticalArrangement = Arrangement.spacedBy(AvaTokens.space2)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
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
            // The hero control: a filled primary circle, big enough to
            // hit without looking. Next stays a quiet ghost button.
            IconButton(
                onClick = { haptics.playPause(); onTogglePlayPause() },
                interactionSource = playInteraction,
                modifier = Modifier
                    .size(AvaTokens.miniPlayerPlaySize)
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                // 0.1: the play/pause icon cross-fades instead of swapping
                // instantly — a small, soft motion on the control people
                // tap most.
                AnimatedContent(
                    targetState = state.isPlaying,
                    transitionSpec = { fadeIn(AvaMotion.crossFade) togetherWith fadeOut(AvaMotion.crossFade) },
                    label = "mini-player-play-icon",
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) R.string.player_pause else R.string.player_play,
                        ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(AvaTokens.space4))
            IconButton(
                onClick = { haptics.tap(); onNext() },
                modifier = Modifier.size(AvaTokens.touchTargetMin),
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(AvaTokens.space8))
        // Inset, thin, round-ended progress — a quiet line, not a bar
        // slammed against the pill's edge.
        MiniPlayerProgress(
            positionMs = positionMs,
            durationMs = state.durationMs,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AvaTokens.space4),
        )
    }
}

/**
 * Run 8 (performance): the progress line alone reads the 500 ms
 * position tick, so the tick recomposes only this thin line — the pill
 * itself (cover, titles, buttons) only recomposes on real playback
 * changes. Same look as before: inset, thin, round-ended.
 */
@Composable
private fun MiniPlayerProgress(
    positionMs: StateFlow<Long>,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val position by positionMs.collectAsState()
    val progress = if (durationMs > 0) {
        (position.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(AvaTokens.radiusFull)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    )
}
