package com.harper.ava

import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import com.harper.ava.core.util.deriveAccentColors
import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.playback.AvaMediaNotificationProvider
import com.harper.ava.playback.AvaPlaybackService
import com.harper.ava.playback.avaNotificationIconRes
import com.harper.ava.playback.notificationAccentArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for the 0.7.5 notification provider's pure logic and
 * structure.
 *
 * 0.7.5 reverted to the 0.7.3 subclass for the second and final time:
 * the 0.7.4 RemoteViews retry crashed on play on Harper's Redmi Note
 * 12 / Android 15 with the identical exception even with the PNG
 * small icon, so the RemoteViews path itself is the cause and is
 * never repeated (see the provider KDoc). What lives here, testable
 * without an Android runtime:
 *
 * - Structure: the provider really IS a [DefaultMediaNotificationProvider]
 *   subclass again, it has the live-accent `updateAccentColor` hook
 *   and NO `release()` / `ACTION_TOGGLE_FAVORITE` leftovers from the
 *   RemoteViews rounds.
 * - The theme accent: [notificationAccentArgb] reads the user's
 *   [AppTheme] (dark primary), and a user-picked custom colour wins.
 * - The Ava action drawables and the PNG small icon are present in
 *   resources; the channel id is set and the notification id matches
 *   Media3's default.
 *
 * Building the notification, the shade rendering and the live accent
 * update on a theme change need a device/emulator and are NOT covered
 * here — above all whether playback still crashes on Harper's phone
 * (the 0.7.3 base played fine there, which is the whole point of
 * this revert).
 */
class CustomNotificationLogicTest {

    @Test
    fun `provider is the Media3 subclass again, not a from-scratch provider`() {
        assertEquals(
            DefaultMediaNotificationProvider::class.java,
            AvaMediaNotificationProvider::class.java.superclass,
        )
    }

    @Test
    fun `provider has the live accent hook and no RemoteViews leftovers`() {
        // updateAccentColor(Int) is the 0.7.5 live-theme hook the
        // service calls when the settings change.
        val update = runCatching {
            AvaMediaNotificationProvider::class.java.getDeclaredMethod(
                "updateAccentColor",
                Int::class.javaPrimitiveType,
            )
        }.getOrNull()
        assertNotNull(update)
        // The RemoteViews rounds' release() ticker scope and like
        // custom action must be gone with the revert.
        try {
            AvaMediaNotificationProvider::class.java.getDeclaredMethod("release")
            fail("release() must not exist on the reverted subclass provider")
        } catch (_: NoSuchMethodException) {
            // Expected: no ticker scope to release.
        }
        try {
            AvaMediaNotificationProvider::class.java.getDeclaredField("ACTION_TOGGLE_FAVORITE")
            fail("ACTION_TOGGLE_FAVORITE must not exist on the reverted subclass provider")
        } catch (_: NoSuchFieldException) {
            // Expected: no like custom action.
        }
    }

    @Test
    fun `notification accent comes from the chosen theme`() {
        // Pinned dark primaries from ui/theme/AppTheme.kt.
        assertEquals(0xFF7C5CFF.toInt(), notificationAccentArgb(AppTheme.VIOLET, null))
        assertEquals(0xFFC4B5FD.toInt(), notificationAccentArgb(AppTheme.GALAXY, null))
        assertNotEquals(
            notificationAccentArgb(AppTheme.VIOLET, null),
            notificationAccentArgb(AppTheme.GALAXY, null),
        )
    }

    @Test
    fun `a user-picked custom colour wins over the theme`() {
        val custom = 0xFF112233.toInt()
        assertEquals(
            deriveAccentColors(custom).primary,
            notificationAccentArgb(AppTheme.VIOLET, custom),
        )
        assertEquals(
            deriveAccentColors(custom).primary,
            notificationAccentArgb(AppTheme.GALAXY, custom),
        )
    }

    @Test
    fun `Ava action drawables map the three stock media buttons`() {
        assertEquals(R.drawable.ic_notif_prev, avaNotificationIconRes(CommandButton.ICON_PREVIOUS))
        assertEquals(R.drawable.ic_notif_play, avaNotificationIconRes(CommandButton.ICON_PLAY))
        assertEquals(R.drawable.ic_notif_pause, avaNotificationIconRes(CommandButton.ICON_PAUSE))
        assertEquals(R.drawable.ic_notif_next, avaNotificationIconRes(CommandButton.ICON_NEXT))
        // Anything else keeps the stock Media3 icon.
        assertEquals(null, avaNotificationIconRes(CommandButton.ICON_STOP))
    }

    @Test
    fun `small icon and Ava action drawables are present in resources`() {
        // The small icon must exist (the 0.7.3 PNG in all five
        // densities); a missing icon is exactly the kind of resource
        // failure the system rejects at startForeground.
        assertNotEquals(0, R.drawable.ic_ava_notification)
        assertNotEquals(0, R.drawable.ic_notif_prev)
        assertNotEquals(0, R.drawable.ic_notif_play)
        assertNotEquals(0, R.drawable.ic_notif_pause)
        assertNotEquals(0, R.drawable.ic_notif_next)
    }

    @Test
    fun `notification channel and id are set`() {
        assertTrue(AvaPlaybackService.CHANNEL_ID.isNotEmpty())
        assertEquals(
            DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
            AvaPlaybackService.NOTIFICATION_ID,
        )
    }
}
