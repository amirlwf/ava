package com.harper.ava.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harper.ava.ui.theme.AvaTokens

/**
 * Skeleton loading (0.7.0, spec §13): shimmer placeholders shaped like
 * the rows they stand in for, so a list never flashes an empty state
 * while its data is genuinely still loading.
 *
 * The shimmer is a soft diagonal sweep between two surface tones from
 * the current theme — no hard-coded greys, so it reads right in light,
 * dark and AMOLED. One [rememberInfiniteTransition] drives the whole
 * list; every row reads the same brush, so the sweep moves in unison
 * instead of each row shimmering on its own clock.
 *
 * Wiring (all in the screens, never here): Home/Library show the
 * skeleton while a MediaStore scan is running and the list is still
 * empty; Search shows it while the typed query's results are stale
 * (the 200ms debounce window); Favorites shows it for a short capped
 * window after entry while Room's first emission is in flight. A
 * skeleton is never shown on a timer when data is already there.
 */

/** The shared shimmer brush: a light band sweeping left → right. */
@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AvaMotion.shimmerDurationMs,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton-sweep",
    )
    // A 1400px band: wide enough to cover a phone row at any density,
    // so the leading edge never visibly pops in mid-row.
    val base: Color = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight: Color = MaterialTheme.colorScheme.surfaceContainerHighest
    val x = -500f + sweep * 1600f
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + 500f, 0f),
    )
}

/** One skeleton song row: cover + title + artist lines, SongRow-shaped. */
@Composable
fun SkeletonSongRow(modifier: Modifier = Modifier) {
    val shimmer = rememberShimmerBrush()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AvaTokens.songRowHeight)
            .padding(horizontal = AvaTokens.space12, vertical = AvaTokens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvaTokens.coverSmall)
                .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                .background(shimmer),
        )
        Spacer(modifier = Modifier.width(AvaTokens.space12))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AvaTokens.space8),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                    .background(shimmer),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                    .background(shimmer),
            )
        }
    }
}

/**
 * A loading list: [count] skeleton rows in a plain [Column] (not a
 * LazyColumn) so it can sit inside a screen's own LazyColumn item as
 * well as stand alone.
 */
@Composable
fun SkeletonList(count: Int = 8, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(count) {
            SkeletonSongRow(modifier = Modifier.padding(horizontal = AvaTokens.space8))
        }
    }
}
