package com.harper.ava

import com.harper.ava.core.util.SWIPE_DISMISS_FLING_VELOCITY_PX_PER_SEC
import com.harper.ava.core.util.SWIPE_DISMISS_FRACTION
import com.harper.ava.core.util.shouldDismissOnRelease
import com.harper.ava.core.util.swipeDismissThresholdPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.1-fix1 (Track F) — swipe-down-to-dismiss decisions for Now Playing.
 */
class SwipeDismissLogicTest {

    @Test
    fun `threshold is a quarter of the sheet height`() {
        assertEquals(0.25f, SWIPE_DISMISS_FRACTION)
        assertEquals(200f, swipeDismissThresholdPx(800f))
        assertEquals(0f, swipeDismissThresholdPx(0f))
    }

    @Test
    fun `past the threshold dismisses even with no velocity`() {
        assertTrue(shouldDismissOnRelease(offsetPx = 201f, velocityPxPerSec = 0f, thresholdPx = 200f))
        assertTrue(shouldDismissOnRelease(offsetPx = 200f, velocityPxPerSec = 0f, thresholdPx = 200f))
    }

    @Test
    fun `below the threshold a slow release springs back`() {
        assertFalse(shouldDismissOnRelease(offsetPx = 199f, velocityPxPerSec = 0f, thresholdPx = 200f))
        assertFalse(shouldDismissOnRelease(offsetPx = 100f, velocityPxPerSec = 400f, thresholdPx = 200f))
    }

    @Test
    fun `below the threshold a fast downward fling dismisses`() {
        assertTrue(
            shouldDismissOnRelease(
                offsetPx = 50f,
                velocityPxPerSec = SWIPE_DISMISS_FLING_VELOCITY_PX_PER_SEC,
                thresholdPx = 200f,
            ),
        )
        assertTrue(
            shouldDismissOnRelease(
                offsetPx = 50f,
                velocityPxPerSec = 2000f,
                thresholdPx = 200f,
            ),
        )
    }

    @Test
    fun `an upward fling below the threshold springs back`() {
        assertFalse(shouldDismissOnRelease(offsetPx = 100f, velocityPxPerSec = -2000f, thresholdPx = 200f))
        // Past the threshold the sheet is visibly over the line, so it
        // dismisses even if the release velocity points back up.
        assertTrue(shouldDismissOnRelease(offsetPx = 250f, velocityPxPerSec = -2000f, thresholdPx = 200f))
    }

    @Test
    fun `an upward drag never dismisses`() {
        assertFalse(shouldDismissOnRelease(offsetPx = -50f, velocityPxPerSec = 0f, thresholdPx = 200f))
    }
}
