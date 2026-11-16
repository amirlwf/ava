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
 * 0.2 (Track E) — ONE motion family, softer everywhere (Harper:
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
 *
 * 0.3-feedback (Track 4) — Now Playing was still "sharp" (Harper:
 * "تیز") despite the 0.1 softening: the 260/220 ms fade is over
 * before the eye registers it, and the cover flight on a 0.85/300
 * spring lands with a snap. The Now Playing transition gets its
 * own slower, fade-dominant family: 420 ms in / 320 ms out (the
 * fade does the work, a ~16dp rise / ~12dp fall is only a whisper
 * underneath), and the cover springs soften to 0.90/250 so the
 * flight and the swipe-dismiss spring-back land instead of
 * snapping. Exit mirrors entry, slightly quicker, as exits should
 * read. Global nav transitions are untouched — the complaint was
 * Now Playing-specific.
 */
object AvaMotion {
    /** Press feedback: a soft squash that settles without a wobble
     *  (play buttons, hearts). 0.2: changed from 0.55/550 — visibly
     *  bouncy; 0.7 damping + 400 stiffness lands instead of bouncing. */
    val pressSpring: SpringSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = 400f)

    /** Entrance of the floating mini player: rises 28dp and settles softly. */
    val miniPlayerEnterSpring: SpringSpec<Dp> =
        spring(dampingRatio = 0.85f, stiffness = 300f)
    val miniPlayerEnterFade: TweenSpec<Float> = tween(durationMillis = 260)

    /** Shared-element cover flight (list row -> Now Playing): the bounds
     *  glide on a soft, non-bouncy spring instead of snapping.
     *  0.3-feedback (Track 4): was 0.85/300 — still landed with a
     *  snap; 0.90/250 settles instead.
     *  2026-11-16 (Harper: the flight felt laggy): 0.90/250 settles
     *  in ~700 ms — long after the 300 ms sheet transition, so the
     *  cover kept gliding after the sheet had landed (and on exit it
     *  floated over the underlying screen after the sheet was gone).
     *  0.85/550 lands in ~350 ms, with the sheet, and still never
     *  overshoots visibly (damping stays in the family's 0.7–0.9). */
    val coverBoundsTransform: BoundsTransform = BoundsTransform { _, _ ->
        spring(dampingRatio = 0.85f, stiffness = 550f)
    }

    /**
     * 2026-11-16 — the cover flight with reduced motion: a 1 ms
     * tween, effectively instant, so the cover never travels for
     * users who asked for stillness (run 19 rule). Used on BOTH
     * ends of the flight (SongRow and NowPlayingScreen) — the
     * transition reads one transform, and a spring on one end with
     * an instant on the other would still move.
     */
    val coverBoundsTransformReducedMotion: BoundsTransform = BoundsTransform { _, _ ->
        tween(durationMillis = 1)
    }

    /** Navigation fades: slow enough to read as soft, fast enough to
     *  feel instant. 0.2: changed from 220/180. */
    val navEnterFade: TweenSpec<Float> = tween(durationMillis = 260)
    val navExitFade: TweenSpec<Float> = tween(durationMillis = 220)

    /**
     * 0.1: navigation rise — screens drift up 24dp while fading in and
     * settle back 12dp while fading out. Vertical only, on purpose:
     * a horizontal slide would fight the RTL layout direction (a slide
     * that reads as "forward" in LTR reads as "back" in RTL); a gentle
     * rise reads the same in both directions. Now Playing keeps its
     * fade-only transition (it is a shared-element destination).
     * 0.2: 300/240 ms (was 260/200) — the rise breathes instead
     * of snapping.
     */
    val navEnterSlide: TweenSpec<IntOffset> =
        tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val navExitSlide: TweenSpec<IntOffset> =
        tween(durationMillis = 240, easing = androidx.compose.animation.core.FastOutSlowInEasing)

    /**
     * 0.3-feedback (Track 4) — the Now Playing transition family.
     * Fade-dominant: the opacity does the work (420 ms in, 320 ms
     * out — slow enough to read as soft, fast enough to never feel
     * sluggish), with only a gentle vertical rise/fall underneath
     * (~16dp in, ~12dp out; vertical only, so it reads identically
     * in RTL and LTR). Exit mirrors entry, slightly quicker. The
     * same FastOutSlowIn easing as the rest of the family, and the
     * same durations the Now Playing route and the swipe slide-off
     * below use — one family, one feeling.
     *
     * 2026-11-16 (Harper: enter/exit "very stiff, very late and
     * laggy"): the 0.3 family was the cause, read from the specs
     * themselves — (1) the fades were LINEAR (`tween(ms)` with no
     * easing), so at 100 ms the screen was only ~24% opaque and the
     * eye registered "nothing happened" for the first ~150 ms;
     * (2) the rise was ~16dp over 420 ms (~0.04 dp/ms), invisible —
     * a dissolve, not a sheet arriving; (3) the exit faded the same
     * way while the cover's slow spring outlived it. The family is
     * now a real sheet: a perceptible rise in, a full-height fall
     * out (the same gesture as the drag-dismiss slide-off below),
     * both on the family FastOutSlowIn easing so the motion answers
     * immediately, and both settled quicker (300/260 ms — nothing
     * here was slowed down to "feel premium"). The slide distances
     * are pure functions ([nowPlayingEnterOffsetY] /
     * [nowPlayingExitOffsetY]) so MotionTest pins them.
     */
    val nowPlayingEnterFade: TweenSpec<Float> =
        tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val nowPlayingExitFade: TweenSpec<Float> =
        tween(durationMillis = 260, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val nowPlayingEnterSlide: TweenSpec<IntOffset> =
        tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val nowPlayingExitSlide: TweenSpec<IntOffset> =
        tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)

    /**
     * 2026-11-16 — the Now Playing sheet's travel, as pure functions
     * of the screen height (pinned in MotionTest). Enter rises from
     * ~140dp below (height/6 on an 844dp screen): a rise the eye can
     * actually follow, not the old it/60 whisper. Exit falls the full
     * height — the sheet drops away exactly the way the drag-dismiss
     * slide-off drops it, so the back button and the drag feel like
     * the same motion. Vertical only, so both read identically in
     * RTL and LTR.
     */
    fun nowPlayingEnterOffsetY(heightPx: Int): Int = heightPx / 6
    fun nowPlayingExitOffsetY(heightPx: Int): Int = heightPx

    /**
     * 2026-11-16 — reduced motion for the Now Playing route: a short
     * calm fade and nothing else (run 19 rule — reduced motion stays
     * calm/instant). The route transitions and the cover entrance
     * both read this spec when [rememberReducedMotion] is on; the
     * slide and the cover flight are skipped entirely.
     */
    val nowPlayingReducedFade: TweenSpec<Float> =
        tween(durationMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing)

    /**
     * 0.3-feedback (Track 4) — the swipe-down slide-off: after the
     * finger releases past the threshold, the sheet glides off in
     * 340 ms on the family easing. Was an inline 300 ms tween in
     * NowPlayingScreen; a shared spec so the dismiss feels like the
     * same motion as the route exit, only driven by the hand.
     */
    val swipeDismissSlideOff: TweenSpec<Float> =
        tween(durationMillis = 340, easing = androidx.compose.animation.core.FastOutSlowInEasing)

    /** Animated theme switching: every surface colour glides to its new value. */
    val themeColorTween: TweenSpec<Color> = tween(durationMillis = 350)

    /**
     * Animated theme switching (improve loop run 14): the corner radii
     * glide to their new values on the same 350 ms as the colours, so
     * a theme change morphs the whole shape scale instead of snapping
     * the radii while the palette glides. Same duration as
     * [themeColorTween] on purpose — one theme-change tween for colour
     * and shape alike; same FastOutSlowIn easing as the rest of the
     * family, like [crossFade].
     */
    val themeRadiusTween: TweenSpec<Dp> =
        tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)

    /**
     * 0.2: the ONE cross-fade spec — song/cover/title changes and
     * play/pause icon swaps all use this 350 ms FastOutSlowIn fade, so
     * every swap in the app dissolves at the same speed. (The
     * AnimatedContent call sites used to rely on the bare `fadeIn()`
     * default, which is a different duration from the theme tween.)
     */
    val crossFade: TweenSpec<Float> =
        tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)

    /**
     * 0.2: the ONE list-item placement spec — `animateItem()` on
     * every song/playlist row uses this soft spring (damping 0.85,
     * stiffness 250), so rows glide into place instead of snapping
     * with the framework's bouncier default.
     */
    val listItemPlacement: SpringSpec<IntOffset> =
        spring(dampingRatio = 0.85f, stiffness = 250f)

    /**
     * 0.2: the soft scale spring for the Now Playing cover's
     * entrance (0.96x → 1x): the bare `scaleIn()` default spring
     * overshoots visibly at this size; this one settles. It also
     * springs the sheet back when a swipe-down is released below the
     * dismiss threshold.
     * 0.3-feedback (Track 4): was 0.85/300 — the cover entrance and
     * the spring-back still landed with a snap; 0.90/250 settles.
     * 2026-11-16: 0.90/250 settles in ~700 ms, twice the sheet's
     * 300 ms — the entrance scale was still moving after the sheet
     * landed. 0.85/500 lands in ~350 ms with the sheet. (Shared
     * with the splash logo entrance, which settles a little quicker
     * too — same family, benign.)
     */
    val coverScaleSpring: SpringSpec<Float> =
        spring(dampingRatio = 0.85f, stiffness = 500f)

    /**
     * Improve loop run 39 (design) — the Now Playing cover breathes
     * with play state: full size while playing, a gentle settle to
     * this scale when paused. The pattern both studied players use
     * (Apple Music's art "breathes"; the Marrow reference shrinks
     * its art while paused) — Ava's own version stays subtle (0.95,
     * not 0.86) because the screen is already alive with
     * AmbientGlow; the cover should settle, not dance. It rides the
     * same soft spring as the cover entrance ([coverScaleSpring]),
     * so the two scales feel like one motion, and it never pretends
     * to follow the music — it answers only play/pause. With
     * reduced motion the call site holds 1f, so the cover never
     * moves for users who asked for stillness (run 19 rule).
     */
    const val coverPausedScale: Float = 0.95f

    /**
     * Improve loop run 49 (design) — the dragged queue row lifts
     * softly: its shadow rises to the soft 8dp lift on long-press
     * and lands back on release, instead of snapping 0 → 8. 160 ms on the family easing
     * (FastOutSlowIn, like the cross-fade): quick enough to feel
     * attached to the finger, slow enough to read as a lift, not
     * a pop. The same 160 ms the Marrow reference uses for its
     * scrubber growth — the studied pattern for "a control
     * answers the hand".
     */
    val queueDragLift: TweenSpec<Float> =
        tween(durationMillis = 160, easing = androidx.compose.animation.core.FastOutSlowInEasing)

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
