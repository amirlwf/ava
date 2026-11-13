package com.harper.ava.ui.common

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
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
 * Springs are real physics ([spring]), never linear: a pressed button
 * overshoots a touch and settles, the mini player rises with a soft
 * bounce, and a liked heart pops. Fades stay short tweens — a fade
 * with a spring looks drunk.
 */
object AvaMotion {
    /** Press feedback: a quick, slightly bouncy squash (play buttons, hearts). */
    val pressSpring: AnimationSpec<Float> =
        spring(dampingRatio = 0.55f, stiffness = 550f)

    /** Entrance of the floating mini player: rises 28dp and settles softly. */
    val miniPlayerEnterSpring: AnimationSpec<Dp> =
        spring(dampingRatio = 0.8f, stiffness = 320f)
    val miniPlayerEnterFade: FiniteAnimationSpec<Float> = tween(durationMillis = 200)

    /** Shared-element cover flight (list row -> Now Playing): the bounds
     *  glide on a soft, non-bouncy spring instead of snapping. */
    val coverBoundsTransform: BoundsTransform = BoundsTransform { _, _ ->
        spring(dampingRatio = 0.85f, stiffness = 300f)
    }

    /** Navigation fades: fast enough to feel instant, slow enough to read. */
    val navEnterFade: FiniteAnimationSpec<Float> = tween(durationMillis = 220)
    val navExitFade: FiniteAnimationSpec<Float> = tween(durationMillis = 180)

    /**
     * 0.1: navigation rise — screens drift up 24dp while fading in and
     * settle back 12dp while fading out. Vertical only, on purpose:
     * a horizontal slide would fight the RTL layout direction (a slide
     * that reads as "forward" in LTR reads as "back" in RTL); a gentle
     * rise reads the same in both directions. Now Playing keeps its
     * fade-only transition (it is a shared-element destination).
     */
    val navEnterSlide: FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = 260, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val navExitSlide: FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)

    /** Animated theme switching: every surface colour glides to its new value. */
    val themeColorTween: AnimationSpec<Color> = tween(durationMillis = 350)

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
