package com.harper.ava.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Haptics (0.7.0, spec §13): one semantic helper for every touch that
 * deserves a buzz, so screens never reach for [HapticFeedback] directly
 * and the strengths stay consistent.
 *
 * Strengths follow the platform idiom: a confirmed action (play/pause,
 * like, theme change) gets a firm [HapticFeedbackType.LongPress]; a
 * light tick while dragging or tapping a row gets
 * [HapticFeedbackType.TextHandleMove]; a selection-style change gets
 * [HapticFeedbackType.ContextClick]. On devices without a vibrator these
 * are no-ops — nothing in the app depends on them.
 *
 * Wiring notes: [dragTick] is for the queue drag handle — the call site
 * lives in QueueScreen (not owned by the motion round), so the helper is
 * ready there and the one-line call is left to the queue owner.
 * [themeChange] is fired from MainActivity when the persisted theme
 * actually changes (see the theme-change effect there), because the
 * Appearance screen itself is owned by another round.
 */
class AvaHaptics(private val feedback: HapticFeedback) {
    /** Play / pause (mini player, Now Playing): a firm confirmation buzz. */
    fun playPause() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Skip / next, row tap, chip tap: a light tick, not a thud. */
    fun tap() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** Liking / unliking a song: firm, so the heart feels answered. */
    fun like() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** One tick per row crossed while dragging the queue. */
    fun dragTick() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** Theme / accent change confirmed. */
    fun themeChange() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** Generic toggle (shuffle / repeat / speed): firm, like play/pause. */
    fun toggle() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

/** Remembers the app-wide [AvaHaptics] for the current composition. */
@Composable
fun rememberAvaHaptics(): AvaHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { AvaHaptics(feedback) }
}
