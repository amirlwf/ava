package com.harper.ava

import com.harper.ava.core.util.lyricIndexAt
import com.harper.ava.core.util.parseLrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsLogicTest {

    @Test
    fun `parses basic timed lines`() {
        val parsed = parseLrc("[00:01.00]First line\n[00:05.50]Second line\n")
        assertTrue(parsed.hasTimestamps)
        assertEquals(2, parsed.lines.size)
        assertEquals(1_000L, parsed.lines[0].timeMs)
        assertEquals("First line", parsed.lines[0].text)
        assertEquals(5_500L, parsed.lines[1].timeMs)
    }

    @Test
    fun `millis with three digits and minutes over one`() {
        val parsed = parseLrc("[01:02.345]Late line\n")
        assertEquals(62_345L, parsed.lines[0].timeMs)
    }

    @Test
    fun `multiple time tags repeat the text`() {
        val parsed = parseLrc("[00:01.00][00:03.00]Chorus\n")
        assertEquals(2, parsed.lines.size)
        assertEquals(1_000L, parsed.lines[0].timeMs)
        assertEquals(3_000L, parsed.lines[1].timeMs)
        assertEquals("Chorus", parsed.lines[1].text)
    }

    @Test
    fun `metadata and offset are applied`() {
        val parsed = parseLrc("[ti:My Song]\n[ar:Singer]\n[offset:+500]\n[00:01.00]Line\n")
        assertEquals("My Song", parsed.title)
        assertEquals("Singer", parsed.artist)
        assertEquals(1_500L, parsed.lines[0].timeMs)
    }

    @Test
    fun `lines are sorted by time`() {
        val parsed = parseLrc("[00:10.00]Later\n[00:02.00]Earlier\n")
        assertEquals("Earlier", parsed.lines[0].text)
        assertEquals("Later", parsed.lines[1].text)
    }

    @Test
    fun `file without timestamps is plain text`() {
        val parsed = parseLrc("Just some lyrics\nNo timing here\n")
        assertFalse(parsed.hasTimestamps)
        assertEquals(2, parsed.lines.size)
        assertEquals(-1L, parsed.lines[0].timeMs)
    }

    @Test
    fun `garbage and blank lines are skipped`() {
        val parsed = parseLrc("\n[00:01.00]Ok\n[not-a-tag]\n[99:99.99]\n")
        assertEquals(1, parsed.lines.size)
        assertEquals("Ok", parsed.lines[0].text)
    }

    @Test
    fun `lyricIndexAt picks the last line at or before the position`() {
        val parsed = parseLrc("[00:01.00]A\n[00:05.00]B\n[00:09.00]C\n")
        assertEquals(-1, lyricIndexAt(parsed.lines, 999L))
        assertEquals(0, lyricIndexAt(parsed.lines, 1_000L))
        assertEquals(0, lyricIndexAt(parsed.lines, 4_999L))
        assertEquals(1, lyricIndexAt(parsed.lines, 5_000L))
        assertEquals(2, lyricIndexAt(parsed.lines, 60_000L))
    }
}
