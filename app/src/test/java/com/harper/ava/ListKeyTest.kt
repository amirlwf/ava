package com.harper.ava

import com.harper.ava.core.util.homeSectionSongKey
import com.harper.ava.core.util.queueRowKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.5.6 regression tests for the duplicate LazyColumn key crash.
 * On the real device, 0.5.5 died with
 * `IllegalArgumentException: Key "1000201063" was already used` as soon
 * as a song was both recently played and recently added: Home shows
 * three song sections inside ONE LazyColumn, and a bare song id is the
 * same key in every section. The same class of bug existed in the queue
 * (the same song may legitimately appear twice there).
 */
class ListKeyTest {

    @Test
    fun `same song in two home sections gets different keys`() {
        // The exact reported id, in the two sections that overlapped.
        val id = 1000201063L
        val keys = listOf(
            homeSectionSongKey("played", id),
            homeSectionSongKey("added", id),
        )
        assertEquals(2, keys.toSet().size)
    }

    @Test
    fun `overlapping home sections produce all-unique keys`() {
        // The real 0.5.5 scenario: songs 1000201063 and 1000201058 were
        // in both recentlyPlayed and recentlyAdded (and one in favorites).
        val played = listOf(1000201063L, 1000201058L)
        val added = listOf(1000201063L, 1000201058L, 1000201077L)
        val favs = listOf(1000201063L)
        val playlists = listOf(1L, 2L)
        val keys = played.map { homeSectionSongKey("played", it) } +
            added.map { homeSectionSongKey("added", it) } +
            favs.map { homeSectionSongKey("fav", it) } +
            playlists.map { "playlist:$it" }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `same song twice in a queue gets different keys`() {
        val id = 1000201063L
        val keys = listOf(queueRowKey(0, id), queueRowKey(1, id))
        assertEquals(2, keys.toSet().size)
    }

    @Test
    fun `queue keys stay unique with duplicates at several positions`() {
        val ids = listOf(1000201063L, 1000201058L, 1000201063L, 1000201058L)
        val keys = ids.mapIndexed { pos, id -> queueRowKey(pos, id) }
        assertEquals(ids.size, keys.toSet().size)
    }
}
