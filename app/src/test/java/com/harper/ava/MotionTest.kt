package com.harper.ava

import androidx.compose.animation.core.FastOutSlowInEasing
import com.harper.ava.ui.common.AvaMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.2 (Track E) — the motion family, pinned. Harper: motions must
 * be softer, and the softness must be a decision in one place, not an
 * accident per screen. These tests pin the shared specs in
 * [AvaMotion]: one easing family (FastOutSlowIn) for every tween,
 * soft springs (damping 0.7–0.9, no visible overshoot) for every
 * physics motion. If a screen invents its own duration or spring it
 * will not fail here — this pins the shared values, not the call
 * sites; the call sites are covered by the kotlinc type-check (they
 * read the specs from AvaMotion).
 */
class MotionTest {

    @Test
    fun `press spring is soft and settles without a bounce`() {
        assertEquals(0.7f, AvaMotion.pressSpring.dampingRatio)
        assertEquals(400f, AvaMotion.pressSpring.stiffness)
        assertTrue(AvaMotion.pressSpring.dampingRatio in 0.7f..0.9f)
    }

    @Test
    fun `mini player enters softly`() {
        assertEquals(0.85f, AvaMotion.miniPlayerEnterSpring.dampingRatio)
        assertEquals(300f, AvaMotion.miniPlayerEnterSpring.stiffness)
        assertEquals(260, AvaMotion.miniPlayerEnterFade.durationMillis)
    }

    @Test
    fun `navigation fades and slides are slow and share one easing`() {
        assertEquals(260, AvaMotion.navEnterFade.durationMillis)
        assertEquals(220, AvaMotion.navExitFade.durationMillis)
        assertEquals(300, AvaMotion.navEnterSlide.durationMillis)
        assertEquals(240, AvaMotion.navExitSlide.durationMillis)
        for (spec in listOf(
            AvaMotion.navEnterFade,
            AvaMotion.navExitFade,
            AvaMotion.navEnterSlide,
            AvaMotion.navExitSlide,
        )) {
            assertEquals(FastOutSlowInEasing, spec.easing)
        }
    }

    @Test
    fun `cross fade matches the theme tween duration and easing`() {
        assertEquals(350, AvaMotion.crossFade.durationMillis)
        assertEquals(FastOutSlowInEasing, AvaMotion.crossFade.easing)
        assertEquals(AvaMotion.crossFade.durationMillis, AvaMotion.themeColorTween.durationMillis)
    }

    @Test
    fun `theme radii glide on the same tween as the theme colours`() {
        // Improve loop run 14: the shape scale morphs with the palette
        // on a theme change — radii must never snap while colours glide,
        // so the radius tween shares the colour tween's duration and
        // the family easing (FastOutSlowIn, like the cross-fade).
        assertEquals(350, AvaMotion.themeRadiusTween.durationMillis)
        assertEquals(FastOutSlowInEasing, AvaMotion.themeRadiusTween.easing)
        assertEquals(
            AvaMotion.themeColorTween.durationMillis,
            AvaMotion.themeRadiusTween.durationMillis,
        )
    }

    @Test
    fun `list placement spring is soft`() {
        assertEquals(0.85f, AvaMotion.listItemPlacement.dampingRatio)
        assertEquals(250f, AvaMotion.listItemPlacement.stiffness)
    }

    @Test
    fun `cover scale spring is soft`() {
        // 0.3-feedback (Track 4): was 0.85/300 — the cover entrance
        // and the swipe-dismiss spring-back still landed with a snap.
        // 2026-11-16 (Harper: the flight felt laggy): 0.90/250
        // settles in ~700 ms, twice the 300 ms sheet — the entrance
        // scale was still moving after the sheet landed. 0.85/500
        // lands in ~350 ms with the sheet, and still never overshoots
        // visibly (damping stays in the family's 0.7–0.9, pinned
        // by the test below).
        assertEquals(0.85f, AvaMotion.coverScaleSpring.dampingRatio)
        assertEquals(500f, AvaMotion.coverScaleSpring.stiffness)
    }

