package com.harper.ava

import androidx.room.InvalidationTracker
import com.harper.ava.data.local.db.AppDatabase
import com.harper.ava.data.local.db.FavoriteDao
import com.harper.ava.data.local.db.ListeningHistoryDao
import com.harper.ava.data.local.db.PlaylistDao
import com.harper.ava.data.local.db.PlaylistEntity
import com.harper.ava.data.local.db.PlaylistSongCrossRef
import com.harper.ava.data.local.db.PlaylistWithSongs
import com.harper.ava.data.local.db.SongDao
import com.harper.ava.data.local.db.SongEntity
import com.harper.ava.data.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.7.2 regression tests for "can't add a song to a playlist".
 *
 * First-hand diagnosis: the insert path itself
 * ([MusicRepository.addSongToPlaylist] -> [PlaylistDao.addSong]) was
 * sound, but the song menu was a dead end — with no playlists yet it
 * showed only a disabled "No playlists yet" item and offered no way to
 * create one, and rows inside a playlist had no add action at all. The
 * fix adds a create-from-menu path ([MusicRepository.createPlaylist] +
 * add) and wires it everywhere. These tests drive that exact flow with
 * a fake DAO: add -> re-read the playlist and see the song immediately,
 * re-add -> no duplicate row, create-then-add -> the song lands in the
 * brand-new playlist.
 *
 * The fake [PlaylistDao.addSong] mirrors Room's REPLACE conflict
 * strategy: a second insert of the same (playlistId, songId) replaces
 * the row instead of adding another one.
 */
class AddToPlaylistTest {

    private class FakePlaylistDao : PlaylistDao {
        private var nextId = 1L
        val playlists = linkedMapOf<Long, PlaylistEntity>()
        val songs = linkedMapOf<Long, SongEntity>()
        /** (playlistId, songId) -> position; REPLACE semantics. */
        val refs = linkedMapOf<Pair<Long, Long>, Int>()

        fun seedSong(entity: SongEntity) {
            songs[entity.mediaStoreId] = entity
        }

        override fun observeAll(): Flow<List<PlaylistEntity>> = flowOf(playlists.values.toList())

        override suspend fun count(): Int = playlists.size

        override fun observeById(id: Long): Flow<PlaylistEntity?> = flowOf(playlists[id])

        override fun observeWithSongs(id: Long): Flow<PlaylistWithSongs?> {
            val playlist = playlists[id] ?: return flowOf(null)
            val ordered = refs.entries
                .filter { it.key.first == id }
                .sortedBy { it.value }
                .mapNotNull { songs[it.key.second] }
            return flowOf(PlaylistWithSongs(playlist, ordered))
        }

        override fun observeSongCount(playlistId: Long): Flow<Int> =
            flowOf(refs.keys.count { it.first == playlistId })

        override fun observeFirstArtUri(playlistId: Long): Flow<String?> = flowOf(null)

        override suspend fun insert(playlist: PlaylistEntity): Long {
            val id = nextId++
            playlists[id] = playlist.copy(id = id)
            return id
        }

        override suspend fun deleteById(id: Long) {
            playlists.remove(id)
            refs.keys.removeIf { it.first == id }
        }

        override suspend fun addSong(ref: PlaylistSongCrossRef) {
            refs[ref.playlistId to ref.songId] = ref.position
        }

        override suspend fun removeSong(playlistId: Long, songId: Long) {
            refs.remove(playlistId to songId)
        }

        override suspend fun maxPosition(playlistId: Long): Int =
            refs.entries
                .filter { it.key.first == playlistId }
                .maxOfOrNull { it.value } ?: -1
    }

