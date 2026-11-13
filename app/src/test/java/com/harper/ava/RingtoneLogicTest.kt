package com.harper.ava

import com.harper.ava.core.util.RINGTONE_DEFAULT_MS
import com.harper.ava.core.util.RINGTONE_MAX_MS
import com.harper.ava.core.util.RINGTONE_MIN_MS
import com.harper.ava.core.util.TrimRange
import com.harper.ava.core.util.defaultTrimRange
import com.harper.ava.core.util.ringtoneFileName
import com.harper.ava.core.util.sanitizeTrimRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.1-fix1 (Track G) — ringtone trim-range decisions.
 */
class RingtoneLogicTest {

    @Test
    fun `default range is the first thirty seconds`() {
        assertEquals(TrimRange(0L, 30_000L), defaultTrimRange(214_000L))
        assertEquals(RINGTONE_DEFAULT_MS, defaultTrimRange(214_000L).lengthMs)
    }

    @Test
    fun `default range is the whole song when it is short`() {
        assertEquals(TrimRange(0L, 12_000L), defaultTrimRange(12_000L))
        assertEquals(TrimRange(0L, 0L), defaultTrimRange(0L))
    }

    @Test
    fun `sanitize keeps a valid range untouched`() {
        assertEquals(TrimRange(5_000L, 35_000L), sanitizeTrimRange(5_000L, 35_000L, 214_000L))
    }

    @Test
    fun `sanitize clamps start and end inside the song`() {
        assertEquals(TrimRange(0L, 60_000L), sanitizeTrimRange(-5_000L, 300_000L, 214_000L))
        assertEquals(TrimRange(213_000L, 214_000L), sanitizeTrimRange(300_000L, 400_000L, 214_000L))
    }

    @Test
    fun `sanitize enforces the minimum length`() {
        // End too close to start: pushed out to start + 1 s.
        assertEquals(TrimRange(10_000L, 11_000L), sanitizeTrimRange(10_000L, 10_500L, 214_000L))
        // Reversed range: end lands on start + 1 s.
        assertEquals(TrimRange(10_000L, 11_000L), sanitizeTrimRange(10_000L, 5_000L, 214_000L))
    }

    @Test
    fun `sanitize enforces the maximum length`() {
        assertEquals(TrimRange(0L, RINGTONE_MAX_MS), sanitizeTrimRange(0L, 200_000L, 214_000L))
        assertEquals(TrimRange(10_000L, 70_000L), sanitizeTrimRange(10_000L, 200_000L, 214_000L))
    }

    @Test
    fun `a song shorter than the minimum yields its whole length`() {
        assertEquals(TrimRange(0L, 500L), sanitizeTrimRange(0L, 500L, 500L))
        assertEquals(TrimRange(0L, 0L), sanitizeTrimRange(0L, 0L, 0L))
    }

    @Test
    fun `trim length is end minus start`() {
        assertEquals(25_000L, TrimRange(5_000L, 30_000L).lengthMs)
        assertTrue(RINGTONE_MIN_MS <= RINGTONE_DEFAULT_MS)
        assertTrue(RINGTONE_DEFAULT_MS <= RINGTONE_MAX_MS)
    }

    @Test
    fun `ringtone file name sanitizes illegal characters`() {
        assertEquals("my_song", ringtoneFileName("my/song"))
        assertEquals("a_b_c", ringtoneFileName("a:b*c"))
        assertEquals("song", ringtoneFileName("  song  "))
        assertEquals("song", ringtoneFileName("...song..."))
        assertEquals("ava-ringtone", ringtoneFileName(""))
        assertEquals("ava-ringtone", ringtoneFileName("   "))
        assertEquals("ava-ringtone", ringtoneFileName("///"))
    }

    @Test
    fun `ringtone file name is capped at eighty characters`() {
        val long = "a".repeat(200)
        assertEquals(80, ringtoneFileName(long).length)
    }
}
