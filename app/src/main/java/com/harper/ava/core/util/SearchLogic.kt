package com.harper.ava.core.util

/**
 * Pure, Android-free search-query logic. Everything here is unit-tested
 * with plain JUnit (no Robolectric, no device) — see SearchLogicTest.
 *
 * The library search is a SQLite LIKE (`SongDao.search`): `%` matches any
 * run of characters and `_` matches any single character, so a query typed
 * by the user must have those two — plus the escape character itself —
 * escaped before it reaches the query, or the search lies: "%" alone
 * would match the whole library, "_" would match almost every song, and
 * a song literally called "100%" could never be found precisely.
 * [escapeLikeQuery] makes the query a literal match; the DAO declares
 * `ESCAPE '\'` so SQLite reads the backslashes as escapes.
 */

/**
 * Escapes a user-typed search term for a SQLite LIKE pattern with
 * `ESCAPE '\'`: `\` first (so the escape character itself is literal),
 * then `%` and `_`. Plain text passes through unchanged.
 */
fun escapeLikeQuery(query: String): String {
    val out = StringBuilder(query.length)
    for (ch in query) {
        when (ch) {
            '\\', '%', '_' -> out.append('\\').append(ch)
            else -> out.append(ch)
        }
    }
    return out.toString()
}
