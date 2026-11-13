package com.harper.ava

import androidx.compose.animation.core.FastOutSlowInEasing
import com.harper.ava.ui.common.AvaMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.1-fix1 (Track E) — the motion family, pinned. Harper: motions must
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
    fun `list placement spring is soft`() {
        assertEquals(0.85f, AvaMotion.listItemPlacement.dampingRatio)
        assertEquals(250f, AvaMotion.listItemPlacement.stiffness)
    }

    @Test
    fun `cover scale spring is soft`() {
        assertEquals(0.85f, AvaMotion.coverScaleSpring.dampingRatio)
        assertEquals(300f, AvaMotion.coverScaleSpring.stiffness)
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