    @Test
    fun `now playing transition is a sheet arriving and leaving`() {
        // 0.3-feedback (Track 4): Harper called the Now Playing
        // enter/exit "sharp" — the 260/220 ms fade was over before
        // the eye registered it. The dedicated family was slower and
        // fade-led: 420 ms in, 320 ms out.
        // 2026-11-16 (Harper: "very stiff, very late and laggy"):
        // that family WAS the cause — the fades were LINEAR, so at
        // 100 ms the screen was only ~24% opaque (reads as "late"),
        // and the rise was ~16dp over 420 ms (invisible — a dissolve,
        // not a sheet arriving). Now a real sheet: 300 ms FastOutSlowIn
        // fade + a ~140dp rise in, 260 ms fade + a full-height fall
        // out (the sheet drops away exactly like the drag-dismiss
        // slide-off, so back and drag feel like one motion). Nothing
        // here was slowed down to "feel premium" — the enter is
        // quicker than the 420 ms it replaces.
        assertEquals(300, AvaMotion.nowPlayingEnterFade.durationMillis)
        assertEquals(260, AvaMotion.nowPlayingExitFade.durationMillis)
        assertEquals(300, AvaMotion.nowPlayingEnterSlide.durationMillis)
        assertEquals(300, AvaMotion.nowPlayingExitSlide.durationMillis)
        assertTrue(
            AvaMotion.nowPlayingEnterFade.durationMillis >
                AvaMotion.nowPlayingExitFade.durationMillis,
        )
        assertTrue(
            AvaMotion.nowPlayingEnterFade.durationMillis >
                AvaMotion.navEnterFade.durationMillis,
        )
        for (spec in listOf(
            AvaMotion.nowPlayingEnterFade,
            AvaMotion.nowPlayingExitFade,
            AvaMotion.nowPlayingEnterSlide,
            AvaMotion.nowPlayingExitSlide,
            AvaMotion.swipeDismissSlideOff,
        )) {
            assertEquals(FastOutSlowInEasing, spec.easing)
        }
        // The swipe slide-off matches the exit's family, not the old
        // inline 300 ms.
        assertEquals(340, AvaMotion.swipeDismissSlideOff.durationMillis)
        // The rise is perceptible (height/6 ≈ 140dp on an 844dp
        // screen — the old it/60 was ~16dp, a whisper), and the exit
        // falls the full height.
        assertEquals(140, AvaMotion.nowPlayingEnterOffsetY(840))
        assertEquals(840, AvaMotion.nowPlayingExitOffsetY(840))
        assertTrue(AvaMotion.nowPlayingEnterOffsetY(840) > 100)
    }

    @Test
    fun `now playing reduced motion is a short calm fade`() {
        // 2026-11-16 (run 19 rule): with reduced motion the Now
        // Playing route fades in 100 ms with no slide and no cover
        // flight — calm and instant, never a 300 ms sheet.
        assertEquals(100, AvaMotion.nowPlayingReducedFade.durationMillis)
        assertEquals(FastOutSlowInEasing, AvaMotion.nowPlayingReducedFade.easing)
        assertTrue(
            AvaMotion.nowPlayingReducedFade.durationMillis <
                AvaMotion.nowPlayingEnterFade.durationMillis,
        )
    }

    @Test
    fun `paused cover settles gently below full size`() {
        // Improve loop run 39: the Now Playing cover breathes with
        // play state — full size while playing, a gentle settle
        // while paused. Subtle on purpose (0.95, not 0.86): the
        // screen is already alive with AmbientGlow, so the cover
        // settles instead of dancing; and it must stay a settle,
        // never a grow above 1.
        assertEquals(0.95f, AvaMotion.coverPausedScale)
        assertTrue(AvaMotion.coverPausedScale in 0.9f..1f)
        assertTrue(AvaMotion.coverPausedScale < 1f)
    }

    @Test
    fun `dragged queue row lifts softly`() {
        // Improve loop run 49: the dragged row's shadow rises to
        // the soft lift and lands back on release — 160 ms on the
        // family easing, quick enough to feel attached to the
        // finger, slow enough to read as a lift, not a pop.
        assertEquals(160, AvaMotion.queueDragLift.durationMillis)
        assertEquals(FastOutSlowInEasing, AvaMotion.queueDragLift.easing)
    }

    @Test
    fun `every spring in the family is underdamped but never bouncy`() {
        // Damping < 1 settles with a whisper of overshoot; >= 0.7 the
        // overshoot is invisible at these sizes. Below 0.7 the press
        // visibly wobbled (0.1: 0.55).
        for (spec in listOf(
            AvaMotion.pressSpring,
            AvaMotion.miniPlayerEnterSpring,
            AvaMotion.listItemPlacement,
            AvaMotion.coverScaleSpring,
        )) {
            assertTrue("${spec.dampingRatio}", spec.dampingRatio in 0.7f..0.9f)
        }
    }
}
