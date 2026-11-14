package com.harper.ava

import com.harper.ava.core.util.needsPlayerRebuild
import com.harper.ava.core.util.queueIndexAfterMove
import com.harper.ava.core.util.queueIndexAfterRemoval
import org.junit.Assert.assertEquals
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

    // Improve loop, run 15: the queue may hold the same song twice, and
    // Song is a data class — so queue.indexOf(currentSong) always
    // returns the FIRST copy even when the second copy is the one
    // playing. The playing index is tracked by position arithmetic now.

    @Test
    fun `queueIndexAfterRemoval shifts only when the removed item was before the playing one`() {
        // Removing the playing song itself: the transition re-points (-1).
        assertEquals(-1, queueIndexAfterRemoval(2, 2))
        // Removing an item before it shifts it down by one.
        assertEquals(1, queueIndexAfterRemoval(2, 0))
        assertEquals(1, queueIndexAfterRemoval(2, 1))
        // Removing an item after it leaves it alone.
        assertEquals(2, queueIndexAfterRemoval(2, 3))
        // Nothing playing stays nothing playing.
        assertEquals(-1, queueIndexAfterRemoval(-1, 0))
    }

    @Test
    fun `queueIndexAfterRemoval stays exact when the playing song is the second copy`() {
        // Queue [A, B, A], the second A playing at 2; removing B at 1
        // must land on 1 — the old newQueue.indexOf(currentSong)
        // returned 0 (the first copy).
        assertEquals(1, queueIndexAfterRemoval(2, 1))
    }

    @Test
    fun `queueIndexAfterMove carries the playing song with it`() {
        // Moving the playing song itself moves the index along.
        assertEquals(0, queueIndexAfterMove(2, 2, 0))
        assertEquals(3, queueIndexAfterMove(1, 1, 3))
        // Moving an item from before the playing song to at/after it shifts down.
        assertEquals(1, queueIndexAfterMove(2, 0, 3))
        assertEquals(1, queueIndexAfterMove(2, 0, 2))
        // Moving an item from after the playing song to at/before it shifts up.
        assertEquals(3, queueIndexAfterMove(2, 3, 0))
        assertEquals(3, queueIndexAfterMove(2, 3, 2))
        // A move that does not cross the playing song leaves it alone.
        assertEquals(2, queueIndexAfterMove(2, 0, 1))
        assertEquals(2, queueIndexAfterMove(2, 3, 3))
        // Nothing playing stays nothing playing.
        assertEquals(-1, queueIndexAfterMove(-1, 0, 2))
    }

    @Test
    fun `queueIndexAfterMove stays exact when the playing song is the second copy`() {
        // Queue [A, B, A], the second A playing at 2; dragging B from 1
        // to 0 must keep the index at 2 — the old
        // newQueue.indexOf(currentSong) returned 1 (the first copy).
        assertEquals(2, queueIndexAfterMove(2, 1, 0))
    }
}
