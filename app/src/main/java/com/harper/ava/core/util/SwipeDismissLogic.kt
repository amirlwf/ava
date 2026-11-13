package com.harper.ava.core.util

/**
 * Swipe-down-to-dismiss for Now Playing (0.1-fix1, Track F) — the pure
 * decisions, so the offline JUnit suite can pin them. The gesture
 * itself lives in `NowPlayingScreen` (a `draggable` on the content
 * Column, following the finger; past the threshold it slides off and
 * pops the back stack, below it it springs back).
 *
 * The screen does not scroll (its lyrics live on a separate
 * LyricsScreen), so the drag never fights vertical scrolling: the
 * only vertical gestures on this screen are this dismiss and the
 * (horizontal) seek slider.
 */

/** Dismiss once the sheet has been dragged this fraction of its height. */
const val SWIPE_DISMISS_FRACTION = 0.25f

/** A fast downward fling dismisses even below the distance threshold. */
const val SWIPE_DISMISS_FLING_VELOCITY_PX_PER_SEC = 800f

/**
 * Whether releasing the drag at [offsetPx] (downward positive) with
 * [velocityPxPerSec] (downward positive) dismisses the screen.
 * Dismisses when the drag passed [thresholdPx], or when the finger
 * flung downward fast enough — an upward fling never dismisses,
 * however far the sheet was dragged.
 */
fun shouldDismissOnRelease(
    offsetPx: Float,
    velocityPxPerSec: Float,
    thresholdPx: Float,
    flingVelocityPxPerSec: Float = SWIPE_DISMISS_FLING_VELOCITY_PX_PER_SEC,
): Boolean {
    if (offsetPx < 0f) return false
    if (offsetPx >= thresholdPx) return true
    return velocityPxPerSec >= flingVelocityPxPerSec
}

/** Dismiss threshold in pixels for a sheet [heightPx] tall. */
fun swipeDismissThresholdPx(heightPx: Float): Float = heightPx * SWIPE_DISMISS_FRACTION
