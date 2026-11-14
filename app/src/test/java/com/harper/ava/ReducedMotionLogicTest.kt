package com.harper.ava

import com.harper.ava.core.util.isReducedMotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the reduced-motion predicate ([isReducedMotion]).
 * The predicate is the one rule the whole feature hangs on — a wrong
 * comparison here would freeze the app's ambient motion for everyone
 * (or for nobody) — so the boundary is pinned directly: exactly 0
 * means the user asked for no animation, anything else does not.
 * The `Settings.Global` read itself lives in `rememberReducedMotion`
 * (DecorVisibility.kt) and cannot run on the JVM.
 */
class ReducedMotionLogicTest {

    @Test
    fun `animator scale 0 means reduced motion`() {
        assertTrue(isReducedMotion(0f))
    }

    @Test
    fun `animator scale 1 means full motion`() {
        assertFalse(isReducedMotion(1f))
    }

    @Test
    fun `animator scale 0_5x is faster motion, not reduced motion`() {
        assertFalse(isReducedMotion(0.5f))
    }

    @Test
    fun `negative zero is still zero`() {
        assertTrue(isReducedMotion(-0f))
    }

    @Test
    fun `NaN is not reduced motion`() {
        assertFalse(isReducedMotion(Float.NaN))
    }
}
