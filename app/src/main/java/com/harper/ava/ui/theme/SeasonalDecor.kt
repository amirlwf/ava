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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.harper.ava.data.preferences.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Seasonal decor (0.7.2): a whisper of the current season behind the
 * content of Home and Now Playing — never anywhere else, never loud.
 *
 * - SPRING: green leaves drifting down.
 * - SUMMER: a soft sun glow in the top corner + a few green leaves.
 * - AUTUMN: amber / red leaves falling.
 * - WINTER: snowflakes drifting down.
 * - GALAXY: twinkling stars + faint nebula blobs (the space aesthetic
 *   that sets Galactic Black apart from plain AMOLED black), plus —
 *   since 0.7.3 — 3 shooting stars crossing the sky on staggered phases
 *   and a small visible solar system (a warm glowing sun in the upper
 *   area with 3 planets on faint elliptical orbits around it).
 * - Every other theme (Emerald, Violet, Ocean, Sunset): no decor.
 *
 * All vector / Canvas-drawn, no images, no new fonts. One slow
 * [rememberInfiniteTransition] (36 s loop) drives every particle from a
 * single `progress` float, so the cost is one small Canvas redraw per
 * frame on at most two screens; particle positions are deterministic
 * functions of `progress` + a per-particle seed, so there is no state
 * churn and nothing to remember across recompositions. Particle counts
 * stay small (5–26) and alphas low (0.05–0.35) — decoration, not noise.
 *
 * The decor is only drawn when [seasonalDecorKind] returns a kind other
 * than [SeasonalDecorKind.NONE]; with effects off (or a non-seasonal
 * theme) the composable returns before creating any animation, so there
 * is zero cost when disabled.
 */

/** Which decor (if any) a theme shows. Pure logic — unit tested. */
enum class SeasonalDecorKind {
    NONE,
    SPRING_LEAVES,
    SUMMER_SUN,
    AUTUMN_LEAVES,
    WINTER_SNOW,
    GALAXY_STARS,
}

/**
 * The decor for [theme], or [SeasonalDecorKind.NONE] when the user turned
 * seasonal effects off or the theme has no seasonal identity.
 */
fun seasonalDecorKind(theme: AppTheme, effectsEnabled: Boolean): SeasonalDecorKind {
    if (!effectsEnabled) return SeasonalDecorKind.NONE
    return when (theme) {
        AppTheme.SPRING -> SeasonalDecorKind.SPRING_LEAVES
        AppTheme.SUMMER -> SeasonalDecorKind.SUMMER_SUN
        AppTheme.AUTUMN -> SeasonalDecorKind.AUTUMN_LEAVES
        AppTheme.WINTER -> SeasonalDecorKind.WINTER_SNOW
        AppTheme.GALAXY -> SeasonalDecorKind.GALAXY_STARS
        AppTheme.EMERALD,
        AppTheme.VIOLET,
        AppTheme.OCEAN,
        AppTheme.SUNSET,
        -> SeasonalDecorKind.NONE
    }
}

/**
 * Seasonal background decor for Home / Now Playing. Place it as the first
 * child of the screen's root [androidx.compose.foundation.layout.Box]
 * (behind the content) with `Modifier.fillMaxSize()`.
 */
@Composable
fun SeasonalDecor(
    theme: AppTheme,
    effectsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val kind = seasonalDecorKind(theme, effectsEnabled)
    if (kind == SeasonalDecorKind.NONE) return
    SeasonalDecorCanvas(kind = kind, modifier = modifier)
}

@Composable
private fun SeasonalDecorCanvas(kind: SeasonalDecorKind, modifier: Modifier = Modifier) {
    // One slow loop for every particle: 36 s, linear, restarts seamlessly.
    val transition = rememberInfiniteTransition(label = "seasonal-decor")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "seasonal-progress",
    )
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier.fillMaxSize()) {
        drawDecor(kind, progress, scheme.primary, scheme.secondary, scheme.tertiary, scheme.onBackground)
    }
}

/** Deterministic pseudo-random 0..1 from an index + salt (no Random state). */
private fun hash01(index: Int, salt: Int): Float {
    val v = sin(index * 12.9898 + salt * 78.233) * 43758.5453
    val f = v - kotlin.math.floor(v)
    return f.toFloat()
}

/** Wrap a float into [0, 1). */
private fun wrap01(v: Float): Float = ((v % 1f) + 1f) % 1f

// ---------------------------------------------------------------------------
// Galaxy helpers (0.7.3) — pure, deterministic, unit tested in
// SeasonalDecorTest. All coordinates are normalised to the canvas
// (0..1 in each axis); the draw code scales them to pixels. Positions are
// functions of `progress` + a per-object seed only: no extra state, no
// extra animation loop — the single 36 s loop drives everything.
// ---------------------------------------------------------------------------

