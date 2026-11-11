package com.harper.ava

import androidx.media3.session.DefaultMediaNotificationProvider
import com.harper.ava.playback.AvaPlaybackService
import org.junit.Assert.assertEquals
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
}
