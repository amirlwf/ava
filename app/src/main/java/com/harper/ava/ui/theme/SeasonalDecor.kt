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
import androidx.compose.ui.unit.dp
import com.harper.ava.data.preferences.AppTheme
import kotlin.math.PI
import kotlin.math.abs
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
 * - GALAXY: a deep sky — a three-layer twinkling starfield with
 *   parallax depth, a diagonal Milky Way band packed with dust, six
 *   layered nebula clouds, two faint constellations, and — since
 *   0.7.3, enriched in 0.7.4 — 4 shooting stars with long glowing
 *   tails and a visible solar system (a warm glowing sun with a
 *   corona, 5 planets on faint elliptical orbits, moons on two of
 *   them and a ring on the outermost).
 * - Every other theme (Emerald, Violet, Ocean, Sunset): no decor.
 *
 * All vector / Canvas-drawn, no images, no new fonts. One slow
 * [rememberInfiniteTransition] (36 s loop) drives every particle from a
 * single `progress` float, so the cost is one small Canvas redraw per
 * frame on at most two screens; particle positions are deterministic
 * functions of `progress` + a per-particle seed, so there is no state
 * churn and nothing to remember across recompositions. Galaxy
 * particle counts stay small per layer (36 far + 26 mid + 10 bright
 * stars, 34 Milky Way dust grains, 6 nebula clouds, 2 constellations)
 * and alphas low (0.03–0.16 for dust, nebula ≤ 0.07, brightest
 * sparkles ≤ 0.45, only a meteor head briefly reaches 0.35) —
 * decoration, not noise.
 *
 * Battery (improve run 3, 2026-11-12): the Canvas is NOT drawn while
 * its screen is not the current destination — each screen passes
 * `active = rememberIsResumed()` (see DecorVisibility.kt), which reads
 * the destination's NavBackStackEntry lifecycle: a back-stack entry
 * drops below RESUMED the moment another screen opens above it (and
 * when the app backgrounds), so Home's sky stops the moment Now
 * Playing opens above it instead of redrawing a full-screen canvas
 * behind a screen nobody sees. The 36 s clock itself keeps running
 * while hidden (one float per frame — cheap) so the sky resumes
 * mid-loop with no visual jump when the user navigates back; only the
 * expensive part — the full-screen redraw — is skipped. Per-frame
 * allocations are also gone: sparkle/leaf [Path]s are cached by radius
 * (they were rebuilt ~35× per frame) and the small constant lists
 * (meteor tail segments, planet radii, Milky Way glow stops) are
 * hoisted to file-level vals instead of being rebuilt per frame.
 *
 * NOTE: this file must not read a CompositionLocal directly
 * (`LocalLifecycleOwner.current` and friends): the unit-test compile
 * in `scripts/verify.sh` runs kotlinc without the Compose plugin, and
 * the inline `CompositionLocal.current` getter cannot be inlined
 * there — the build fails. Lifecycle reads live in DecorVisibility.kt
 * (not in the test-compile list); this file only takes a plain
 * `active` Boolean.
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
 *
 * @param active false while this screen is not the current destination
 * (the caller reads its NavBackStackEntry lifecycle — see
 * [rememberIsResumed]); the Canvas then draws no frames, so a hidden
 * screen's sky costs nothing. Defaults to true.
 */
@Composable
fun SeasonalDecor(
    theme: AppTheme,
    effectsEnabled: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val kind = seasonalDecorKind(theme, effectsEnabled)
    if (kind == SeasonalDecorKind.NONE) return
    SeasonalDecorCanvas(kind = kind, modifier = modifier, active = active)
}