/** Number of shooting stars in the galaxy sky. */
const val GALAXY_METEOR_COUNT = 3

/** Number of planets orbiting the galaxy sun. */
const val GALAXY_PLANET_COUNT = 3

/** Max alpha of a meteor head — a whisper, never a flash. */
const val GALAXY_METEOR_MAX_ALPHA = 0.25f

/**
 * One shooting star at a given loop [progress], or null while it is
 * off-screen. Each meteor starts at its own phase (first ~65% of the
 * loop), streaks diagonally from the upper right toward the lower left
 * for ~10–15% of the loop (~4–5 s), then waits for the next loop.
 * The tail points up-right, opposite the direction of travel; [alpha]
 * fades in at the start and out at the end so the meteor appears and
 * dissolves instead of popping in/out.
 */
data class MeteorState(
    val headX: Float,
    val headY: Float,
    val tailX: Float,
    val tailY: Float,
    val alpha: Float,
)

fun meteorState(index: Int, progress: Float): MeteorState? {
    val start = hash01(index, 21) * 0.65f
    val duration = 0.10f + 0.05f * hash01(index, 22)
    val local = wrap01(progress - start)
    if (local >= duration) return null
    val u = local / duration
    val headX = 0.75f + 0.25f * hash01(index, 23) - u * 0.95f
    val headY = -0.02f + 0.18f * hash01(index, 24) + u * 0.55f
    // Fade in over the first 15% of the streak, out over the last 25%.
    val fadeIn = minOf(u / 0.15f, 1f)
    val fadeOut = minOf((1f - u) / 0.25f, 1f)
    val alpha = GALAXY_METEOR_MAX_ALPHA * fadeIn * fadeOut
    return MeteorState(
        headX = headX,
        headY = headY,
        tailX = headX + 0.16f,
        tailY = headY - 0.09f,
        alpha = alpha,
    )
}

/**
 * Elliptical orbit radii of planet [index] around the galaxy sun, as
 * fractions of the canvas width (horizontal) and height (vertical).
 * The vertical radius is deliberately ~half the horizontal one, so the
 * orbit reads as a tilted ellipse seen from slightly above rather than
 * a flat circle. Radii grow with the index: the planets never share a
 * track and never cross each other's path.
 */
fun planetOrbitRadius(index: Int): Pair<Float, Float> {
    val rx = 0.14f + 0.06f * index.toFloat()
    val ry = 0.075f + 0.032f * index.toFloat()
    return rx to ry
}

/** Orbital angle (radians, 0..2π) of planet [index] at loop [progress]. */
fun planetAngle(index: Int, progress: Float): Float {
    val lapsPerLoop = 0.6f - 0.2f * index.toFloat() // inner planets orbit faster
    val phase = hash01(index, 25)
    return wrap01(progress * lapsPerLoop + phase) * 2f * PI.toFloat()
}

/** Position of planet [index] on its ellipse around ([centerX], [centerY]). */
data class OrbitPoint(val x: Float, val y: Float)

fun planetPosition(
    index: Int,
    progress: Float,
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
): OrbitPoint {
    val angle = planetAngle(index, progress)
    return OrbitPoint(
        x = centerX + radiusX * cos(angle),
        y = centerY + radiusY * sin(angle),
    )
}

private fun DrawScope.drawDecor(
    kind: SeasonalDecorKind,
    progress: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    onBackground: Color,
) {
    when (kind) {
        SeasonalDecorKind.SPRING_LEAVES -> drawFallingLeaves(
            progress = progress,
            count = 10,
            colors = listOf(primary, tertiary),
            alpha = 0.28f,
        )
        SeasonalDecorKind.SUMMER_SUN -> {
            drawSun(progress, secondary)
            drawFallingLeaves(
                progress = progress,
                count = 5,
                colors = listOf(primary, tertiary),
                alpha = 0.22f,
            )
        }
        SeasonalDecorKind.AUTUMN_LEAVES -> drawFallingLeaves(
            progress = progress,
            count = 12,
            colors = listOf(primary, secondary),
            alpha = 0.32f,
        )
        SeasonalDecorKind.WINTER_SNOW -> drawSnow(progress, tertiary, onBackground)
        SeasonalDecorKind.GALAXY_STARS -> drawGalaxy(progress, primary, secondary, tertiary, onBackground)
        SeasonalDecorKind.NONE -> Unit
    }
}

/** A pointed-oval leaf centred on the origin, `radius` tall. */
private fun leafPath(radius: Float): Path = Path().apply {
    moveTo(0f, -radius)
    quadraticTo(radius * 0.75f, -radius * 0.25f, 0f, radius)
    quadraticTo(-radius * 0.75f, -radius * 0.25f, 0f, -radius)
    close()
}

