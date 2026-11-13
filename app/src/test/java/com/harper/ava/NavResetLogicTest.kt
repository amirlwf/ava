package com.harper.ava

import com.harper.ava.core.util.bottomBarNavOptions
import com.harper.ava.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.3-feedback (Track 5) — tapping a bottom-bar section icon resets
 * that section to its root. The options are built by [bottomBarNavOptions]
 * (pure, offline): the test pins the pattern — pop up to the Home
 * root, non-inclusive, with state saved and restored, single-top —
 * for every bottom-bar section, so a future edit cannot silently
 * drop the reset.
 */
class NavResetLogicTest {

    @Test
    fun `bottom bar tap pops to the home root and keeps section state`() {
        val options = bottomBarNavOptions()
        // The graph start is the pop target, never popped itself.
        assertEquals(Destination.Home.route, options.popUpToRoute)
        assertFalse(options.isPopUpToInclusive())
        // The section's own state survives the pop and comes back.
        assertTrue(options.shouldPopUpToSaveState())
        assertTrue(options.shouldRestoreState())
        // Tapping the section already on screen stacks nothing.
        assertTrue(options.shouldLaunchSingleTop())
    }

    @Test
    fun `the reset options are the same for every bottom bar section`() {
        assertEquals(4, Destination.bottomBar.size)
        for (destination in Destination.bottomBar) {
            val options = bottomBarNavOptions()
            assertEquals(
                "${destination.route} pops to home",
                Destination.Home.route,
                options.popUpToRoute,
            )
            assertTrue("${destination.route} single top", options.shouldLaunchSingleTop())
            assertTrue("${destination.route} restores state", options.shouldRestoreState())
        }
    }
}
