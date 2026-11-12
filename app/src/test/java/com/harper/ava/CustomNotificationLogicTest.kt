package com.harper.ava

import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import com.harper.ava.playback.AvaMediaNotificationProvider
import com.harper.ava.playback.avaNotificationIconRes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the 0.7.3 notification provider's pure logic.
 *
 * 0.7.3 reverted the 0.7.2 from-scratch custom provider to the 0.6.0
 * [DefaultMediaNotificationProvider] subclass (it crashed on play with
 * `Bad notification for startForeground`). The 0.7.2 helpers are gone
 * with it — no like button, no progress bar, no custom action — so
 * those tests are deleted here, not skipped. What remains, testable
 * without an Android runtime:
 *
 * - Play/pause icon: the subclass still swaps Media3's stock icons
 *   for Ava's own drawables (pause while playing, play while paused).
 * - The ongoing trade-off, pinned structurally: the provider IS a
 *   [DefaultMediaNotificationProvider] subclass, whose
 *   `createNotification()` is final and forces ongoing false — so
 *   `setOngoing(true)` while playing is NOT achievable on this path.
 *   Playback loss on dismiss is guarded by the round-8 fix (pause,
 *   never stop(), in the service's `onDestroy()` + queue rebuild),
 *   not by ongoing. If this test ever fails because the provider
 *   stopped being a subclass, the ongoing story must be re-checked
 *   from scratch.
 *
 * Building the notification itself needs a device/emulator and is NOT
 * covered here (see the round report for what is still unverified).
 */
class CustomNotificationLogicTest {

    @Test
    fun `play-pause icon follows playback state`() {
        // Media3 emits ICON_PAUSE while playing and ICON_PLAY while
        // paused; Ava's mapping swaps each for its own drawable.
        assertEquals(R.drawable.ic_notif_pause, avaNotificationIconRes(CommandButton.ICON_PAUSE))
        assertEquals(R.drawable.ic_notif_play, avaNotificationIconRes(CommandButton.ICON_PLAY))
    }

    @Test
    fun `provider is the Media3 subclass so ongoing while playing is unavailable`() {
        assertEquals(
            DefaultMediaNotificationProvider::class.java,
            AvaMediaNotificationProvider::class.java.superclass,
        )
    }
}
