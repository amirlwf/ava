package com.harper.ava

import com.harper.ava.core.util.RINGTONE_DEFAULT_MS
import com.harper.ava.core.util.RINGTONE_MAX_MS
import com.harper.ava.core.util.RINGTONE_MIN_MS
import com.harper.ava.core.util.TrimRange
import com.harper.ava.core.util.decodeRingtoneUris
import com.harper.ava.core.util.defaultTrimRange
import com.harper.ava.core.util.encodeRingtoneUris
import com.harper.ava.core.util.putRingtoneUri
import com.harper.ava.core.util.ringtoneFileName
import com.harper.ava.core.util.sanitizeTrimRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.2 (Track G) — ringtone trim-range decisions.
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

    // Improve loop, run 25 — per-song ringtone-row Uri storage.

    @Test
    fun `ringtone uris round-trip through encode and decode`() {
        val map = mapOf(
            42L to "content://media/external/audio/media/7",
            1000201063L to "content://media/external/audio/media/9",
        )
        assertEquals(map, decodeRingtoneUris(encodeRingtoneUris(map)))
    }

    @Test
    fun `ringtone uris decode is empty for null blank or garbage`() {
        assertEquals(emptyMap<Long, String>(), decodeRingtoneUris(null))
        assertEquals(emptyMap<Long, String>(), decodeRingtoneUris(""))
        assertEquals(emptyMap<Long, String>(), decodeRingtoneUris("   \n  "))
        assertEquals(emptyMap<Long, String>(), decodeRingtoneUris("no-equals-here"))
        assertEquals(emptyMap<Long, String>(), decodeRingtoneUris("abc=content://x"))
        assertEquals(emptyMap<Long, String>(), decodeRingtoneUris("42="))
    }

    @Test
    fun `ringtone uris decode skips bad lines and keeps the good ones`() {
        val raw = "bad-line\n42=content://media/external/audio/media/7\n" +
            "xyz=content://media/external/audio/media/8\n" +
            "1000201063=content://media/external/audio/media/9\n"
        assertEquals(
            mapOf(
                42L to "content://media/external/audio/media/7",
                1000201063L to "content://media/external/audio/media/9",
            ),
            decodeRingtoneUris(raw),
        )
    }

    @Test
    fun `ringtone uris decode lets a later line win on a duplicate id`() {
        val raw = "42=content://media/external/audio/media/7\n" +
            "42=content://media/external/audio/media/8"
        assertEquals(
            mapOf(42L to "content://media/external/audio/media/8"),
            decodeRingtoneUris(raw),
        )
    }

    @Test
    fun `put ringtone uri inserts and replaces`() {
        val empty = emptyMap<Long, String>()
        val one = putRingtoneUri(empty, 42L, "content://media/external/audio/media/7")
        assertEquals(mapOf(42L to "content://media/external/audio/media/7"), one)
        val replaced = putRingtoneUri(one, 42L, "content://media/external/audio/media/8")
        assertEquals(mapOf(42L to "content://media/external/audio/media/8"), replaced)
        val two = putRingtoneUri(replaced, 7L, "content://media/external/audio/media/9")
        assertEquals(2, two.size)
    }

    @Test
    fun `ringtone uris encode is sorted by song id`() {
        val map = mapOf(
            1000201063L to "content://media/external/audio/media/9",
            42L to "content://media/external/audio/media/7",
        )
        assertEquals(
            "42=content://media/external/audio/media/7\n" +
                "1000201063=content://media/external/audio/media/9",
            encodeRingtoneUris(map),
        )
    }
}