@Composable
private fun SeasonalDecorCanvas(kind: SeasonalDecorKind, modifier: Modifier = Modifier, active: Boolean) {
    // One slow loop for every particle: 36 s, linear, restarts seamlessly.
    // The transition is created BEFORE the visibility gate on purpose:
    // while this screen is hidden its clock keeps ticking (one float per
    // frame — cheap), so the sky resumes mid-loop with no jump when the
    // user navigates back. Only the Canvas — the expensive part — stops.
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
    // Paused while this screen is not the current destination (or the
    // app is backgrounded): a canvas nobody sees draws no frames, so
    // Home's sky stops the moment Now Playing opens above it.
    if (!active) return
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
// Galaxy helpers (0.7.3 + deep 0.7.4 redesign) — pure, deterministic, unit
// tested in SeasonalDecorTest. All coordinates are normalised to the canvas
// (0..1 in each axis); the draw code scales them to pixels. Positions are
// functions of `progress` + a per-object seed only: no extra state, no
// extra animation loop — the single 36 s loop drives everything.
// ---------------------------------------------------------------------------

/** Number of shooting stars in the galaxy sky (0.7.4: was 3). */
const val GALAXY_METEOR_COUNT = 4

/** Number of planets orbiting the galaxy sun (0.7.4: was 3 in 0.7.3). */
const val GALAXY_PLANET_COUNT = 5

/**
 * Max alpha of a meteor head — the brightest (first) meteor; the other
 * meteors peak at 0.25 ([meteorMaxAlpha]).
 */
const val GALAXY_METEOR_MAX_ALPHA = 0.35f

/** Starfield layers: 0 = far (tiny, dim, slow), 1 = mid, 2 = near/bright. */
const val GALAXY_FAR_STAR_COUNT = 36
const val GALAXY_MID_STAR_COUNT = 26
const val GALAXY_BRIGHT_STAR_COUNT = 10

/** Dust grains packed along the Milky Way band. */
const val GALAXY_MILKY_WAY_DUST_COUNT = 34

// ---------------------------------------------------------------------------
// Per-frame allocations removed (improve run 3, 2026-11-12). The Canvas
// redraws 60×/s, so every `listOf` / fresh `Path` inside a draw function
// was that many small allocations per second. These vals hold the exact
// numbers the draw code used to build inline, every frame; the tests in
// SeasonalDecorTest pin their contents, so a transcription slip fails
// loudly instead of silently changing the sky.
// ---------------------------------------------------------------------------

/** Meteor tail segments: where each of the five segments starts along the tail (0..1). */
val METEOR_TAIL_FRACTIONS = listOf(0f, 0.18f, 0.38f, 0.60f, 0.82f, 1f)

/** Meteor tail segment alphas: brightest at the head, fading to the tip. */
val METEOR_TAIL_ALPHAS = listOf(1f, 0.70f, 0.45f, 0.25f, 0.12f)

/** Meteor tail segment widths in dp: thick at the head, thin at the tip. */
val METEOR_TAIL_WIDTHS_DP = listOf(2.2f, 1.8f, 1.4f, 1.1f, 0.8f)

/** Milky Way glow stops along the band (0..1); colours are violet, pink, violet. */
val MILKY_WAY_GLOW_STOPS = listOf(0.25f, 0.50f, 0.75f)

/** Planet radii in dp, inner to outer — one entry per planet. */
val GALAXY_PLANET_RADII_DP = listOf(4.5f, 3.5f, 3f, 4f, 5f)

/**
 * Origin-centred sparkle / leaf paths, cached by radius: the galaxy
 * draws ~25 sparkles and the seasons ~10 leaves every frame, and a
 * fresh [Path] per shape per frame is pure GC churn (each one wraps a
 * native SkPath). Radii are deterministic hashes, so the distinct set
 * is tiny (~30 sparkles, ~12 leaves) and the cache stays small.
 * Drawing happens only on the UI thread, so no synchronisation is
 * needed; a cached path is never mutated after creation — callers draw
 * it inside [translate] (and [rotate] for leaves).
 */
private val sparklePathCache = mutableMapOf<Float, Path>()
private val leafPathCache = mutableMapOf<Float, Path>()

/**
 * A four-point sparkle centred on the origin with the given [radius]
 * — the same outline [drawStar] used to build around `center` every
 * frame, only shifted to the origin so it can be cached and drawn via
 * [translate]. Geometry is unchanged by construction (every coordinate
 * is the old one minus `center`); not unit-testable here — [Path]
 * delegates to android.graphics.Path, which is a stub on the JVM.
 */
private fun sparklePath(radius: Float): Path = sparklePathCache.getOrPut(radius) {
    Path().apply {
        moveTo(0f, -radius)
        quadraticTo(0f, 0f, radius, 0f)
        quadraticTo(0f, 0f, 0f, radius)
        quadraticTo(0f, 0f, -radius, 0f)
        quadraticTo(0f, 0f, 0f, -radius)
        close()
    }
}

/**
 * A pointed-oval leaf centred on the origin, `radius` tall — cached
 * like [sparklePath]; the outline is byte-for-byte the old `leafPath`,
 * which was already origin-centred.
 */
private fun leafPath(radius: Float): Path = leafPathCache.getOrPut(radius) {
    Path().apply {
        moveTo(0f, -radius)
        quadraticTo(radius * 0.75f, -radius * 0.25f, 0f, radius)
        quadraticTo(-radius * 0.75f, -radius * 0.25f, 0f, -radius)
        close()
    }
}

/** Peak alpha of meteor [index]: the first meteor is the bright one. */
fun meteorMaxAlpha(index: Int): Float = if (index == 0) GALAXY_METEOR_MAX_ALPHA else 0.25f

/** Length of meteor [index]'s tail, as a fraction of the canvas width. */
fun meteorTailLength(index: Int): Float = 0.13f + 0.09f * hash01(index, 26)

/**
 * One shooting star at a given loop [progress], or null while it is
 * off-screen. Each meteor starts at its own phase (first ~65% of the
 * loop), streaks diagonally from the upper right toward the lower left
 * for ~10–15% of the loop (~4–5 s), then waits for the next loop.
 * The tail points up-right, opposite the direction of travel; [alpha]
 * fades in at the start and out at the end so the meteor appears and
 * dissolves instead of popping in/out. Tail length varies per meteor
 * ([meteorTailLength]); the first meteor burns brighter than the rest.
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
    val alpha = meteorMaxAlpha(index) * fadeIn * fadeOut
    val tailLen = meteorTailLength(index)
    return MeteorState(
        headX = headX,
        headY = headY,
        tailX = headX + tailLen,
        tailY = headY - tailLen * 0.56f,
        alpha = alpha,
    )
}

/**
 * Elliptical orbit radii of planet [index] around the galaxy sun, as
 * fractions of the canvas width (horizontal) and height (vertical).
 * The vertical radius is deliberately ~half the horizontal one, so the
 * orbit reads as a tilted ellipse seen from slightly above rather than
 * a flat circle. Radii grow with the index: the planets never share a
 * track and never cross each other's path. Kept compact (widest
 * 0.25 x 0.138) so all five orbits fit around the sun at (0.70, 0.14)
 * without leaving the canvas.
 */
fun planetOrbitRadius(index: Int): Pair<Float, Float> {
    val rx = 0.09f + 0.04f * index.toFloat()
    val ry = 0.05f + 0.022f * index.toFloat()
    return rx to ry
}

/** Orbital angle (radians, 0..2π) of planet [index] at loop [progress]. */
fun planetAngle(index: Int, progress: Float): Float {
    val lapsPerLoop = 0.6f - 0.09f * index.toFloat() // inner planets orbit faster
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

// ---------------------------------------------------------------------------
// 0.7.4 galaxy helpers — starfield depth, Milky Way, nebula, constellations,
// moons. All pure + deterministic, same single-loop discipline as above.
// ---------------------------------------------------------------------------

/** Radius (dp) of star [index] on layer [layer] (0 far, 1 mid, 2 bright). */
fun galaxyStarRadiusDp(index: Int, layer: Int): Float = when (layer) {
    0 -> 0.8f + 0.9f * hash01(index, 51)
    2 -> 3.0f + 2.5f * hash01(index, 53)
    else -> 1.5f + 3.0f * hash01(index, 52)
}

/**
 * Twinkling alpha of star [index] on [layer] at [progress]: far stars
 * breathe 0.06–0.22, mid stars 0.10–0.40, bright stars 0.15–0.45. Each
 * star has its own speed + phase, so the sky never twinkles in unison.
 */
fun galaxyStarAlpha(index: Int, layer: Int, progress: Float): Float {
    val speed = 0.5f + hash01(index, 54 + layer)
    val phase = index * 1.3f + layer * 2.1f
    val twinkle = 0.5f + 0.5f * sin(progress * 2f * PI.toFloat() * speed + phase)
    return when (layer) {
        0 -> 0.06f + 0.16f * twinkle
        2 -> 0.15f + 0.30f * twinkle
        else -> 0.10f + 0.30f * twinkle
    }
}

/**
 * Parallax drift (normalised canvas x) of layer [layer] at [progress]:
 * a slow sway — the far layer barely moves (±0.006), the near layer
 * moves most (±0.020) — so the layers feel at different depths. A
 * smooth sine, so it is exactly 0 at both ends of the 36 s loop and
 * never jumps when the loop restarts.
 */
fun galaxyParallaxDrift(layer: Int, progress: Float): Float {
    val amplitude = 0.006f + 0.007f * layer.toFloat()
    return sin(progress * 2f * PI.toFloat()) * amplitude
}

/**
 * Normalised position of star [index] on [layer], including its
 * parallax drift. Each layer uses its own hash salts, so the three
 * layers never stack two stars on the same spot.
 */
fun galaxyStarPosition(index: Int, layer: Int, progress: Float): Pair<Float, Float> {
    val x = wrap01(hash01(index, 61 + layer * 2) + galaxyParallaxDrift(layer, progress))
    val y = hash01(index, 62 + layer * 2)
    return x to y
}

/** Centre line of the Milky Way band: bottom-left (0, 0.78) to top-right (1, 0.18). */
fun milkyWayBandCenterY(x: Float): Float = 0.78f - 0.60f * x

/**
 * How dense the Milky Way looks at ([x], [y]): 1 on the band's centre
 * line, fading quadratically to 0 at 0.17 (normalised) away from it.
 */
fun milkyWayDensity(x: Float, y: Float): Float {
    val d = abs(y - milkyWayBandCenterY(x)) / 0.17f
    val t = 1f - minOf(d, 1f)
    return t * t
}

/** Normalised position of Milky Way dust grain [index] — always inside the band. */
fun milkyWayDustPosition(index: Int): Pair<Float, Float> {
    val along = 0.04f + 0.92f * hash01(index, 41)
    val across = (hash01(index, 42) - 0.5f) * 0.16f
    return along to (milkyWayBandCenterY(along) + across)
}

/** Twinkling alpha of dust grain [index] at [progress] (0.03–0.16). */
fun milkyWayDustAlpha(index: Int, progress: Float): Float {
    val (x, y) = milkyWayDustPosition(index)
    val speed = 0.4f + hash01(index, 43)
    val twinkle = 0.5f + 0.5f * sin(progress * 2f * PI.toFloat() * speed + index * 1.1f)
    return 0.03f + 0.13f * milkyWayDensity(x, y) * twinkle
}

/** One soft nebula cloud: [colorIndex] 0 violet, 1 pink, 2 blue, 3 indigo. */
data class NebulaBlob(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val colorIndex: Int,
    val alpha: Float,
)

/** The six nebula clouds, back to front. Alphas stay ≤ 0.07. */
val GALAXY_NEBULA_BLOBS: List<NebulaBlob> = listOf(
    NebulaBlob(0.12f, 0.10f, 0.42f, 0, 0.07f),
    NebulaBlob(0.92f, 0.88f, 0.36f, 1, 0.05f),
    NebulaBlob(0.85f, 0.18f, 0.30f, 2, 0.05f),
    NebulaBlob(0.18f, 0.62f, 0.30f, 3, 0.06f),
    NebulaBlob(0.55f, 0.45f, 0.24f, 1, 0.04f),
    NebulaBlob(0.38f, 0.85f, 0.26f, 0, 0.05f),
)

/** A constellation: normalised star points + the edges joining them. */
data class Constellation(
    val points: List<Pair<Float, Float>>,
    val edges: List<Pair<Int, Int>>,
)

/** Two constellations: a small kite (upper left) and a W (lower left). */
val GALAXY_CONSTELLATIONS: List<Constellation> = listOf(
    Constellation(
        points = listOf(
            0.10f to 0.34f,
            0.17f to 0.30f,
            0.24f to 0.34f,
            0.17f to 0.42f,
            0.17f to 0.50f,
        ),
        edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 3 to 4),
    ),
    Constellation(
        points = listOf(
            0.12f to 0.66f,
            0.19f to 0.60f,
            0.26f to 0.66f,
            0.33f to 0.60f,
            0.40f to 0.66f,
        ),
        edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
    ),
)

/** Planets with a moon: the 2nd and the 4th. */
fun planetHasMoon(index: Int): Boolean = index == 1 || index == 3

/** The outermost planet wears a Saturn-like ring. */
fun planetHasRing(index: Int): Boolean = index == GALAXY_PLANET_COUNT - 1

/** Moon orbit radius around its planet (normalised x; y is 0.55x, tilted like the orbits). */
fun moonOrbitRadius(planetIndex: Int): Float = 0.020f + 0.004f * planetIndex.toFloat()

/** Moon angle (radians, 0..2π): moons lap much faster than their planet. */
fun moonAngle(planetIndex: Int, progress: Float): Float {
    val lapsPerLoop = 1.6f - 0.12f * planetIndex.toFloat()
    val phase = hash01(planetIndex, 31)
    return wrap01(progress * lapsPerLoop + phase) * 2f * PI.toFloat()
}

/** Position of the moon of planet [planetIndex] around ([planetX], [planetY]). */
fun moonPosition(
    planetIndex: Int,
    progress: Float,
    planetX: Float,
    planetY: Float,
): OrbitPoint {
    val angle = moonAngle(planetIndex, progress)
    val rx = moonOrbitRadius(planetIndex)
    val ry = rx * 0.55f
    return OrbitPoint(
        x = planetX + rx * cos(angle),
        y = planetY + ry * sin(angle),
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
            colorA = primary,
            colorB = tertiary,
            alpha = 0.28f,
        )
        SeasonalDecorKind.SUMMER_SUN -> {
            drawSun(progress, secondary)
            drawFallingLeaves(
                progress = progress,
                count = 5,
                colorA = primary,
                colorB = tertiary,
                alpha = 0.22f,
            )
        }
        SeasonalDecorKind.AUTUMN_LEAVES -> drawFallingLeaves(
            progress = progress,
            count = 12,
            colorA = primary,
            colorB = secondary,
            alpha = 0.32f,
        )
        SeasonalDecorKind.WINTER_SNOW -> drawSnow(progress, tertiary, onBackground)
        SeasonalDecorKind.GALAXY_STARS -> drawGalaxy(progress, primary, secondary, tertiary, onBackground)
        SeasonalDecorKind.NONE -> Unit
    }
}

