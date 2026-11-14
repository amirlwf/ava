package com.harper.ava.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glass aurora (0.1-fix1, Track C) — the one bold, unmistakable element
 * of the IPHONE theme.
 *
 * Why it exists: in 0.1 the glass theme was a translucent panel over a
 * flat opaque background, so there was nothing behind the glass to
 * show through — selecting IPHONE changed almost nothing visible
 * ("no change is felt at all"). The aurora fixes that at the root:
 * three large, soft colour fields (iOS blue / cyan / violet) drift
 * behind every screen, so the frosted panels have something real to
 * blur. It lives at app level in [AppBackdrop], next to the seasonal
 * decor, never per-screen and never inside a nav transition.
 *
 * The loop is 48 s (slower than the 36 s decor loop, so the two never
 * read as one rhythm), linear, and restarts seamlessly. Strength comes
 * from the pure [glassAuroraAlpha] rules — strong enough to be felt
 * through the glass panels (≤ 0.20), quieter in AMOLED — and each blob
 * is a radial gradient fading to transparent, so there are no hard
 * edges anywhere. 0.3-feedback (Track 6): the fields are larger than
 * in 0.1 (radii 0.68 / 0.58 / 0.72 of the longest side, was 0.55 /
 * 0.48 / 0.60) as well as stronger — the sky has to cover the screen,
 * not sit in three small spots.
 *
 * This file is NOT in verify.sh's test-compile list (it uses the
 * Compose plugin's inline-free composables only in the full build);
 * the pure strength rules live in AppTheme.kt and are pinned there.
 *
 * Reduced motion (improve run 19): with [reducedMotion] the aurora
 * draws one still frame — `progress` is never read inside the draw
 * block, so the ticking 48 s clock schedules no redraws. The blobs
 * sit at their base positions with the phase sway baked in at
 * progress 0, so the glass still has a coloured sky to blur over,
 * only a still one.
 */
@Composable
fun GlassAurora(amoled: Boolean, modifier: Modifier = Modifier, reducedMotion: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "glass-aurora")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 48_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora-progress",
    )
    val background = MaterialTheme.colorScheme.background
    val baseAlpha = glassAuroraAlpha(
        argbLuminance(background.toArgb().toLong() and 0xFFFFFFFFL),
        amoled,
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        // Reduced motion: a still sky (see the KDoc above). The
        // transition still exists — a hook cannot be conditional —
        // but an unread clock is one cheap float per frame.
        val drawProgress = if (reducedMotion) 0f else progress
        val maxDim = maxOf(size.width, size.height)
        for (i in AURORA_BLOBS.indices) {
            val blob = AURORA_BLOBS[i]
            // A slow, barely-there sway — each blob drifts on its own
            // phase, ±4% of the canvas, so the sky feels alive without
            // ever moving fast enough to distract.
            val phase = i * 2.1f
            val driftX = sin(drawProgress * 2f * PI.toFloat() + phase) * size.width * 0.04f
            val driftY = cos(drawProgress * 2f * PI.toFloat() + phase) * size.height * 0.04f
            val center = Offset(
                blob.centerX * size.width + driftX,
                blob.centerY * size.height + driftY,
            )
            val radius = blob.radiusFraction * maxDim
            val alpha = glassAuroraBlobAlpha(baseAlpha, i)
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to blob.color.copy(alpha = alpha),
                    1.0f to blob.color.copy(alpha = 0f),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

/** One aurora field: where it sits, how big it is, which iOS colour it is. */
private data class AuroraBlob(
    val centerX: Float,
    val centerY: Float,
    val radiusFraction: Float,
    val color: Color,
)

/**
 * The three fields: blue upper-left (the strongest, the sky's anchor),
 * cyan upper-right, violet low-centre. Radii are fractions of the
 * canvas's longest side, so the fields cover the screen on phones and
 * tablets alike without hard edges.
 */
private val AURORA_BLOBS: List<AuroraBlob> = listOf(
    AuroraBlob(0.18f, 0.16f, 0.68f, Color(0xFF0071E3)),
    AuroraBlob(0.85f, 0.28f, 0.58f, Color(0xFF64D2FF)),
    AuroraBlob(0.50f, 0.88f, 0.72f, Color(0xFFBF5AF2)),
)
