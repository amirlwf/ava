package com.harper.ava

import androidx.media3.session.CommandButton
import com.harper.ava.playback.LIKE_COMMAND_ACTION
import com.harper.ava.playback.isLikeAction
import com.harper.ava.playback.likeButtonSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the 0.7.6 notification like button (pure decisions
 * in `NotificationLikeLogic`).
 *
 * What IS pinned here: the action string, the action check, and the
 * button's decisions (which heart constant, which Ava drawable,
 * which label) via [likeButtonSpec].
 *
 * What is NOT unit-tested here, and why (not a gap to paper over):
 * building a `SessionCommand` / `CommandButton` constructs an
 * `android.os.Bundle`, whose stub constructor throws on this
 * offline JVM, and reading the `DEFAULT_SESSION_COMMANDS` statics
 * initialises `Player.Commands`, which touches
 * `android.util.SparseBooleanArray` (same stub problem); building a
 * button also reads the media3 R class, which the verify
 * environment does not generate. So `likeSessionCommand`,
 * `likeButton` and `availableSessionCommandsWithNotificationActions` are verified
 * by the kotlinc type-check, by dex/javap inspection in the manual
 * build (the grant must call `addSessionCommands`, the button must
 * call `setCustomIconResId` with a heart drawable and `setSlots`
 * with `SLOT_OVERFLOW`), and by Harper's device. The tap path
 * itself (PendingIntent -> service -> session callback) needs a
 * live session and is covered the same way.
 */
class NotificationLikeTest {

    @Test
    fun `like action string is stable`() {
        assertEquals("com.harper.ava.action.TOGGLE_FAVORITE", LIKE_COMMAND_ACTION)
    }

    @Test
    fun `isLikeAction only matches the like action`() {
        assertTrue(isLikeAction(LIKE_COMMAND_ACTION))
        assertFalse(isLikeAction("com.harper.ava.action.OTHER"))
        assertFalse(isLikeAction(""))
        assertFalse(isLikeAction(null))
    }

    @Test
    fun `unfavourited spec shows the outline heart and the add label`() {
        val spec = likeButtonSpec(
            isFavorite = false,
            addLabel = "Add to favorites",
            removeLabel = "Remove from favorites",
        )
        assertEquals(CommandButton.ICON_HEART_UNFILLED, spec.icon)
        assertEquals(R.drawable.ic_notif_heart_outline, spec.iconResId)
        assertEquals("Add to favorites", spec.label)
    }

    @Test
    fun `favourited spec shows the filled heart and the remove label`() {
        val spec = likeButtonSpec(
            isFavorite = true,
            addLabel = "Add to favorites",
            removeLabel = "Remove from favorites",
        )
        assertEquals(CommandButton.ICON_HEART_FILLED, spec.icon)
        assertEquals(R.drawable.ic_notif_heart, spec.iconResId)
        assertEquals("Remove from favorites", spec.label)
    }
}
