package com.harper.ava

import com.harper.ava.core.util.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `zero formats as 0 00`() {
        assertEquals("0:00", formatDuration(0L))
    }

    @Test
    fun `seconds only`() {
        assertEquals("0:07", formatDuration(7_000L))
    }

    @Test
    fun `minutes and seconds`() {
        assertEquals("3:07", formatDuration(187_000L))
    }

    @Test
    fun `hours use H MM SS`() {
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }

    @Test
    fun `negative clamps to zero`() {
        assertEquals("0:00", formatDuration(-5_000L))
    }
}
