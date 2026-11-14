package com.harper.ava.ui.nowplaying

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 0.1: the Now Playing ambient glow.
 *
 * While a song plays, the screen should feel alive and luxurious —
 * comfort and premium-ness from the song itself — without ever
 * pretending to react to the music. So this is ambient motion only:
 * three soft colour orbs (the theme's primary / secondary / tertiary)
 * drift slowly across the screen on two long loops (24 s and 31 s,
 * so the pattern never visibly repeats), and a halo behind the cover
 * breathes gently. When the song is playing the glow is at full
 * strength; when paused it eases down to a calm resting glow over
 * two seconds. It never flashes, never pulses fast, and it sits
 * behind the content, under a dark-enough backdrop that the text
 * above it stays readable.
 *
 * Honest about what it is: the motion is time-based, not
 * audio-reactive — there is no fake visualizer here.
 *
 * Performance: one Canvas drawing four radial gradients per frame,
 * and only while the screen is the current destination (`active`,
 * the same [com.harper.ava.ui.theme.rememberIsResumed] gate the
 * seasonal decor uses). A hidden Now Playing screen costs nothing.
 *
 * Reduced motion (improve run 19): with `reducedMotion` (from
 * [com.harper.ava.ui.theme.rememberReducedMotion]) the orbs and the
 * breathing halo hold one still frame — the two loop progresses are
 * never read inside the draw block. The play/pause aliveness ease
 * is left alone on purpose: it answers a user action, a 2 s fade,
 * not motion.
 */

/** Which theme colour an ambient orb is drawn from. */
enum class AmbientOrbRole { PRIMARY, SECONDARY, TERTIARY }

/**
 * One drifting orb. All positions are fractions of the canvas
 * (0..1 in each axis); the draw code scales them to pixels.
 */
data class AmbientOrb(
    val role: AmbientOrbRole,
    val baseX: Float,
    val baseY: Float,
    /** Radius as a fraction of the canvas' smaller side. */
    val radiusFraction: Float,
    /** How far the orb wanders from its base point (fractions). */
    val driftX: Float,
    val driftY: Float,
    /** Phase offset so the orbs never move in lockstep. */
    val phase: Float,
)

/** The three orbs: warm primary upper-left, cool secondary lower-right, soft tertiary top. */
val AMBIENT_ORBS: List<AmbientOrb> = listOf(
    AmbientOrb(AmbientOrbRole.PRIMARY, 0.22f, 0.28f, 0.55f, 0.06f, 0.05f, 0f),
    AmbientOrb(AmbientOrbRole.SECONDARY, 0.80f, 0.72f, 0.50f, 0.05f, 0.06f, 2.1f),
    AmbientOrb(AmbientOrbRole.TERTIARY, 0.62f, 0.16f, 0.38f, 0.07f, 0.04f, 4.2f),
)

/**
 * Centre of [orb] at loop [progress] (0..1) on a canvas of [size].
 * Pure — unit tested. The centre always stays inside the canvas
 * (with the drift clamped), so an orb never slides off-screen.
 */
fun ambientOrbCenter(orb: AmbientOrb, progress: Float, size: IntSize): Offset {
    val angle = 2.0 * PI * progress + orb.phase
    val x = (orb.baseX + orb.driftX * sin(angle).toFloat()).coerceIn(0f, 1f) * size.width
    val y = (orb.baseY + orb.driftY * cos(angle).toFloat()).coerceIn(0f, 1f) * size.height
    return Offset(x, y)
}

/**
 * Orb alpha for a [baseAlpha] at [aliveness] (0 = paused/resting,
 * 1 = playing). Resting is 45% of the base — the glow never fully
 * dies, it just calms down. Pure — unit tested.
 */
fun ambientOrbAlpha(baseAlpha: Float, aliveness: Float): Float =
    (baseAlpha * (0.45f + 0.55f * aliveness.coerceIn(0f, 1f))).coerceIn(0f, 1f)

/**
 * Breathing scale of the cover halo at loop [progress] and
 * [aliveness]: 1.0 when resting, up to ±3.5% while playing — a slow
 * inhale/exhale, never a throb. Pure — unit tested.
 */
fun ambientBreathScale(progress: Float, aliveness: Float): Float =
    1f + 0.035f * aliveness.coerceIn(0f, 1f) * sin(2.0 * PI * progress).toFloat()

@Composable
fun AmbientGlow(
    isPlaying: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    // The decor-pause contract: a hidden screen draws no frames.
    if (!active) return
    // Aliveness eases over 2 s when play/pause toggles — the glow
    // wakes up and settles down gently, never snapping.
    val aliveness by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 2000),
        label = "ambient-aliveness",
    )
    // Two loops at different lengths so the combined pattern does not
    // visibly repeat for minutes.
    val drift = rememberInfiniteTransition(label = "ambient-drift")
    val progressA by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambient-drift-a",
    )
    val progressB by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 31_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambient-drift-b",
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier) {
        // Reduced motion (improve run 19): the orbs and the halo hold
        // one still frame — neither loop progress is read, so the
        // ticking clocks schedule no redraws. The play/pause aliveness
        // ease above is deliberately left alone: it answers a user
        // action (a 2 s fade, not motion), and freezing it would make
        // the glow snap instead of settling.
        val drawProgressA = if (reducedMotion) 0f else progressA
        val drawProgressB = if (reducedMotion) 0f else progressB
        val canvasSize = IntSize(size.width.toInt(), size.height.toInt())
        // Breathing halo behind the cover (upper-middle of the screen).
        val breath = ambientBreathScale(drawProgressA, aliveness)
        val haloCenter = Offset(size.width * 0.5f, size.height * 0.34f)
        val haloRadius = size.minDimension * 0.42f * breath
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = ambientOrbAlpha(0.10f, aliveness)),
                    Color.Transparent,
                ),
                center = haloCenter,
                radius = haloRadius,
            ),
            radius = haloRadius,
            center = haloCenter,
        )
        // The three drifting orbs.
        for ((index, orb) in AMBIENT_ORBS.withIndex()) {
            val progress = if (index % 2 == 0) drawProgressA else drawProgressB
            val center = ambientOrbCenter(orb, progress, canvasSize)
            val radius = size.minDimension * orb.radiusFraction
            val color = when (orb.role) {
                AmbientOrbRole.PRIMARY -> primary
                AmbientOrbRole.SECONDARY -> secondary
                AmbientOrbRole.TERTIARY -> tertiary
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = ambientOrbAlpha(0.14f, aliveness)),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
