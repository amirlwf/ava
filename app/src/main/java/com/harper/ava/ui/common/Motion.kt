package com.harper.ava.ui.common

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

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

    /** Animated theme switching: every surface colour glides to its new value. */
    val themeColorTween: AnimationSpec<Color> = tween(durationMillis = 350)

    /** Skeleton shimmer sweep duration (one pass across a row). */
    const val shimmerDurationMs: Int = 1400

    /** Grace window: a skeleton only appears if loading actually lasts this long. */
    const val skeletonGraceMs: Long = 150L
}
