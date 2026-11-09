package com.harper.ava

import com.harper.ava.core.util.SLEEP_TIMER_MAX_MINUTES
import com.harper.ava.core.util.SLEEP_TIMER_MIN_MINUTES
import com.harper.ava.core.util.SLEEP_TIMER_PRESETS_MINUTES
import com.harper.ava.core.util.clampCustomSleepMinutes
import com.harper.ava.core.util.decodeQueueIds
import com.harper.ava.core.util.encodeQueueIds
import com.harper.ava.core.util.folderDisplayName
import com.harper.ava.core.util.isSleepTimerExpired
import com.harper.ava.core.util.restoreSleepTimerMinutes
import com.harper.ava.core.util.sleepTimerEndsAtMs
import com.harper.ava.core.util.sleepTimerRemainingMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerLogicTest {

    @Test
    fun `presets are the documented ready-made durations`() {
        assertEquals(listOf(5, 10, 15, 30, 45, 60, 90), SLEEP_TIMER_PRESETS_MINUTES)
    }

    @Test
    fun `custom minutes clamp to 1 to 480`() {
        assertEquals(SLEEP_TIMER_MIN_MINUTES, clampCustomSleepMinutes(0))
        assertEquals(SLEEP_TIMER_MIN_MINUTES, clampCustomSleepMinutes(-7))
        assertEquals(45, clampCustomSleepMinutes(45))
        assertEquals(SLEEP_TIMER_MAX_MINUTES, clampCustomSleepMinutes(9999))
    }

    @Test
    fun `endsAt and remaining math`() {
        val now = 1_000_000L
        val endsAt = sleepTimerEndsAtMs(now, 15)
        assertEquals(now + 15 * 60_000L, endsAt)
        assertEquals(15 * 60_000L, sleepTimerRemainingMs(endsAt, now))
        assertEquals(0L, sleepTimerRemainingMs(endsAt, endsAt + 5_000L))
        assertFalse(isSleepTimerExpired(endsAt, endsAt - 1))
        assertTrue(isSleepTimerExpired(endsAt, endsAt))
    }

    @Test
    fun `queue ids round-trip through encode and decode`() {
        val ids = listOf(11L, 222L, 3333L)
        assertEquals(ids, decodeQueueIds(encodeQueueIds(ids)))
        assertEquals(emptyList<Long>(), decodeQueueIds(""))
        assertEquals(emptyList<Long>(), decodeQueueIds(null))
        // Garbage segments are dropped, valid ones survive.
        assertEquals(listOf(5L, 9L), decodeQueueIds("5,abc,,9,-3,0"))
    }

    @Test
    fun `restore recovers the set duration rounded up`() {
        val now = 1_000_000L
        // 30 minutes left exactly, and 29:59 left (one second into a 30-min timer).
        assertEquals(30, restoreSleepTimerMinutes(now + 30 * 60_000L, now))
        assertEquals(30, restoreSleepTimerMinutes(now + 29 * 60_000L + 59_000L, now))
        // A nearly-expired timer recovers to 1 minute, never 0.
        assertEquals(1, restoreSleepTimerMinutes(now + 1_000L, now))
        assertEquals(1, restoreSleepTimerMinutes(now, now))
    }

    @Test
    fun `folder display name is the last path segment`() {
        assertEquals("Rock", folderDisplayName("Music/Rock/"))
        assertEquals("Rock", folderDisplayName("Music/Rock"))
        assertEquals("Music", folderDisplayName("Music/"))
        assertNull(folderDisplayName(null))
        assertNull(folderDisplayName(""))
        assertNull(folderDisplayName("   "))
    }
}