/** Leaves falling slowly, swaying side to side, gently spinning. */
private fun DrawScope.drawFallingLeaves(
    progress: Float,
    count: Int,
    colorA: Color,
    colorB: Color,
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
        val color = (if (i % 2 == 0) colorA else colorB).copy(alpha = alpha)
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
 * Galaxy (0.7.4 deep redesign): the sky is built back-to-front so the
 * depth reads even at a glance, all on the same 36 s `progress` loop:
 *
 * 1. [drawNebula] — six layered nebula clouds with brighter cores.
 * 2. [drawMilkyWay] — a diagonal band of glow + 34 dust grains.
 * 3. Far starfield (36 tiny dots, parallax layer 0) — the deep sky.
 * 4. [drawConstellations] — a kite + a W in hairline links.
 * 5. Mid starfield (26 stars, parallax layer 1) — the 0.7.3 sky.
 * 6. [drawBrightStars] — 10 near sparkles with cross flares (layer 2).
 * 7. [drawSolarSystem] — the 0.7.3 sun, now with a corona, 5 planets,
 *    moons on two of them and a ring on the outermost; anchored, so
 *    it never drifts with the parallax.
 * 8. [drawMeteors] — 4 shooting stars with long five-segment tails.
 *
 * Every layer is faint (nebula ≤ 0.07, dust ≤ 0.16) and sits behind
 * the content, so the song list and album art stay readable.
 */
private fun DrawScope.drawGalaxy(
    progress: Float,
    violet: Color,
    pink: Color,
    blue: Color,
    white: Color,
) {
    val indigo = Color(0xFF6366F1)
    drawNebula(progress, violet, pink, blue, indigo)
    drawMilkyWay(progress, violet, pink, white)
    drawStarLayer(progress, layer = 0, violet, pink, blue, white)
    drawConstellations(progress, white)
    drawStarLayer(progress, layer = 1, violet, pink, blue, white)
    drawBrightStars(progress, white, pink)
    drawSolarSystem(progress, pink, blue, white)
    drawMeteors(progress)
}

/** Six soft nebula clouds, each with a brighter core; they sway with the far layer. */
private fun DrawScope.drawNebula(progress: Float, violet: Color, pink: Color, blue: Color, indigo: Color) {
    val drift = galaxyParallaxDrift(0, progress) * size.width
    for (blob in GALAXY_NEBULA_BLOBS) {
        val center = Offset(blob.centerX * size.width + drift, blob.centerY * size.height)
        val radius = blob.radius * size.width
        // colorIndex 0 violet, 1 pink, 2 blue, 3 indigo (was a per-frame list).
        val color = when (blob.colorIndex) {
            0 -> violet
            1 -> pink
            2 -> blue
            else -> indigo
        }
        drawCircle(color = color.copy(alpha = blob.alpha), radius = radius, center = center)
        drawCircle(color = color.copy(alpha = blob.alpha * 0.7f), radius = radius * 0.55f, center = center)
    }
}

/** The Milky Way: three overlapping glows along the band + dust grains packed inside it. */
private fun DrawScope.drawMilkyWay(progress: Float, violet: Color, pink: Color, white: Color) {
    val drift = galaxyParallaxDrift(0, progress) * size.width
    for ((index, t) in MILKY_WAY_GLOW_STOPS.withIndex()) {
        // Stops are violet, pink, violet (was a per-frame list of pairs).
        val color = if (index == 1) pink else violet
        val center = Offset(t * size.width + drift, milkyWayBandCenterY(t) * size.height)
        drawCircle(color = color.copy(alpha = 0.05f), radius = size.width * 0.20f, center = center)
    }
    for (i in 0 until GALAXY_MILKY_WAY_DUST_COUNT) {
        val (nx, ny) = milkyWayDustPosition(i)
        val center = Offset(nx * size.width + drift, ny * size.height)
        val radius = (0.8f + 1.2f * hash01(i, 44)).dp.toPx()
        val alpha = milkyWayDustAlpha(i, progress)
        val color = (if (i % 4 == 0) pink else white).copy(alpha = alpha)
        drawCircle(color = color, radius = radius * 0.5f, center = center)
    }
}

/**
 * One starfield layer: far (tiny dots) or mid (dots + four-point
 * sparkles, the 0.7.3 sky). Positions, sizes and twinkle come from the
 * pure helpers, so each layer has its own depth, speed and brightness.
 */
private fun DrawScope.drawStarLayer(
    progress: Float,
    layer: Int,
    violet: Color,
    pink: Color,
    blue: Color,
    white: Color,
) {
    val count = if (layer == 0) GALAXY_FAR_STAR_COUNT else GALAXY_MID_STAR_COUNT
    for (i in 0 until count) {
        val (nx, ny) = galaxyStarPosition(i, layer, progress)
        val center = Offset(nx * size.width, ny * size.height)
        val radius = galaxyStarRadiusDp(i, layer).dp.toPx()
        val alpha = galaxyStarAlpha(i, layer, progress)
        val color = when (i % 5) {
            0 -> pink
            1 -> blue
            2 -> violet
            else -> white
        }.copy(alpha = alpha)
        if (layer == 1 && i % 3 == 0) {
            drawStar(center, radius, color)
        } else {
            // Far stars are drawn at full (already tiny) radius; mid
            // dots are half their sparkle radius so they stay pinpricks.
            val dotRadius = if (layer == 0) radius else radius * 0.45f
            drawCircle(color = color, radius = dotRadius, center = center)
        }
    }
}

/** Near layer: the 10 brightest stars — big four-point sparkles with a cross flare. */
private fun DrawScope.drawBrightStars(progress: Float, white: Color, pink: Color) {
    for (i in 0 until GALAXY_BRIGHT_STAR_COUNT) {
        val (nx, ny) = galaxyStarPosition(i, 2, progress)
        val center = Offset(nx * size.width, ny * size.height)
        val radius = galaxyStarRadiusDp(i, 2).dp.toPx()
        val alpha = galaxyStarAlpha(i, 2, progress)
        val color = (if (i % 4 == 0) pink else white).copy(alpha = alpha)
        // Cross flare: a thin horizontal + vertical glint through the sparkle.
        val flare = radius * 1.6f
        drawLine(
            color = color.copy(alpha = alpha * 0.5f),
            start = center + Offset(-flare, 0f),
            end = center + Offset(flare, 0f),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color.copy(alpha = alpha * 0.5f),
            start = center + Offset(0f, -flare),
            end = center + Offset(0f, flare),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawStar(center, radius, color)
        drawCircle(color = color.copy(alpha = alpha * 0.6f), radius = radius * 0.18f, center = center)
    }
}

/** Two faint constellations: hairline links + slightly brighter stars (brightest = a sparkle). */
private fun DrawScope.drawConstellations(progress: Float, white: Color) {
    val drift = galaxyParallaxDrift(1, progress) * size.width
    for (constellation in GALAXY_CONSTELLATIONS) {
        val pts = constellation.points.map { (nx, ny) ->
            Offset(nx * size.width + drift, ny * size.height)
        }
        for ((a, b) in constellation.edges) {
            drawLine(
                color = white.copy(alpha = 0.12f),
                start = pts[a],
                end = pts[b],
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        for ((index, pt) in pts.withIndex()) {
            if (index == 1) {
                drawStar(pt, 3.5.dp.toPx(), white.copy(alpha = 0.38f))
            } else {
                drawCircle(color = white.copy(alpha = 0.32f), radius = 2.dp.toPx(), center = pt)
            }
        }
    }
}

/**
 * The solar system: a small warm sun in the upper area, faint
 * elliptical orbit rings, and 5 planets slowly circling it — moons on
 * the 2nd and 4th, a Saturn-like ring on the outermost. The sun sits
 * right-of-centre near the top so its widest orbit (0.25 × width,
 * 0.138 × height) still fits on screen; the whole system stays in the
 * top band, behind the header content.
 */
private fun DrawScope.drawSolarSystem(
    progress: Float,
    pink: Color,
    blue: Color,
    white: Color,
) {
    // Sun at 14% height: the widest orbit (ry 0.138) just fits above it,
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
    // Corona: eight very faint rays turning almost imperceptibly.
    for (k in 0 until 8) {
        val angle = k * PI.toFloat() / 4f + progress * 0.35f
        val inner = 12.dp.toPx()
        val outer = inner + 6.dp.toPx()
        drawLine(
            color = sunWarm.copy(alpha = 0.10f),
            start = sunCenter + Offset(cos(angle) * inner, sin(angle) * inner),
            end = sunCenter + Offset(cos(angle) * outer, sin(angle) * outer),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    drawCircle(color = sunCore.copy(alpha = 0.85f), radius = 9.dp.toPx(), center = sunCenter)
    drawCircle(color = sunWarm.copy(alpha = 0.55f), radius = 6.dp.toPx(), center = sunCenter)
    // Bright rim + two tiny sunspots, so the disc reads as a sun, not a dot.
    drawCircle(
        color = sunCore.copy(alpha = 0.30f),
        radius = 10.dp.toPx(),
        center = sunCenter,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
    )
    drawCircle(
        color = Color(0xFF92400E).copy(alpha = 0.35f),
        radius = 1.2.dp.toPx(),
        center = sunCenter + Offset(3.dp.toPx(), -2.dp.toPx()),
    )
    drawCircle(
        color = Color(0xFF92400E).copy(alpha = 0.30f),
        radius = 1.dp.toPx(),
        center = sunCenter + Offset(-2.5.dp.toPx(), 3.dp.toPx()),
    )

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
        val radius = GALAXY_PLANET_RADII_DP[i].dp.toPx()
        // Inner to outer: pink, blue, white, amber, pale gold.
        val color = when (i) {
            0 -> pink
            1 -> blue
            2 -> white
            3 -> Color(0xFFFBBF24)
            else -> Color(0xFFD9B36C)
        }
        // Saturn-like ring on the outermost planet: a tilted ellipse around the disc.
        if (planetHasRing(i)) {
            drawOval(
                color = color.copy(alpha = 0.28f),
                topLeft = Offset(center.x - radius * 2.1f, center.y - radius * 0.7f),
                size = androidx.compose.ui.geometry.Size(radius * 4.2f, radius * 1.4f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }
        drawCircle(color = color.copy(alpha = 0.10f), radius = radius * 2f, center = center)
        drawCircle(color = color.copy(alpha = if (i % 2 == 0) 0.30f else 0.50f), radius = radius, center = center)
        // Moon circling its planet (2nd and 4th planets).
        if (planetHasMoon(i)) {
            val moon = moonPosition(i, progress, pos.x, pos.y)
            val moonCenter = Offset(moon.x * size.width, moon.y * size.height)
            drawCircle(color = white.copy(alpha = 0.45f), radius = 1.6.dp.toPx(), center = moonCenter)
        }
    }
}

/** Shooting stars: a glowing head + a five-segment tail that fades along its length. */
private fun DrawScope.drawMeteors(progress: Float) {
    for (i in 0 until GALAXY_METEOR_COUNT) {
        val meteor = meteorState(i, progress) ?: continue
        val head = Offset(meteor.headX * size.width, meteor.headY * size.height)
        val tail = Offset(meteor.tailX * size.width, meteor.tailY * size.height)
        // Halo around the head, so the brightest meteor reads as burning, not just white.
        drawCircle(color = Color.White.copy(alpha = meteor.alpha * 0.30f), radius = 5.dp.toPx(), center = head)
        // Tail in five segments, each fainter and thinner than the last
        // (segment tables hoisted to file level — no per-frame lists).
        for (s in METEOR_TAIL_ALPHAS.indices) {
            val from = head + (tail - head) * METEOR_TAIL_FRACTIONS[s]
            val to = head + (tail - head) * METEOR_TAIL_FRACTIONS[s + 1]
            drawLine(
                color = Color.White.copy(alpha = meteor.alpha * METEOR_TAIL_ALPHAS[s]),
                start = from,
                end = to,
                strokeWidth = METEOR_TAIL_WIDTHS_DP[s].dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawStar(head, 3.dp.toPx(), Color.White.copy(alpha = meteor.alpha * 0.8f))
        drawCircle(color = Color.White.copy(alpha = meteor.alpha), radius = 2.dp.toPx(), center = head)
    }
}

/** A four-point sparkle centred on [center] (path cached by radius — see [sparklePath]). */
private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    translate(left = center.x, top = center.y) {
        drawPath(sparklePath(radius), color)
    }
}
