package com.harper.ava

import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import com.harper.ava.playback.AvaMediaNotificationProvider
import com.harper.ava.playback.AvaPlaybackService
import com.harper.ava.playback.notificationIsOngoing
import com.harper.ava.playback.notificationLikeIconRes
import com.harper.ava.playback.notificationPlayPauseIconRes
import com.harper.ava.playback.notificationProgressPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the 0.7.4 custom notification provider's pure logic
 * and structure.
 *
 * 0.7.4 rebuilt the 0.7.2 from-scratch custom provider with the 0.7.3
 * PNG small icon (the vector icon is the crash suspect — see the
 * provider KDoc). The 0.7.3 test asserting the provider IS a
 * [DefaultMediaNotificationProvider] subclass is deleted here, not
 * skipped: it pinned the revert, and this round undoes the revert.
 * What lives here, testable without an Android runtime:
 *
 * - The ongoing rule, the play/pause + like icon choice, and the
 *   progress percentage (0.7.2 logic, unchanged).
 * - Structure: the provider really is a from-scratch
 *   [MediaNotification.Provider] (not the subclass), the small icon,
 *   both RemoteViews layouts and the channel id are present, and the
 *   like custom-action string is the pinned value the manager routes
 *   back to [AvaMediaNotificationProvider.handleCustomCommand].
 *
 * Building the RemoteViews, the artwork decode and the like toggle
 * itself need a device/emulator and are NOT covered here (see the
 * round report for what is still unverified — above all whether
 * playback still crashes on Harper's phone).
 */
class CustomNotificationLogicTest {

    @Test
    fun `ongoing while playing, dismissible while paused`() {
        assertTrue(notificationIsOngoing(true))
        assertFalse(notificationIsOngoing(false))
    }

    @Test
    fun `play-pause icon follows playback state`() {
        assertEquals(R.drawable.ic_notif_pause, notificationPlayPauseIconRes(true))
        assertEquals(R.drawable.ic_notif_play, notificationPlayPauseIconRes(false))
    }

    @Test
    fun `like icon is filled when liked, outline otherwise`() {
        assertEquals(R.drawable.ic_notif_heart_filled, notificationLikeIconRes(true))
        assertEquals(R.drawable.ic_notif_heart, notificationLikeIconRes(false))
    }

    @Test
    fun `progress percent is a clamped 0 to 100`() {
        assertEquals(0, notificationProgressPercent(0L, 0L))
        assertEquals(0, notificationProgressPercent(0L, 120_000L))
        assertEquals(25, notificationProgressPercent(30_000L, 120_000L))
        assertEquals(50, notificationProgressPercent(60_000L, 120_000L))
        assertEquals(100, notificationProgressPercent(120_000L, 120_000L))
        assertEquals(100, notificationProgressPercent(999_000L, 120_000L))
        assertEquals(0, notificationProgressPercent(-5_000L, 120_000L))
        assertEquals(0, notificationProgressPercent(30_000L, -1L))
    }

    @Test
    fun `like custom action string is the pinned value`() {
        assertEquals(
            "com.harper.ava.action.TOGGLE_FAVORITE",
            AvaMediaNotificationProvider.ACTION_TOGGLE_FAVORITE,
        )
    }

    @Test
    fun `provider is a from-scratch MediaNotification Provider, not the Media3 subclass`() {
        assertTrue(
            MediaNotification.Provider::class.java
                .isAssignableFrom(AvaMediaNotificationProvider::class.java),
        )
        assertNotEquals(
            DefaultMediaNotificationProvider::class.java,
            AvaMediaNotificationProvider::class.java.superclass,
        )
    }

    @Test
    fun `small icon and both custom layouts are present in resources`() {
        // The 0.7.4 small icon must exist (it is the 0.7.3 PNG in all
        // five densities); a missing icon is exactly the kind of
        // resource failure the system rejects at startForeground.
        assertNotEquals(0, R.drawable.ic_ava_notification)
        assertNotEquals(0, R.layout.notification_ava_compact)
        assertNotEquals(0, R.layout.notification_ava_expanded)
        // Every drawable the RemoteViews reference must resolve too.
        assertNotEquals(0, R.drawable.ic_notif_art_placeholder)
        assertNotEquals(0, R.drawable.ic_notif_heart)
        assertNotEquals(0, R.drawable.ic_notif_heart_filled)
        assertNotEquals(0, R.drawable.notif_circle_dark)
        assertNotEquals(0, R.drawable.notif_circle_white)
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