    private class FakeFavoriteDao : FavoriteDao {
        override fun observeFavoriteIds(): Flow<List<Long>> = flowOf(emptyList())
        override fun observeIsFavorite(songId: Long): Flow<Boolean> = flowOf(false)
        override suspend fun isFavorite(songId: Long): Boolean = false
        override suspend fun count(): Int = 0
        override suspend fun add(favorite: com.harper.ava.data.local.db.FavoriteEntity) = Unit
        override suspend fun remove(songId: Long) = Unit
        override suspend fun removeEntity(favorite: com.harper.ava.data.local.db.FavoriteEntity) = Unit
    }

    private class FakeDb(
        private val playlistDao: PlaylistDao,
        private val favoriteDao: FavoriteDao,
    ) : AppDatabase() {
        override fun songDao(): SongDao =
            throw UnsupportedOperationException("not used by addSongToPlaylist")
        override fun playlistDao(): PlaylistDao = playlistDao
        override fun favoriteDao(): FavoriteDao = favoriteDao
        override fun listeningHistoryDao(): ListeningHistoryDao =
            throw UnsupportedOperationException("not used by addSongToPlaylist")
        override fun createInvalidationTracker(): InvalidationTracker =
            InvalidationTracker(this, emptyMap(), emptyMap())
        override fun clearAllTables() {
            throw UnsupportedOperationException("not used by addSongToPlaylist")
        }
    }

    private fun songEntity(id: Long, title: String = "Song $id") = SongEntity(
        mediaStoreId = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        uri = "content://media/$id",
    )

    private fun repoWithSongs(vararg ids: Long): Pair<MusicRepository, FakePlaylistDao> {
        val dao = FakePlaylistDao()
        ids.forEach { dao.seedSong(songEntity(it)) }
        return MusicRepository(FakeDb(dao, FakeFavoriteDao())) to dao
    }

    @Test
    fun `addSongToPlaylist then re-reading the playlist shows the song`() = runBlocking {
        val (repo, _) = repoWithSongs(1001L)
        val playlistId = repo.createPlaylist("Road")
        repo.addSongToPlaylist(playlistId, 1001L)
        val songs = repo.observePlaylistSongs(playlistId).first()
        assertEquals(listOf(1001L), songs.map { it.id })
    }

    @Test
    fun `adding the same song twice does not duplicate it`() = runBlocking {
        val (repo, _) = repoWithSongs(1001L)
        val playlistId = repo.createPlaylist("Road")
        repo.addSongToPlaylist(playlistId, 1001L)
        repo.addSongToPlaylist(playlistId, 1001L)
        val songs = repo.observePlaylistSongs(playlistId).first()
        assertEquals(1, songs.size)
        assertEquals(1001L, songs.single().id)
    }

    @Test
    fun `songs keep insertion order inside the playlist`() = runBlocking {
        val (repo, _) = repoWithSongs(1001L, 1002L)
        val playlistId = repo.createPlaylist("Road")
        repo.addSongToPlaylist(playlistId, 1001L)
        repo.addSongToPlaylist(playlistId, 1002L)
        val songs = repo.observePlaylistSongs(playlistId).first()
        assertEquals(listOf(1001L, 1002L), songs.map { it.id })
    }

    @Test
    fun `create-then-add lands the song in the brand-new playlist`() = runBlocking {
        val (repo, _) = repoWithSongs(1001L)
        // The exact menu flow: no playlist existed, the user creates one
        // from the song menu and the song must be in it right away.
        val playlistId = repo.createPlaylist("New from menu")
        repo.addSongToPlaylist(playlistId, 1001L)
        val songs = repo.observePlaylistSongs(playlistId).first()
        assertEquals(1, songs.size)
        assertEquals("Song 1001", songs.single().title)
    }

    @Test
    fun `adding to one playlist does not leak into another`() = runBlocking {
        val (repo, _) = repoWithSongs(1001L)
        val first = repo.createPlaylist("First")
        val second = repo.createPlaylist("Second")
        repo.addSongToPlaylist(first, 1001L)
        assertTrue(repo.observePlaylistSongs(second).first().isEmpty())
        assertEquals(1, repo.observePlaylistSongs(first).first().size)
    }
}
