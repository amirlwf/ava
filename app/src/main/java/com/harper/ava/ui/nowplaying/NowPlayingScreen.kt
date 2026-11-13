package com.harper.ava.ui.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import coil3.compose.AsyncImage
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.formatDuration
import com.harper.ava.core.util.rememberAvaHaptics
import com.harper.ava.core.util.shouldDismissOnRelease
import com.harper.ava.core.util.swipeDismissThresholdPx
import com.harper.ava.data.repository.RepeatMode
import com.harper.ava.ui.theme.rememberIsResumed
import com.harper.ava.ui.theme.LocalAppThemeSpec
import com.harper.ava.ui.common.AvaMotion
import com.harper.ava.ui.common.rememberPressScale
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.navigation.LocalAvaAnimatedVisibilityScope
import com.harper.ava.ui.navigation.LocalAvaSharedTransitionScope
import com.harper.ava.ui.navigation.songCoverSharedKey
import com.harper.ava.ui.theme.AvaTokens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    app: AvaApplication,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenLyrics: () -> Unit = {},
    onOpenSongDetails: (Long) -> Unit = {},
    onOpenRingtoneEditor: (Long) -> Unit = {},
) {
    val viewModel: NowPlayingViewModel = viewModel(
        factory = NowPlayingViewModel.factory(
            app.container.musicRepository,
            app.container.playerRepository,
        ),
    )
    val state by viewModel.playback.collectAsState()
    val isFavorite by viewModel.isCurrentFavorite.collectAsState()
    val sleepTimer by viewModel.sleepTimer.collectAsState()
    var moreMenuOpen by remember { mutableStateOf(false) }
    val song = state.currentSong

    if (song == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.now_playing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Stage 6: this used the queue's empty copy — wrong screen, wrong text.
                EmptyState(
                    title = stringResource(R.string.now_playing_empty_title),
                    body = stringResource(R.string.now_playing_empty_body),
                )
            }
        }
        return
    }

    var coverFailed by remember(song.albumArtUri) { mutableStateOf(false) }
    val haptics = rememberAvaHaptics()

    // 0.7.0 motion: the big cover is the other end of the shared element
    // that starts in the list row ([songCoverSharedKey]) — the same key,
    // so a tapped row's cover flies here and back. The AnimatedContent
    // itself carries the modifier (its bounds ARE the cover bounds).
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

    // Hero play button: spring squash while pressed, like the mini player.
    val playInteraction = remember { MutableInteractionSource() }
    val playScale = rememberPressScale(playInteraction, pressedScale = 0.88f)

    // Heart pop on every favorite tap (same spring as the rows).
    val heartScale = remember { Animatable(1f) }
    var heartPop by remember { mutableIntStateOf(0) }
    LaunchedEffect(heartPop) {
        if (heartPop > 0) {
            heartScale.animateTo(1.2f, AvaMotion.pressSpring)
            heartScale.animateTo(1f, AvaMotion.pressSpring)
        }
    }
    val speed = state.playbackSpeed
    val speedLabel = if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"
    val artist = song.artist.ifBlank { stringResource(R.string.unknown_artist) }

    // Stage 6: the cover was a fixed 300dp, but the content column is only
    // 272dp wide on a 320dp screen (24dp padding each side) — the cover
    // overflowed. It now adapts to the available width/height.
    // 0.1: the glass theme frosts this screen (stronger blur + a
    // translucent veil); the ambient glow below is gated by the same
    // resumed flag as the seasonal decor, so a hidden Now Playing
    // screen costs no animation frames.
    val glass = LocalAppThemeSpec.current.isGlass
    val isResumed = rememberIsResumed()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val coverSize = minOf(
            AvaTokens.nowPlayingCoverSize,
            maxWidth - AvaTokens.space48,
            maxHeight * 0.45f,
        )
        // Adaptive background: the cover, heavily blurred, under a scrim.
        // Falls back to a plain accent-tinted gradient when there is no art.
        if (song.albumArtUri != null && !coverFailed) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { coverFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (glass) 96.dp else 64.dp)
                    .graphicsLayer { alpha = 0.55f },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )
        // 0.1: on the glass theme, a translucent surface veil over the
        // blur is what makes it read as frosted glass instead of just a
        // blurry photo; on other themes the scrim above is enough.
        if (glass) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)),
            )
        }
        // 0.1: the ambient glow — slow drifting colour from the theme
        // palette, breathing a little harder while the song plays.
        // Ambient only: it never pretends to follow the music.
        AmbientGlow(
            isPlaying = state.isPlaying,
            active = isResumed,
            modifier = Modifier.fillMaxSize(),
        )

        // 0.1-fix1 (Track F): swipe down to dismiss. The content Column
        // follows the finger (a vertical `draggable`); past a quarter
        // of the screen height — or on a fast downward fling — it
        // slides off and pops the back stack (playback keeps going,
        // the mini player stays). Below the threshold it springs back
        // on the shared soft spring. The blurred-art backdrop and the
        // glow stay put: only the content moves, so the backdrop never
        // flashes (Track B). The screen does not scroll (lyrics live
        // on their own screen), so the drag never fights scrolling;
        // the seek slider is horizontal, so it never fights this
        // vertical gesture either. Taps still work: `draggable` only
        // consumes movement, not taps.
        val sheetHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val dismissOffset = remember { Animatable(0f) }
        val dismissScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dismissOffset.value.roundToInt()) }
                .graphicsLayer {
                    // Fade gently as the sheet leaves — 1.0 at rest,
                    // 0.6 at the bottom edge, never fully invisible
                    // mid-drag (the slide-off finishes the job).
                    alpha = 1f - (dismissOffset.value / sheetHeightPx) * 0.4f
                }
                .draggable(
                    state = rememberDraggableState { delta ->
                        dismissScope.launch {
                            dismissOffset.snapTo(
                                (dismissOffset.value + delta).coerceAtLeast(0f),
                            )
                        }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        dismissScope.launch {
                            if (shouldDismissOnRelease(
                                    dismissOffset.value,
                                    velocity,
                                    swipeDismissThresholdPx(sheetHeightPx),
                                )
                            ) {
                                dismissOffset.animateTo(
                                    sheetHeightPx,
                                    tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                )
                                onBack()
                            } else {
                                dismissOffset.animateTo(0f, AvaMotion.coverScaleSpring)
                            }
                        }
                    },
                )
                .padding(horizontal = AvaTokens.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.now_playing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = stringResource(R.string.player_queue),
                        )
                    }
                    // Advanced features (stage 5): lyrics, equalizer, sleep
                    // timer and song details — all real screens, no dead ends.
                    Box {
                        IconButton(onClick = { moreMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.song_more_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = moreMenuOpen,
                            onDismissRequest = { moreMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_lyrics)) },
                                onClick = { moreMenuOpen = false; onOpenLyrics() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_equalizer)) },
                                onClick = { moreMenuOpen = false; onOpenEqualizer() },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (sleepTimer.active) {
                                            stringResource(
                                                R.string.player_sleep_timer_active,
                                                formatDuration(sleepTimer.remainingMs),
                                            )
                                        } else {
                                            stringResource(R.string.player_sleep_timer)
                                        },
                                    )
                                },
                                onClick = { moreMenuOpen = false; onOpenSleepTimer() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_song_details)) },
                                onClick = {
                                    moreMenuOpen = false
                                    onOpenSongDetails(song.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.song_set_as_ringtone)) },
                                onClick = {
                                    moreMenuOpen = false
                                    onOpenRingtoneEditor(song.id)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            Spacer(modifier = Modifier.height(AvaTokens.space20))

            // Cover + titles animate softly when the track changes.
            AnimatedContent(
                targetState = song,
                transitionSpec = { (fadeIn(AvaMotion.crossFade) + scaleIn(initialScale = 0.96f, animationSpec = AvaMotion.coverScaleSpring)) togetherWith fadeOut(AvaMotion.crossFade) },
                label = "now-playing-cover",
                modifier = coverModifier,
            ) { current ->
                // Stage 6: a soft shadow lifts the cover off the blurred
                // background (premium depth instead of a flat tile).
                // 0.6.0: the cover is rounder (28dp via the token) and the
                // lift is the shared soft elevation, not a hard 16dp drop.
                Box(
                    modifier = Modifier
                        .size(coverSize)
                        .shadow(AvaTokens.elevationLifted, RoundedCornerShape(AvaTokens.radiusExtraLarge))
                        .clip(RoundedCornerShape(AvaTokens.radiusExtraLarge))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current.albumArtUri != null && !coverFailed) {
                        AsyncImage(
                            model = current.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onError = { coverFailed = true },
                            modifier = Modifier.size(coverSize),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(72.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AvaTokens.space24))
            // 0.6.0: clear hierarchy — headline title, medium artist,
            // small quiet album — with a breath between the lines instead
            // of three same-weight lines stacked tight.
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AvaTokens.space4))
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (song.album.isNotBlank()) {
                Spacer(modifier = Modifier.height(AvaTokens.space2))
                Text(
                    text = song.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            // Run 8: the slider + times live in their own composable, so
            // the 500 ms position tick recomposes only this small section —
            // not the whole screen (blurred cover, glow, decor, titles).
            NowPlayingProgress(
                positionMs = viewModel.positionMs,
                durationMs = state.durationMs,
                songId = song.id,
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AvaTokens.space8))

            // 0.6.0: skip buttons sit on soft tonal discs (48dp) so the
            // row reads as three friendly pills around one hero button,
            // not five bare icons floating in space. Shuffle/repeat stay
            // bare — their color already says whether they are on.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { haptics.toggle(); viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { haptics.tap(); viewModel.playPrevious() },
                    modifier = Modifier
                        .size(AvaTokens.touchTargetMin)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.player_previous),
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(
                    onClick = { haptics.playPause(); viewModel.togglePlayPause() },
                    interactionSource = playInteraction,
                    modifier = Modifier
                        .size(AvaTokens.nowPlayingPlaySize)
                        .graphicsLayer {
                            scaleX = playScale
                            scaleY = playScale
                        }
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                ) {
                    // 0.1: the play/pause icon cross-fades instead of
                    // swapping instantly, like the mini player.
                    AnimatedContent(
                        targetState = state.isPlaying,
                        transitionSpec = { fadeIn(AvaMotion.crossFade) togetherWith fadeOut(AvaMotion.crossFade) },
                        label = "now-playing-play-icon",
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (playing) R.string.player_pause else R.string.player_play,
                            ),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { haptics.tap(); viewModel.playNext() },
                    modifier = Modifier
                        .size(AvaTokens.touchTargetMin)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.player_next),
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { haptics.toggle(); viewModel.cycleRepeatMode() }) {
                    Icon(
                        imageVector = if (state.repeatMode == RepeatMode.ONE) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        contentDescription = stringResource(
                            when (state.repeatMode) {
                                RepeatMode.OFF -> R.string.player_repeat_off
                                RepeatMode.ALL -> R.string.player_repeat_all
                                RepeatMode.ONE -> R.string.player_repeat_one
                            },
                        ),
                        tint = if (state.repeatMode == RepeatMode.OFF) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(AvaTokens.space12))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 0.6.0: a favorited song gets a soft tonal disc behind the
                // heart, so the state is visible at a glance, not only by
                // the icon's fill/color.
                IconButton(
                    onClick = { haptics.like(); heartPop++; viewModel.toggleFavorite() },
                    modifier = Modifier
                        .size(AvaTokens.touchTargetMin)
                        .background(
                            if (isFavorite) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                Color.Transparent
                            },
                            CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.player_favorite_remove
                            else R.string.player_favorite_add,
                        ),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer {
                            scaleX = heartScale.value
                            scaleY = heartScale.value
                        },
                    )
                }
                TextButton(onClick = { viewModel.cycleSpeed() }) {
                    Text(
                        text = "$speedLabel ${stringResource(R.string.player_speed)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.player_queue),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(AvaTokens.space24))
        }
    }
}

/**
 * Improve loop, run 8 (performance): the seek slider + elapsed /
 * remaining times, isolated in their own composable. This is the ONLY
 * part of Now Playing that reads the 500 ms position tick, so the tick
 * recomposes just a Slider and two Texts — the rest of the screen
 * (blurred cover, AmbientGlow, titles, controls) only
 * recomposes on real playback changes (play/pause, track, speed,
 * shuffle, repeat). The drag state lives here too, so a track change
 * resets the slider through [songId] exactly as before.
 */
@Composable
private fun NowPlayingProgress(
    positionMs: StateFlow<Long>,
    durationMs: Long,
    songId: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position by positionMs.collectAsState()
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember(songId) { mutableStateOf(position.toFloat()) }
    LaunchedEffect(position) {
        if (!sliderDragging) sliderValue = position.toFloat()
    }
    val remaining = (durationMs - position).coerceAtLeast(0L)
    Column(modifier = modifier) {
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderDragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                sliderDragging = false
                onSeek(sliderValue.toLong())
            },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(position),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "-${formatDuration(remaining)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
