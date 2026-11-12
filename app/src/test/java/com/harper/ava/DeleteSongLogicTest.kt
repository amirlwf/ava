package com.harper.ava

import com.harper.ava.core.util.planSongDelete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.7.2 song delete: the queue/checkpoint plan after one song is
 * deleted. The rule under test — deleting the playing song advances
 * to the next one, deleting the only song stops playback — is the
 * same rule PlayerRepository.removeSongById applies at runtime
 * (ExoPlayer transition / stop); this pins the decision down in
 * pure logic so a regression shows up without a device.
 */
class DeleteSongLogicTest {

    @Test
    fun `deleting the playing song advances to the next song`() {
        val plan = planSongDelete(listOf(1L, 2L, 3L), currentIndex = 1, deletedId = 2L)
        assertEquals(listOf(1L, 3L), plan.queueIdsAfter)
        assertTrue(plan.wasPlaying)
        assertFalse(plan.queueBecomesEmpty)
        assertEquals(1, plan.nextIndex) // song 3 slides into index 1
    }

    @Test
    fun `deleting the playing last song falls back to the previous one`() {
        val plan = planSongDelete(listOf(1L, 2L, 3L), currentIndex = 2, deletedId = 3L)
        assertEquals(listOf(1L, 2L), plan.queueIdsAfter)
        assertTrue(plan.wasPlaying)
        assertFalse(plan.queueBecomesEmpty)
        assertEquals(1, plan.nextIndex)
    }

    @Test
    fun `deleting the only song empties the queue and stops playback`() {
        val plan = planSongDelete(listOf(7L), currentIndex = 0, deletedId = 7L)
        assertEquals(emptyList<Long>(), plan.queueIdsAfter)
        assertTrue(plan.wasPlaying)
        assertTrue(plan.queueBecomesEmpty)
        assertEquals(-1, plan.nextIndex)
    }

    @Test
    fun `deleting a queued song before the playing one shifts the index down`() {
        val plan = planSongDelete(listOf(1L, 2L, 3L, 4L), currentIndex = 2, deletedId = 1L)
        assertEquals(listOf(2L, 3L, 4L), plan.queueIdsAfter)
        assertFalse(plan.wasPlaying)
        assertFalse(plan.queueBecomesEmpty)
        assertEquals(1, plan.nextIndex) // song 3 is still playing, now at 1
    }

    @Test
    fun `deleting a queued song after the playing one leaves the index alone`() {
        val plan = planSongDelete(listOf(1L, 2L, 3L), currentIndex = 0, deletedId = 3L)
        assertEquals(listOf(1L, 2L), plan.queueIdsAfter)
        assertFalse(plan.wasPlaying)
        assertEquals(0, plan.nextIndex)
    }

    @Test
    fun `deleting a song that is not queued changes nothing`() {
        val plan = planSongDelete(listOf(1L, 2L), currentIndex = 0, deletedId = 99L)
        assertEquals(listOf(1L, 2L), plan.queueIdsAfter)
        assertFalse(plan.wasPlaying)
        assertFalse(plan.queueBecomesEmpty)
        assertEquals(0, plan.nextIndex)
    }

    @Test
    fun `deleting from an empty queue is a no-op`() {
        val plan = planSongDelete(emptyList(), currentIndex = -1, deletedId = 5L)
        assertEquals(emptyList<Long>(), plan.queueIdsAfter)
        assertFalse(plan.wasPlaying)
        assertFalse(plan.queueBecomesEmpty)
        assertEquals(-1, plan.nextIndex)
    }
}
