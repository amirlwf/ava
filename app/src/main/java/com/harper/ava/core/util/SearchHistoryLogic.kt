package com.harper.ava.core.util

/**
 * Pure, Android-free search-history logic. Everything here is unit-tested
 * with plain JUnit (no Robolectric, no device) — see SearchHistoryLogicTest.
 *
 * A search is recorded when the user actually opens a result, not on every
 * keystroke: typing alone never pollutes the history. Matching is
 * case-insensitive, so "ava" and "Ava" are the same entry.
 */

const val MAX_RECENT_SEARCHES: Int = 8

/** Records [query] at the front; duplicates move to the front, oldest drop off. */
fun recordSearch(history: List<String>, query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return history
    return (listOf(trimmed) + history.filter { it.trim().lowercase() != trimmed.lowercase() })
        .take(MAX_RECENT_SEARCHES)
}

/** Removes one entry (case-insensitive match). */
fun removeSearch(history: List<String>, query: String): List<String> {
    val trimmed = query.trim().lowercase()
    return history.filter { it.trim().lowercase() != trimmed }
}

/** Encodes the history for DataStore (one query per line). */
fun encodeRecentSearches(history: List<String>): String =
    history.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

/** Parses [encodeRecentSearches] output; garbage lines are dropped, never crash. */
fun decodeRecentSearches(encoded: String?): List<String> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_RECENT_SEARCHES)
}
