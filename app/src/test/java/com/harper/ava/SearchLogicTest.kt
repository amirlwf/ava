package com.harper.ava

import com.harper.ava.core.util.escapeLikeQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchLogicTest {

    @Test
    fun `escapeLikeQuery leaves plain text unchanged`() {
        assertEquals("ava", escapeLikeQuery("ava"))
        assertEquals("", escapeLikeQuery(""))
    }

    @Test
    fun `escapeLikeQuery escapes percent so it matches literally`() {
        assertEquals("100\\%", escapeLikeQuery("100%"))
    }

    @Test
    fun `escapeLikeQuery escapes underscore so it matches one literal character`() {
        assertEquals("a\\_b", escapeLikeQuery("a_b"))
    }

    @Test
    fun `escapeLikeQuery escapes the backslash first`() {
        assertEquals("a\\\\b", escapeLikeQuery("a\\b"))
        // A typed "\%" is a literal backslash followed by a literal
        // percent, not an escaped percent: both characters get their
        // own backslash.
        assertEquals("\\\\\\%", escapeLikeQuery("\\%"))
    }

    @Test
    fun `escapeLikeQuery handles a mixed query`() {
        assertEquals("rock\\_\\%\\\\", escapeLikeQuery("rock_%\\"))
    }
}
