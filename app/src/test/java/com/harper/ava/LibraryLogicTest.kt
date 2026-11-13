package com.harper.ava

import com.harper.ava.core.util.RawScannedSong
import com.harper.ava.core.util.decodeQueueIds
import com.harper.ava.core.util.encodeQueueIds
import com.harper.ava.core.util.filenameFromUri
import com.harper.ava.core.util.folderDisplayName
import com.harper.ava.core.util.normalizeScannedSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [com.harper.ava.core.util.LibraryLogic] — the queue-id
 * codec, folder display names and the scan-normalize edges not covered
 * by ScanNormalizeTest.
 *
 * Improve loop, run 7 (2026-11-13): the queue-ids and folder tests
 * used to live in SleepTimerLogicTest, where nothing about the sleep
 * timer was tested by them — they moved here unchanged, next to the
 * code they actually pin.
 */
class LibraryLogicTest {

    private fun raw(
        id: Long = 1L,
        title: String? = "Song",
        uri: String = "content://media/external/audio/media/1",
        fileName: String? = null,
        genre: String? = null,
        folderPath: String? = null,
    ) = RawScannedSong(
        mediaStoreId = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        uri = uri,
        albumArtUri = null,
        dateAddedMs = 1_700_000_000_000L,
        trackNumber = null,
        year = null,
        genre = genre,
        folderPath = folderPath,
        fileName = fileName,
    )

    // -- queue-id codec (moved from SleepTimerLogicTest, unchanged) ----

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
    fun `encode of an empty queue is empty and decode tolerates whitespace`() {
        assertEquals("", encodeQueueIds(emptyList()))
        assertEquals(listOf(5L, 9L), decodeQueueIds(" 5 , 9 "))
    }

    // -- folder display names (moved from SleepTimerLogicTest, unchanged) --

    @Test
    fun `folder display name is the last path segment`() {
        assertEquals("Rock", folderDisplayName("Music/Rock/"))
        assertEquals("Rock", folderDisplayName("Music/Rock"))
        assertEquals("Music", folderDisplayName("Music/"))
        assertNull(folderDisplayName(null))
        assertNull(folderDisplayName(""))
        assertNull(folderDisplayName("   "))
    }

    @Test
    fun `folder display name takes the deepest segment`() {
        assertEquals("Classic", folderDisplayName("Music/Rock/Classic/"))
    }

    // -- filenameFromUri ------------------------------------------------

    @Test
    fun `filenameFromUri strips a query string and trims whitespace`() {
        assertEquals("my-song", filenameFromUri("content://x/my-song.mp3?token=abc"))
        assertEquals("my-song", filenameFromUri("  my-song.mp3  "))
    }

    // -- normalizeScannedSong edges -------------------------------------

    @Test
    fun `blank title with no file name and an empty uri segment falls back to track-id`() {
        // URI ends in "/" so its last segment is empty: the synthetic
        // "track-<id>" is the only title left.
        val entity = normalizeScannedSong(
            raw(id = 7L, title = null, uri = "content://media/external/audio/media/"),
        )
        assertEquals("track-7", entity?.title)
    }

    @Test
    fun `blank title with a whitespace-only file name falls through to the uri segment`() {
        // 0.7.6 follow-up (run 7): filenameFromUri trims, so a
        // whitespace DISPLAY_NAME no longer becomes a whitespace
        // title — the URI segment wins instead.
        val entity = normalizeScannedSong(
            raw(
                id = 7L,
                title = "  ",
                uri = "content://media/external/audio/media/7",
                fileName = "   ",
            ),
        )
        assertEquals("7", entity?.title)
    }

    @Test
    fun `blank genre folder and file name normalize to null, non-blank ones trim`() {
        val blank = normalizeScannedSong(raw(genre = "  ", folderPath = " ", fileName = "  "))
        assertNull(blank?.genre)
        assertNull(blank?.folderPath)
        assertNull(blank?.fileName)

        val kept = normalizeScannedSong(
            raw(genre = " Rock ", folderPath = " Music/Rock/ ", fileName = " song.mp3 "),
        )
        assertEquals("Rock", kept?.genre)
        assertEquals("Music/Rock/", kept?.folderPath)
        assertEquals("song.mp3", kept?.fileName)
    }
}
