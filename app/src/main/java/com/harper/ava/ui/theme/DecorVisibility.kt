package com.harper.ava.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.harper.ava.core.util.isReducedMotion

/**
 * Whether this screen is the current destination — the switch the
 * seasonal decor (and any other always-on animation) uses to stop
 * drawing frames nobody sees (improve run 3, 2026-11-12).
 *
 * Navigation Compose gives every destination its own NavBackStackEntry
 * lifecycle owner ([LocalLifecycleOwner]), and a back-stack entry drops
 * below RESUMED the moment another screen opens above it — and below
 * RESUMED when the app backgrounds. The initial value is read from the
 * current state (a destination composed mid-transition may still be
 * STARTED, in which case the decor appears on ON_RESUME); the observer
 * then tracks every resume/pause. Any non-resume event means "not
 * resumed": ON_PAUSE is the one that matters, the rest only confirm it.
 *
 * This lives in its own file on purpose: the unit-test compile in
 * `scripts/verify.sh` runs kotlinc without the Compose plugin, and the
 * inline `CompositionLocal.current` getter cannot be inlined there —
 * a file in the test-compile list must never read a CompositionLocal
 * directly (see the NOTE on SeasonalDecor.kt). This file is not in
 * that list; do not add it without also adding the Compose plugin to
 * the test-compile step.
 */
@Composable
fun rememberIsResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * Whether the user asked the system for no animation (improve run 19,
 * 2026-11-14) — Settings > Accessibility > Remove animations, or
 * Developer options > Animator duration scale set to off. Both write 0
 * to [Settings.Global.ANIMATOR_DURATION_SCALE]; [isReducedMotion]
 * pins that rule (0 means reduced, anything else — including 0.5x —
 * does not). The 3-arg `getFloat` returns the 1f default when the
 * setting is absent (older Android), so no exception handling is
 * needed.
 *
 * Read once per composition (remembered on the context): the ambient
 * loops freeze for the life of the process when this is true, and a
 * changed setting takes effect on the next cold start. Watching the
 * setting live would need a ContentObserver; the toggle is an
 * accessibility choice users set once, not something they flip
 * mid-session, so the simpler read is the right trade.
 *
 * Lives in this file for the same reason as [rememberIsResumed]:
 * it reads a CompositionLocal ([LocalContext]), which the unit-test
 * compile in `scripts/verify.sh` cannot inline — a file in the
 * test-compile list must never read one directly. This file is not
 * in that list; do not add it without also adding the Compose plugin
 * to the test-compile step.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        isReducedMotion(
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ),
        )
    }
}
