package com.harper.ava

import com.harper.ava.core.util.MAX_RECENT_SEARCHES
import com.harper.ava.core.util.decodeRecentSearches
import com.harper.ava.core.util.encodeRecentSearches
import com.harper.ava.core.util.recordSearch
import com.harper.ava.core.util.removeSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryLogicTest {

    @Test
    fun `recordSearch puts the new query first`() {
        assertEquals(
            listOf("ava", "rock"),
            recordSearch(listOf("rock"), "ava"),
        )
    }

    @Test
    fun `recordSearch moves a duplicate to the front instead of duplicating`() {
        // The newest spelling wins ("ROCK" replaces the older "rock").
        assertEquals(
            listOf("ROCK", "ava"),
            recordSearch(listOf("ava", "rock"), "ROCK"),
        )
    }

    @Test
    fun `recordSearch ignores blank queries and caps at eight`() {
        val history = (1..MAX_RECENT_SEARCHES).map { "q$it" }
        assertEquals(history, recordSearch(history, "   "))
        val capped = recordSearch(history, "new")
        assertEquals(MAX_RECENT_SEARCHES, capped.size)
        assertEquals("new", capped.first())
        assertTrue("q8" !in capped) // oldest drops off; q1 is still there
    }

    @Test
    fun `removeSearch drops the matching entry case-insensitively`() {
        assertEquals(
            listOf("ava"),
            removeSearch(listOf("ava", "Rock"), "rock"),
        )
    }

    @Test
    fun `encode and decode round-trip and drop blank lines`() {
        val history = listOf("ava", "rock and roll")
        assertEquals(history, decodeRecentSearches(encodeRecentSearches(history)))
        assertEquals(listOf("ava"), decodeRecentSearches("ava\n\n  \n"))
        assertEquals(emptyList<String>(), decodeRecentSearches(null))
        assertEquals(emptyList<String>(), decodeRecentSearches("   "))
    }
}
