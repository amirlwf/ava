package com.harper.ava.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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
