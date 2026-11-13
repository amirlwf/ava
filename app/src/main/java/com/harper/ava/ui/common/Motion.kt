package com.harper.ava.ui.common

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * Motion Design System (0.7.0, spec §13): the single home for every
 * animation spec in the app. Screens never invent their own durations
 * or spring constants — they read them from here, so a tap feels the
 * same everywhere and a theme change never snaps.
 *
 * 0.1-fix1 (Track E) — ONE motion family, softer everywhere (Harper:
 * "animations must be softer, spend a lot of time on them"):
 *
 * - Tweens all share [FastOutSlowInEasing]: one easing family
 *   app-wide, so no screen accelerates differently from another.
 *   Fades and slides are a touch longer than in 0.1 (260/220 ms
 *   fades, 300/240 ms slides, 350 ms cross-fades) — slow enough to
 *   read as soft, fast enough to never feel sluggish.
 * - Springs are real physics ([spring]), never linear — but SOFT
 *   physics: damping ratios 0.7–0.9, so nothing overshoots or
 *   bounces visibly. A pressed button settles instead of wobbling;
 *   the mini player rises and lands; a liked heart pops gently
 *   (1.2x, not 1.3x). The shared-element cover flight keeps its
 *   0.85/300 spring — it was already in the soft family.
 * - Ambient motion (Galaxy 36 s, aurora 48 s, AmbientGlow 24/31 s,
 *   skeleton shimmer 1400 ms) stays Linear and slow: calm, never
 *   competing with the user's actions. Motion that answers an action
 *   is welcome; non-user-triggered motion stays sparing and
 *   deliberate, one orchestrated moment at a time.
 * - Where two motions fought (nav fade + press scale on the same
 *   tap), the softer combination wins: the press scale is gentle
 *   (0.92x, soft spring) and the nav fade is slow, so neither
 *   shouts over the other.
 */
object AvaMotion {
    /** Press feedback: a soft squash that settles without a wobble
     *  (play buttons, hearts). 0.1-fix1: was 0.55/550 — visibly
     *  bouncy; 0.7 damping + 400 stiffness lands instead of bouncing. */
    val pressSpring: SpringSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = 400f)

    /** Entrance of the floating mini player: rises 28dp and settles softly. */
    val miniPlayerEnterSpring: SpringSpec<Dp> =
        spring(dampingRatio = 0.85f, stiffness = 300f)
    val miniPlayerEnterFade: TweenSpec<Float> = tween(durationMillis = 260)

    /** Shared-element cover flight (list row -> Now Playing): the bounds
     *  glide on a soft, non-bouncy spring instead of snapping. */
    val coverBoundsTransform: BoundsTransform = BoundsTransform { _, _ ->
        spring(dampingRatio = 0.85f, stiffness = 300f)
    }

    /** Navigation fades: slow enough to read as soft, fast enough to
     *  feel instant. 0.1-fix1: was 220/180. */
    val navEnterFade: TweenSpec<Float> = tween(durationMillis = 260)
    val navExitFade: TweenSpec<Float> = tween(durationMillis = 220)

    /**
     * 0.1: navigation rise — screens drift up 24dp while fading in and
     * settle back 12dp while fading out. Vertical only, on purpose:
     * a horizontal slide would fight the RTL layout direction (a slide
     * that reads as "forward" in LTR reads as "back" in RTL); a gentle
     * rise reads the same in both directions. Now Playing keeps its
     * fade-only transition (it is a shared-element destination).
     * 0.1-fix1: 300/240 ms (was 260/200) — the rise breathes instead
     * of snapping.
     */
    val navEnterSlide: TweenSpec<IntOffset> =
        tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val navExitSlide: TweenSpec<IntOffset> =
        tween(durationMillis = 240, easing = androidx.compose.animation.core.FastOutSlowInEasing)

    /** Animated theme switching: every surface colour glides to its new value. */
    val themeColorTween: TweenSpec<Color> = tween(durationMillis = 350)

    /**
     * 0.1-fix1: the ONE cross-fade spec — song/cover/title changes and
     * play/pause icon swaps all use this 350 ms FastOutSlowIn fade, so
     * every swap in the app dissolves at the same speed. (The
     * AnimatedContent call sites used to rely on the bare `fadeIn()`
     * default, which is a different duration from the theme tween.)
     */
    val crossFade: TweenSpec<Float> =
        tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)

    /**
     * 0.1-fix1: the ONE list-item placement spec — `animateItem()` on
     * every song/playlist row uses this soft spring (damping 0.85,
     * stiffness 250), so rows glide into place instead of snapping
     * with the framework's bouncier default.
     */
    val listItemPlacement: SpringSpec<IntOffset> =
        spring(dampingRatio = 0.85f, stiffness = 250f)

    /**
     * 0.1-fix1: the soft scale spring for the Now Playing cover's
     * entrance (0.96x → 1x): the bare `scaleIn()` default spring
     * overshoots visibly at this size; this one settles.
     */
    val coverScaleSpring: SpringSpec<Float> =
        spring(dampingRatio = 0.85f, stiffness = 300f)

    /** Skeleton shimmer sweep duration (one pass across a row). */
    const val shimmerDurationMs: Int = 1400

    /** Grace window: a skeleton only appears if loading actually lasts this long. */
    const val skeletonGraceMs: Long = 150L
}

/**
 * 0.1: press squash for a button, from its [InteractionSource].
 *
 * The scale a pressed button shrinks to on [AvaMotion.pressSpring]
 * (0.88x for hero play buttons, 0.92x elsewhere) and springs back
 * from on release. One helper so every press feels identical —
 * MiniPlayer and Now Playing used to inline the same three lines.
 */
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.92f,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = AvaMotion.pressSpring,
        label = "press-scale",
    ).value
}
