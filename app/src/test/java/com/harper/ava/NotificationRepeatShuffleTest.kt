package com.harper.ava

import androidx.media3.session.CommandButton
import com.harper.ava.data.repository.RepeatMode
import com.harper.ava.playback.REPEAT_COMMAND_ACTION
import com.harper.ava.playback.SHUFFLE_COMMAND_ACTION
import com.harper.ava.playback.isRepeatAction
import com.harper.ava.playback.isShuffleAction
import com.harper.ava.playback.repeatButtonSpec
import com.harper.ava.playback.shuffleButtonSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.2-fix (Track 1) — the notification repeat + shuffle buttons.
 *
 * Harper's 0.2 report: the notification had no repeat and no
 * shuffle. The buttons ride the same session-command mechanism as
 * the like button, so the pure decisions are pinned here the same
 * way: which icon constant, which Ava drawable, which label.
 *
 * The Media3-typed assembly (`repeatButton`, `shuffleButton`, the
 * session commands) cannot run in this offline JVM suite — building
 * a `SessionCommand` / `CommandButton` constructs an
 * `android.os.Bundle`, whose stub constructor throws, and the media3
 * R class is not generated here — so those are verified by the
 * kotlinc type-check, by dex/javap inspection in the manual build,
 * and by Harper's device (same boundary as the like button).
 */
class NotificationRepeatShuffleTest {

    @Test
    fun `repeat action strings are the notification custom actions`() {
        assertEquals("com.harper.ava.action.TOGGLE_REPEAT", REPEAT_COMMAND_ACTION)
        assertEquals("com.harper.ava.action.TOGGLE_SHUFFLE", SHUFFLE_COMMAND_ACTION)
        assertTrue(isRepeatAction(REPEAT_COMMAND_ACTION))
        assertFalse(isRepeatAction(SHUFFLE_COMMAND_ACTION))
        assertFalse(isRepeatAction(null))
        assertTrue(isShuffleAction(SHUFFLE_COMMAND_ACTION))
        assertFalse(isShuffleAction(REPEAT_COMMAND_ACTION))
        assertFalse(isShuffleAction(null))
    }

    @Test
    fun `repeat off offers repeat all`() {
        // The label names the mode the tap switches TO (OFF -> all).
        val spec = repeatButtonSpec(RepeatMode.OFF, "Repeat off", "Repeat all", "Repeat one")
        assertEquals(CommandButton.ICON_REPEAT_OFF, spec.icon)
        assertEquals(R.drawable.ic_notif_repeat_off, spec.iconResId)
        assertEquals("Repeat all", spec.label)
    }

    @Test
    fun `repeat all offers repeat one`() {
        val spec = repeatButtonSpec(RepeatMode.ALL, "Repeat off", "Repeat all", "Repeat one")
        assertEquals(CommandButton.ICON_REPEAT_ALL, spec.icon)
        assertEquals(R.drawable.ic_notif_repeat, spec.iconResId)
        assertEquals("Repeat one", spec.label)
    }

    @Test
    fun `repeat one offers repeat off`() {
        val spec = repeatButtonSpec(RepeatMode.ONE, "Repeat off", "Repeat all", "Repeat one")
        assertEquals(CommandButton.ICON_REPEAT_ONE, spec.icon)
        assertEquals(R.drawable.ic_notif_repeat_one, spec.iconResId)
        assertEquals("Repeat off", spec.label)
    }

    @Test
    fun `shuffle on offers to turn it off`() {
        val spec = shuffleButtonSpec(true, "Turn shuffle on", "Turn shuffle off")
        assertEquals(CommandButton.ICON_SHUFFLE_ON, spec.icon)
        assertEquals(R.drawable.ic_notif_shuffle, spec.iconResId)
        assertEquals("Turn shuffle off", spec.label)
    }

    @Test
    fun `shuffle off offers to turn it on`() {
        val spec = shuffleButtonSpec(false, "Turn shuffle on", "Turn shuffle off")
        assertEquals(CommandButton.ICON_SHUFFLE_OFF, spec.icon)
        assertEquals(R.drawable.ic_notif_shuffle_off, spec.iconResId)
        assertEquals("Turn shuffle on", spec.label)
    }
}
