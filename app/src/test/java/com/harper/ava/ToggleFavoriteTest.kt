package com.harper.ava

import androidx.room.InvalidationTracker
import com.harper.ava.data.local.db.AppDatabase
import com.harper.ava.data.local.db.FavoriteDao
import com.harper.ava.data.local.db.FavoriteEntity
import com.harper.ava.data.local.db.PlaylistDao
import com.harper.ava.data.local.db.SongDao
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-8 regression tests for the "like never unlikes" bug on the
 * Now Playing page (0.5.7).
 *
 * The toggle used to decide from `Song.isFavorite` — a snapshot. In
 * Now Playing the song comes from the player's queue, which is built
 * at play time and never refreshed with favorite state, so the flag
 * was always false there: the toggle always inserted (IGNORE on
 * conflict) and a like could never be removed. The fix reads the
 * database instead, so these tests drive the toggle with a fake DAO
 * and pass Songs whose flag deliberately disagrees with the DB.
 */
class ToggleFavoriteTest {

    private class FakeFavoriteDao : FavoriteDao {
        val ids = mutableSetOf<Long>()

        override fun observeFavoriteIds(): Flow<List<Long>> = flowOf(ids.toList())
        override fun observeIsFavorite(songId: Long): Flow<Boolean> = flowOf(songId in ids)
        override suspend fun isFavorite(songId: Long): Boolean = songId in ids
        override suspend fun count(): Int = ids.size
        override suspend fun add(favorite: FavoriteEntity) {
            ids.add(favorite.songId)
        }

        override suspend fun remove(songId: Long) {
            ids.remove(songId)
        }

        override suspend fun removeEntity(favorite: FavoriteEntity) {
            ids.remove(favorite.songId)
        }
    }

    private class FakeDb(private val fav: FavoriteDao) : AppDatabase() {
        override fun songDao(): SongDao =
            throw UnsupportedOperationException("not used by toggleFavorite")

        override fun playlistDao(): PlaylistDao =
            throw UnsupportedOperationException("not used by toggleFavorite")

        override fun favoriteDao(): FavoriteDao = fav

        // RoomDatabase abstract members the toggle never touches.
        // The 4-arg InvalidationTracker constructor is the non-deprecated
        // one (the vararg form is deprecated in Room 2.8.5).
        override fun createInvalidationTracker(): InvalidationTracker =
            InvalidationTracker(this, emptyMap(), emptyMap())

        override fun clearAllTables() {
            throw UnsupportedOperationException("not used by toggleFavorite")
        }
    }

    private fun song(id: Long, isFavorite: Boolean) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        uri = "content://media/$id",
        isFavorite = isFavorite,
    )

    @Test
    fun `toggle adds a song that is not a favorite`() = runBlocking {
        val dao = FakeFavoriteDao()
        val repo = MusicRepository(FakeDb(dao))
        repo.toggleFavorite(song(1000201063L, isFavorite = false))
        assertTrue(dao.isFavorite(1000201063L))
    }

    @Test
    fun `toggle removes a favorite even when the passed Song says it is not`() = runBlocking {
        // The exact 0.5.7 Now Playing scenario: the DB has the like,
        // but the queue snapshot still says isFavorite=false.
        val dao = FakeFavoriteDao()
        dao.add(FavoriteEntity(songId = 1000201063L, addedAtMs = 0L))
        val repo = MusicRepository(FakeDb(dao))
        repo.toggleFavorite(song(1000201063L, isFavorite = false))
        assertFalse(dao.isFavorite(1000201063L))
    }

    @Test
    fun `toggle adds when the passed Song says favorite but the database disagrees`() = runBlocking {
        val dao = FakeFavoriteDao()
        val repo = MusicRepository(FakeDb(dao))
        repo.toggleFavorite(song(1000201063L, isFavorite = true))
        assertTrue(dao.isFavorite(1000201063L))
    }

    @Test
    fun `toggle twice returns to the original state`() = runBlocking {
        val dao = FakeFavoriteDao()
        val repo = MusicRepository(FakeDb(dao))
        val s = song(1000201058L, isFavorite = false)
        repo.toggleFavorite(s)
        assertTrue(dao.isFavorite(1000201058L))
        repo.toggleFavorite(s)
        assertFalse(dao.isFavorite(1000201058L))
    }
}
