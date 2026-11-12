package com.harper.ava

import com.harper.ava.core.util.RawScannedSong
import com.harper.ava.core.util.dedupeScannedSongs
import com.harper.ava.core.util.filenameFromUri
import com.harper.ava.core.util.normalizeScannedSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanNormalizeTest {

    private fun raw(
        id: Long = 1L,
        title: String? = "Song",
        artist: String? = "Artist",
        album: String? = "Album",
        durationMs: Long = 180_000L,
        uri: String = "content://media/external/audio/media/1",
        fileName: String? = null,
    ) = RawScannedSong(
        mediaStoreId = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = uri,
        albumArtUri = null,
        dateAddedMs = 1_700_000_000_000L,
        trackNumber = null,
        year = null,
        fileName = fileName,
    )

    @Test
    fun `valid row normalizes to an entity`() {
        val entity = normalizeScannedSong(raw())
        assertEquals(1L, entity?.mediaStoreId)
        assertEquals("Song", entity?.title)
        assertEquals("Artist", entity?.artist)
        assertEquals(180_000L, entity?.durationMs)
    }

    @Test
    fun `blank title falls back to the file name`() {
        val entity = normalizeScannedSong(
            raw(title = "  ", uri = "content://media/external/audio/media/7/my-song.mp3"),
        )
        assertEquals("my-song", entity?.title)
    }

    @Test
    fun `blank title prefers DISPLAY_NAME over the numeric uri segment`() {
        // 0.7.6: a real MediaStore audio URI carries no file name
        // ("content://media/external/audio/media/7" -> "7"), so an
        // untagged song used to be titled with its numeric id. The
        // stored DISPLAY_NAME wins over the URI segment.
        val entity = normalizeScannedSong(
            raw(
                title = null,
                uri = "content://media/external/audio/media/7",
                fileName = "my-song.mp3",
            ),
        )
        assertEquals("my-song", entity?.title)
    }

    @Test
    fun `blank title without a file name keeps the numeric uri segment`() {
        // Last resort before the synthetic "track-<id>": the URI's
        // last segment. Documents the pre-0.7.6 behaviour for rows
        // with no DISPLAY_NAME at all.
        val entity = normalizeScannedSong(
            raw(title = "", uri = "content://media/external/audio/media/7"),
        )
        assertEquals("7", entity?.title)
    }

    @Test
    fun `filenameFromUri strips directories and extension`() {
        assertEquals("my-song", filenameFromUri("content://media/external/audio/media/7/my-song.mp3"))
        assertEquals("track", filenameFromUri("file:///music/track"))
    }

    @Test
    fun `blank artist and album stay blank instead of crashing`() {
        val entity = normalizeScannedSong(raw(artist = null, album = ""))
        assertEquals("", entity?.artist)
        assertEquals("", entity?.album)
    }

    @Test
    fun `zero duration is skipped as corrupt`() {
        assertNull(normalizeScannedSong(raw(durationMs = 0L)))
        assertNull(normalizeScannedSong(raw(durationMs = -5L)))
    }

    @Test
    fun `invalid id or blank uri is skipped`() {
        assertNull(normalizeScannedSong(raw(id = 0L)))
        assertNull(normalizeScannedSong(raw(uri = "   ")))
    }

    @Test
    fun `dedupe keeps the first occurrence of each id`() {
        val first = raw(id = 1L, title = "First")
        val dup = raw(id = 1L, title = "Second")
        val other = raw(id = 2L, title = "Other")
        val deduped = dedupeScannedSongs(listOf(first, dup, other))
        assertEquals(listOf(first, other), deduped)
    }
}