/** Leaves falling slowly, swaying side to side, gently spinning. */
private fun DrawScope.drawFallingLeaves(
    progress: Float,
    count: Int,
    colors: List<Color>,
    alpha: Float,
) {
    for (i in 0 until count) {
        val x0 = hash01(i, 1)
        val y0 = hash01(i, 2)
        val speed = 0.25f + 0.55f * hash01(i, 3)
        val leafRadius = (7f + 9f * hash01(i, 4)).dp.toPx()
        val sway = sin(progress * 2f * PI.toFloat() * 1.5f + i * 1.7f) * 22.dp.toPx()
        val x = x0 * size.width + sway
        val y = ((y0 + progress * speed) % 1f) * size.height
        val rotation = progress * 360f * (if (i % 2 == 0) 1f else -1f) + i * 40f
        val color = colors[i % colors.size].copy(alpha = alpha)
        translate(left = x, top = y) {
            rotate(degrees = rotation) {
                drawPath(leafPath(leafRadius), color)
                // A faint centre vein so the leaf reads as a leaf, not a blob.
                drawLine(
                    color = color.copy(alpha = alpha * 0.6f),
                    start = Offset(0f, -leafRadius),
                    end = Offset(0f, leafRadius),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** A soft sun in the top corner: two glow discs + eight short rays. */
private fun DrawScope.drawSun(progress: Float, sunColor: Color) {
    val center = Offset(size.width - 44.dp.toPx(), 44.dp.toPx())
    drawCircle(color = sunColor.copy(alpha = 0.10f), radius = 58.dp.toPx(), center = center)
    drawCircle(color = sunColor.copy(alpha = 0.16f), radius = 34.dp.toPx(), center = center)
    for (k in 0 until 8) {
        val angle = k * PI.toFloat() / 4f + progress * 0.35f
        val inner = 44.dp.toPx()
        val outer = inner + (10.dp.toPx() + 4.dp.toPx() * sin(progress * 2f * PI.toFloat() + k))
        drawLine(
            color = sunColor.copy(alpha = 0.20f),
            start = center + Offset(cos(angle) * inner, sin(angle) * inner),
            end = center + Offset(cos(angle) * outer, sin(angle) * outer),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Snowflakes drifting down, swaying gently, in two cool tones. */
private fun DrawScope.drawSnow(progress: Float, ice: Color, white: Color) {
    for (i in 0 until 14) {
        val x0 = hash01(i, 5)
        val y0 = hash01(i, 6)
        val speed = 0.15f + 0.30f * hash01(i, 7)
        val radius = (2.5f + 2.5f * hash01(i, 8)).dp.toPx()
        val sway = sin(progress * 2f * PI.toFloat() + i * 2.1f) * 14.dp.toPx()
        val x = x0 * size.width + sway
        val y = ((y0 + progress * speed) % 1f) * size.height
        val color = (if (i % 3 == 0) ice else white).copy(alpha = 0.30f)
        drawSnowflake(Offset(x, y), radius, color)
    }
}

private fun DrawScope.drawSnowflake(center: Offset, radius: Float, color: Color) {
    for (k in 0 until 3) {
        val angle = k * PI.toFloat() / 3f
        val dx = cos(angle) * radius
        val dy = sin(angle) * radius
        drawLine(
            color = color,
            start = center + Offset(-dx, -dy),
            end = center + Offset(dx, dy),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    drawCircle(color = color, radius = radius * 0.3f, center = center)
}

/**
 * Galaxy: faint nebula blobs in the corners + twinkling four-point stars.
 * Stars never move — only their alpha breathes, so the night sky feels
 * alive without any motion that could distract from the music.
 *
 * 0.7.3 adds two quiet pieces of sky, both driven by the same 36 s
 * `progress` loop and both kept faint so the content stays readable:
 * - 3 shooting stars ([meteorState]) streaking diagonally across the
 *   sky on staggered phases, each visible only ~4–5 s per loop.
 * - A small solar system in the upper area: a warm glowing sun with
 *   3 planets on faint elliptical orbits ([planetPosition]). The whole
 *   system sits in the top ~30% band, behind the content, so it never
 *   fights the album art or the song list.
 */
private fun DrawScope.drawGalaxy(
    progress: Float,
    violet: Color,
    pink: Color,
    blue: Color,
    white: Color,
) {
    // Nebula: two very faint, very large washes in opposite corners.
    drawCircle(
        color = violet.copy(alpha = 0.07f),
        radius = size.width * 0.42f,
        center = Offset(size.width * 0.12f, size.height * 0.10f),
    )
    drawCircle(
        color = pink.copy(alpha = 0.05f),
        radius = size.width * 0.36f,
        center = Offset(size.width * 0.92f, size.height * 0.88f),
    )
    drawCircle(
        color = blue.copy(alpha = 0.05f),
        radius = size.width * 0.30f,
        center = Offset(size.width * 0.85f, size.height * 0.18f),
    )
    for (i in 0 until 26) {
        val x = hash01(i, 9) * size.width
        val y = hash01(i, 10) * size.height
        val radius = (1.5f + 3f * hash01(i, 11)).dp.toPx()
        val twinkleSpeed = 0.5f + hash01(i, 12)
        val phase = i * 1.3f
        val twinkle = 0.5f + 0.5f * sin(progress * 2f * PI.toFloat() * twinkleSpeed + phase)
        val alpha = 0.10f + 0.30f * twinkle
        val color = when (i % 5) {
            0 -> pink
            1 -> blue
            2 -> violet
            else -> white
        }.copy(alpha = alpha)
        if (i % 3 == 0) {
            drawStar(Offset(x, y), radius, color)
        } else {
            drawCircle(color = color, radius = radius * 0.45f, center = Offset(x, y))
        }
    }
    drawSolarSystem(progress, pink, blue, white)
    drawMeteors(progress)
}

/**
 * The solar system: a small warm sun in the upper area, faint
 * elliptical orbit rings, and 3 planets slowly circling it.
 * The sun sits right-of-centre near the top so its widest orbit
 * (0.26 × width) still fits on screen; the whole system stays in the
 * top band, behind the header content.
 */
private fun DrawScope.drawSolarSystem(
    progress: Float,
    pink: Color,
    blue: Color,
    white: Color,
) {
    // Sun at 14% height: the widest orbit (ry 0.139) just fits above it,
    // so no orbit ring is clipped by the top edge of the screen.
    val sunCenter = Offset(size.width * 0.70f, size.height * 0.14f)
    val sunWarm = Color(0xFFFFE08A)
    val sunCore = Color(0xFFFFF6D8)
    // Halo breathes very gently with the loop — barely noticeable.
    val breathe = 0.5f + 0.5f * sin(progress * 2f * PI.toFloat())
    drawCircle(
        color = sunWarm.copy(alpha = 0.06f + 0.02f * breathe),
        radius = 52.dp.toPx(),
        center = sunCenter,
    )
    drawCircle(
        color = sunWarm.copy(alpha = 0.13f + 0.03f * breathe),
        radius = 30.dp.toPx(),
        center = sunCenter,
    )
    drawCircle(color = sunCore.copy(alpha = 0.85f), radius = 9.dp.toPx(), center = sunCenter)
    drawCircle(color = sunWarm.copy(alpha = 0.55f), radius = 6.dp.toPx(), center = sunCenter)

    val planetColors = listOf(pink, blue, white)
    val planetRadii = listOf(4.5f, 3.5f, 3f)
    for (i in 0 until GALAXY_PLANET_COUNT) {
        val (rxNorm, ryNorm) = planetOrbitRadius(i)
        val rx = rxNorm * size.width
        val ry = ryNorm * size.height
        // Orbit ring: a very faint tilted ellipse.
        drawOval(
            color = white.copy(alpha = 0.08f),
            topLeft = Offset(sunCenter.x - rx, sunCenter.y - ry),
            size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )
        val pos = planetPosition(
            index = i,
            progress = progress,
            centerX = sunCenter.x / size.width,
            centerY = sunCenter.y / size.height,
            radiusX = rxNorm,
            radiusY = ryNorm,
        )
        val center = Offset(pos.x * size.width, pos.y * size.height)
        val radius = planetRadii[i].dp.toPx()
        val color = planetColors[i]
        drawCircle(color = color.copy(alpha = 0.10f), radius = radius * 2f, center = center)
        drawCircle(color = color.copy(alpha = if (i % 2 == 0) 0.30f else 0.50f), radius = radius, center = center)
    }
}

/** Shooting stars: a bright head + a fading tail of three segments. */
private fun DrawScope.drawMeteors(progress: Float) {
    for (i in 0 until GALAXY_METEOR_COUNT) {
        val meteor = meteorState(i, progress) ?: continue
        val head = Offset(meteor.headX * size.width, meteor.headY * size.height)
        val tail = Offset(meteor.tailX * size.width, meteor.tailY * size.height)
        // Tail in three segments, each fainter than the last.
        val p1 = head + (tail - head) * 0.30f
        val p2 = head + (tail - head) * 0.65f
        drawLine(
            color = Color.White.copy(alpha = meteor.alpha),
            start = head,
            end = p1,
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = meteor.alpha * 0.55f),
            start = p1,
            end = p2,
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = meteor.alpha * 0.25f),
            start = p2,
            end = tail,
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = Color.White.copy(alpha = meteor.alpha), radius = 2.dp.toPx(), center = head)
    }
}

/** A four-point sparkle centred on [center]. */
private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}
