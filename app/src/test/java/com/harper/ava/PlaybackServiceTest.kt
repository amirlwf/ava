package com.harper.ava

import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import com.harper.ava.data.preferences.Accent
import com.harper.ava.playback.AvaPlaybackService
import com.harper.ava.playback.avaNotificationIconRes
import com.harper.ava.playback.notificationAccentArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the foreground-service notification contract (round 7).
 *
 * The placeholder the service posts in onStartCommand and the media
 * notification Media3 posts later must be the SAME notification, so
 * Media3 replaces it instead of stacking a second one. That is only
 * true when both use the same notification id — the service's id must
 * stay equal to Media3's default. If someone changes either side,
 * this test fails before the app ships with two playback
 * notifications (or a placeholder that never goes away).
 */
class PlaybackServiceTest {

    @Test
    fun `placeholder notification id matches Media3 default`() {
        assertEquals(
            DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
            AvaPlaybackService.NOTIFICATION_ID,
        )
    }

    @Test
    fun `notification channel id is set`() {
        assertTrue(AvaPlaybackService.CHANNEL_ID.isNotEmpty())
    }

    /**
     * 0.6.0: the notification accent follows the user's palette. The
     * values are pinned to the primaries of `accentPalette` in
     * ui/theme/Color.kt — if a palette primary changes there, this test
     * fails until the provider is updated with it.
     */
    @Test
    fun `notification accent follows each palette`() {
        assertEquals(0xFF7C5CFF.toInt(), notificationAccentArgb(Accent.VIOLET, null))
        assertEquals(0xFF2E7CF6.toInt(), notificationAccentArgb(Accent.ELECTRIC_BLUE, null))
        assertEquals(0xFF10B981.toInt(), notificationAccentArgb(Accent.EMERALD, null))
        assertEquals(0xFFE11D48.toInt(), notificationAccentArgb(Accent.CRIMSON, null))
        assertEquals(0xFFF59E0B.toInt(), notificationAccentArgb(Accent.AMBER, null))
        assertEquals(0xFFF43F5E.toInt(), notificationAccentArgb(Accent.ROSE, null))
        assertEquals(0xFFE4E4E7.toInt(), notificationAccentArgb(Accent.MONOCHROME, null))
    }

    @Test
    fun `custom accent colour wins over the palette`() {
        assertEquals(
            0xFFFF0000.toInt(),
            notificationAccentArgb(Accent.VIOLET, 0xFFFF0000.toInt()),
        )
    }

    /**
     * 0.6.0: the three stock media buttons get Ava drawables; anything
     * else (stop, skip, shuffle, ...) keeps Media3's stock icon.
     *
     * The exact drawable ids are NOT asserted here: `verify.sh` only
     * feeds kotlinc the generated R.java as source, so `R` is not a
     * class on the test classpath. The ids are pinned instead by the
     * main-module compile — `AvaMediaNotificationProvider` references
     * all four `R.drawable.ic_notif_*` ids and step 2 type-checks them
     * against the aapt2-generated R (a missing drawable fails the
     * build). This test pins the mapping behaviour: four distinct
     * non-zero drawables for the four button icons, null otherwise.
     */
    @Test
    fun `ava notification icons map the three media buttons`() {
        val prev = avaNotificationIconRes(CommandButton.ICON_PREVIOUS)
        val play = avaNotificationIconRes(CommandButton.ICON_PLAY)
        val pause = avaNotificationIconRes(CommandButton.ICON_PAUSE)
        val next = avaNotificationIconRes(CommandButton.ICON_NEXT)
        assertNotNull(prev)
        assertNotNull(play)
        assertNotNull(pause)
        assertNotNull(next)
        assertEquals(4, setOf(prev, play, pause, next).size)
        assertNull(avaNotificationIconRes(CommandButton.ICON_STOP))
        assertNull(avaNotificationIconRes(CommandButton.ICON_UNDEFINED))
    }
}
