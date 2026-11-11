package com.harper.ava

import com.harper.ava.core.util.needsPlayerRebuild
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-8 regression tests for the "play does nothing after the
 * notification is dismissed" bug (0.5.7).
 *
 * Dismissing the media notification destroys the playback service,
 * whose onDestroy used to call player.stop() — that resets the player
 * to IDLE with no current media item, while the repository still knew
 * the queue. play() on such a player is a silent no-op until a new
 * song is picked. The fix rebuilds the player from the saved queue
 * whenever it has nothing to play; this tests the rebuild decision.
 */
class PlaybackRecoveryTest {

    @Test
    fun `empty player with a saved queue must rebuild`() {
        assertTrue(needsPlayerRebuild(mediaItemCount = 0, isIdle = true, queueSize = 5))
    }

    @Test
    fun `empty player must rebuild even when not idle`() {
        assertTrue(needsPlayerRebuild(mediaItemCount = 0, isIdle = false, queueSize = 5))
    }

    @Test
    fun `idle player with items must rebuild`() {
        assertTrue(needsPlayerRebuild(mediaItemCount = 3, isIdle = true, queueSize = 3))
    }

    @Test
    fun `healthy player must not rebuild`() {
        assertFalse(needsPlayerRebuild(mediaItemCount = 3, isIdle = false, queueSize = 3))
    }

    @Test
    fun `empty queue never rebuilds`() {
        assertFalse(needsPlayerRebuild(mediaItemCount = 0, isIdle = true, queueSize = 0))
        assertFalse(needsPlayerRebuild(mediaItemCount = 0, isIdle = false, queueSize = 0))
        assertFalse(needsPlayerRebuild(mediaItemCount = 3, isIdle = true, queueSize = 0))
    }
}
