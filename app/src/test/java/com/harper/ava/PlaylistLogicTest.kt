package com.harper.ava

import com.harper.ava.core.util.normalizePlaylistName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for playlist-name normalization — the guard that keeps
 * blank or whitespace-padded names out of the playlist table, no
 * matter which screen created the playlist.
 */
class PlaylistLogicTest {

    @Test
    fun `blank and whitespace-only names normalize to null`() {
        assertNull(normalizePlaylistName(""))
        assertNull(normalizePlaylistName("   "))
        assertNull(normalizePlaylistName("\t\n"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Road Trip", normalizePlaylistName("  Road Trip  "))
        assertEquals("Road Trip", normalizePlaylistName("\tRoad Trip\n"))
    }

    @Test
    fun `normal names pass through unchanged`() {
        assertEquals("Gym", normalizePlaylistName("Gym"))
        assertEquals("My Favourites 2026", normalizePlaylistName("My Favourites 2026"))
    }

    @Test
    fun `persian names are kept untouched`() {
        assertEquals("آهنگ‌های شب", normalizePlaylistName("  آهنگ‌های شب  "))
        assertEquals("صبح", normalizePlaylistName("صبح"))
    }
}
