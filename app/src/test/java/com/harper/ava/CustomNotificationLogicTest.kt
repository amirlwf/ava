package com.harper.ava

import com.harper.ava.playback.AvaMediaNotificationProvider
import com.harper.ava.playback.notificationIsOngoing
import com.harper.ava.playback.notificationLikeIconRes
import com.harper.ava.playback.notificationPlayPauseIconRes
import com.harper.ava.playback.notificationProgressPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the 0.7.2 custom notification provider's pure logic.
 *
 * Only what is testable without an Android runtime lives here: the
 * ongoing rule, the play/pause + like icon choice, and the progress
 * percentage. Building the RemoteViews, the artwork decode and the
 * like toggle itself need a device/emulator and are NOT covered here
 * (see the round report for what is still unverified).
 *
 * NOTE: this file is intentionally NOT registered in verify.sh — the
 * 0.7.2 integrator registers it. Run it manually with JUnitCore
 * (same classpath as verify.sh step 3).
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
    fun `like custom action string is set`() {
        assertTrue(AvaMediaNotificationProvider.ACTION_TOGGLE_FAVORITE.isNotEmpty())
    }
}
