package com.harper.ava.core.util

import androidx.navigation.navOptions
import com.harper.ava.ui.navigation.Destination

/**
 * Bottom-bar section reset (0.3-feedback, Track 5) — the NavOptions
 * every bottom-bar tap navigates with, extracted from the inline
 * lambda in MainActivity so the offline JUnit suite can pin the
 * pattern instead of trusting it by inspection.
 *
 * Tapping a section's icon RESETS that section: `popUpTo(Home)` pops
 * everything above the graph start (Home) — e.g. Home → Settings →
 * tap Home lands back on the main Home screen; Library →
 * PlaylistDetail → tap Library lands back on the Library root — and
 * then navigates to the tapped section. `saveState` + `restoreState`
 * keep the section's own state (scroll position, ViewModel) across
 * the pop, so the reset is navigation-only: playback is never
 * stopped or disturbed, and the queue is untouched.
 * `launchSingleTop` makes tapping the section you are already on a
 * no-op instead of stacking a duplicate destination.
 * `popUpTo` is NON-inclusive: Home itself stays on the stack as the
 * graph start, so the back stack is never emptied.
 */
fun bottomBarNavOptions() = navOptions {
    popUpTo(Destination.Home.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
