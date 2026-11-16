package com.harper.ava

import android.content.Context
import android.content.ContextWrapper
import androidx.room.InvalidationTracker
import com.harper.ava.data.local.db.AlbumSummary
import com.harper.ava.data.local.db.AppDatabase
import com.harper.ava.data.local.db.ArtistSummary
import com.harper.ava.data.local.db.FavoriteDao
import com.harper.ava.data.local.db.FavoriteEntity
import com.harper.ava.data.local.db.FolderSummary
import com.harper.ava.data.local.db.GenreSummary
import com.harper.ava.data.local.db.ListeningHistoryDao
import com.harper.ava.data.local.db.ListeningHistoryEntity
import com.harper.ava.data.local.db.PlaylistDao
import com.harper.ava.data.local.db.PlaylistEntity
import com.harper.ava.data.local.db.PlaylistSongCrossRef
import com.harper.ava.data.local.db.PlaylistWithSongs
import com.harper.ava.data.local.db.SongDao
import com.harper.ava.data.local.db.SongEntity
import com.harper.ava.data.media.MediaStoreScanner
import com.harper.ava.data.media.ScanResult
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlayerRepository
import com.harper.ava.ui.home.HomeViewModel
import com.harper.ava.ui.library.LibraryViewModel
import com.harper.ava.ui.scan.ScanUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import sun.misc.Unsafe

/**
 * 0.4.4 launch-crash regression (2026-11-16): HomeViewModel and
 * LibraryViewModel launched an init-block collector that wrote
 * `_scanState` — declared AFTER the init block. viewModelScope runs on
 * Dispatchers.Main.immediate and the scanner's scanState is a
 * StateFlow, so collect delivered its first value synchronously
 * during the constructor, while _scanState was still null:
 * NullPointerException at HomeViewModel.kt:37 on every launch.
 * LibraryViewModel had the identical trap (it only never fired
 * because Home, the start destination, crashed first).
 *
 * The fix moves _scanState above the init block in both ViewModels.
 * These tests construct both ViewModels against a scanner whose
 * scanState already holds a non-default value, with Main set to an
 * UnconfinedTestDispatcher — the same undispatched start
 * Main.immediate gives on the device. On the unfixed code they fail:
 * the collector dies with the NPE (visible on stderr) and the mirror
 * never happens, so the value stays Idle. On the fixed code they pass
 * and the mirrored value arrives.
 *
 * Test doubles: a real MediaStoreScanner (its constructor only stores
 * its arguments) with a stub Context, a real MusicRepository over
 * fake DAOs that return empty flows, and a PlayerRepository allocated
 * without its constructor — the real one builds an ExoPlayer and
 * cannot run on the JVM; the ViewModels under test only store the
 * reference during construction and never call it here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanMirrorInitOrderTest {

    // -- fakes ------------------------------------------------------------

    private class FakeSongDao : SongDao {
        override fun observeAllByTitle(): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeAllByArtist(): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeAllByDateAdded(): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeRecentlyAdded(limit: Int): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeAllByDuration(): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun search(query: String): Flow<List<SongEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): SongEntity? = null
        override suspend fun getByIds(ids: List<Long>): List<SongEntity> = emptyList()
        override fun observeCount(): Flow<Int> = flowOf(0)
        override suspend fun count(): Int = 0
        override suspend fun totalDurationMs(): Long = 0L
        override suspend fun allSongs(): List<SongEntity> = emptyList()
        override fun observeRecentlyPlayed(): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeFavorites(): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeRecentlyFavorited(limit: Int): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeArtists(): Flow<List<ArtistSummary>> = flowOf(emptyList())
        override fun observeAlbums(): Flow<List<AlbumSummary>> = flowOf(emptyList())
        override fun observeSongsByArtist(artist: String): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeSongsByAlbum(album: String): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeGenres(): Flow<List<GenreSummary>> = flowOf(emptyList())
        override fun observeSongsByGenre(genre: String): Flow<List<SongEntity>> = flowOf(emptyList())
        override fun observeFolders(): Flow<List<FolderSummary>> = flowOf(emptyList())
        override fun observeSongsByFolder(folderPath: String): Flow<List<SongEntity>> = flowOf(emptyList())
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun upsertAll(songs: List<SongEntity>) = Unit
        override suspend fun deleteByIds(ids: List<Long>) = Unit
        override suspend fun markPlayed(id: Long, atMs: Long) = Unit
        override suspend fun updateLastPlayed(id: Long, atMs: Long) = Unit
    }

    private class FakePlaylistDao : PlaylistDao {
        override fun observeAll(): Flow<List<PlaylistEntity>> = flowOf(emptyList())
        override suspend fun count(): Int = 0
        override fun observeById(id: Long): Flow<PlaylistEntity?> = flowOf(null)
        override fun observeWithSongs(id: Long): Flow<PlaylistWithSongs?> = flowOf(null)
        override fun observeSongCount(playlistId: Long): Flow<Int> = flowOf(0)
        override fun observeFirstArtUri(playlistId: Long): Flow<String?> = flowOf(null)
        override suspend fun insert(playlist: PlaylistEntity): Long = 0L
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun addSong(ref: PlaylistSongCrossRef) = Unit
        override suspend fun songIds(playlistId: Long): List<Long> = emptyList()
        override suspend fun removeSong(playlistId: Long, songId: Long) = Unit
        override suspend fun maxPosition(playlistId: Long): Int = 0
    }

    private class FakeFavoriteDao : FavoriteDao {
        override fun observeFavoriteIds(): Flow<List<Long>> = flowOf(emptyList())
        override fun observeIsFavorite(songId: Long): Flow<Boolean> = flowOf(false)
        override suspend fun isFavorite(songId: Long): Boolean = false
        override suspend fun count(): Int = 0
        override suspend fun add(favorite: FavoriteEntity) = Unit
        override suspend fun remove(songId: Long) = Unit
        override suspend fun removeEntity(favorite: FavoriteEntity) = Unit
    }

    private class FakeListeningHistoryDao : ListeningHistoryDao {
        override suspend fun insert(event: ListeningHistoryEntity): Long = 0L
        override suspend fun setListened(id: Long, listenedMs: Long) = Unit
        override fun observeAll(): Flow<List<ListeningHistoryEntity>> = flowOf(emptyList())
        override suspend fun count(): Int = 0
    }

    private class FakeDb : AppDatabase() {
        override fun songDao(): SongDao = FakeSongDao()
        override fun playlistDao(): PlaylistDao = FakePlaylistDao()
        override fun favoriteDao(): FavoriteDao = FakeFavoriteDao()
        override fun listeningHistoryDao(): ListeningHistoryDao = FakeListeningHistoryDao()
        override fun createInvalidationTracker(): InvalidationTracker =
            InvalidationTracker(this, emptyMap(), emptyMap())

        override fun clearAllTables() {
            throw UnsupportedOperationException("not used by this test")
        }
    }

    /**
     * A Context without a constructor call: the platform android.jar
     * ships no android.test stubs (no MockContext), and every concrete
     * Context constructor is a "Stub!" body on the JVM. The scanner's
     * constructor only stores the Context and the ViewModels under
     * test never call a method on it, so an uninitialised
     * ContextWrapper is enough for this test.
     */
    private fun dummyContext(): Context {
        val unsafe = theUnsafe()
        @Suppress("UNCHECKED_CAST")
        return unsafe.allocateInstance(ContextWrapper::class.java) as Context
    }

    private fun theUnsafe(): Unsafe {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return field.get(null) as Unsafe
    }

    // -- setup ------------------------------------------------------------

    @Before
    fun setUp() {
        // Unconfined: a launch on Main starts undispatched, exactly
        // like Main.immediate on the device — the StateFlow's first
        // emission lands synchronously inside the constructor.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A PlayerRepository without its constructor: the real one builds an ExoPlayer. */
    private fun dummyPlayerRepository(): PlayerRepository {
        @Suppress("UNCHECKED_CAST")
        return theUnsafe().allocateInstance(PlayerRepository::class.java) as PlayerRepository
    }

    /** A real scanner whose shared state already holds [state] — the only way to preset it without a real scan. */
    private fun scannerWithState(music: MusicRepository, state: ScanUiState): MediaStoreScanner {
        val scanner = MediaStoreScanner(dummyContext(), music)
        setScannerState(scanner, state)
        return scanner
    }

    private fun setScannerState(scanner: MediaStoreScanner, state: ScanUiState) {
        val field = MediaStoreScanner::class.java.getDeclaredField("_scanState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(scanner) as MutableStateFlow<ScanUiState>).value = state
    }

    // -- tests ------------------------------------------------------------

    @Test
    fun `HomeViewModel construction mirrors the scanner state`() {
        val music = MusicRepository(FakeDb())
        val scanner = scannerWithState(music, ScanUiState.Scanning)
        val vm = HomeViewModel(music, dummyPlayerRepository(), scanner)
        assertEquals(ScanUiState.Scanning, vm.scanState.value)
    }

    @Test
    fun `LibraryViewModel construction mirrors the scanner state`() {
        val music = MusicRepository(FakeDb())
        val scanner = scannerWithState(music, ScanUiState.Scanning)
        val vm = LibraryViewModel(music, dummyPlayerRepository(), scanner)
        assertEquals(ScanUiState.Scanning, vm.scanState.value)
    }

    @Test
    fun `scanner state changes after construction are mirrored`() {
        val music = MusicRepository(FakeDb())
        val scanner = scannerWithState(music, ScanUiState.Scanning)
        val vm = HomeViewModel(music, dummyPlayerRepository(), scanner)
        assertEquals(ScanUiState.Scanning, vm.scanState.value)
        setScannerState(scanner, ScanUiState.Done(found = 3, removedStale = 1))
        assertEquals(ScanUiState.Done(found = 3, removedStale = 1), vm.scanState.value)
    }

    @Test
    fun `manual scan callbacks still drive the state`() {
        val music = MusicRepository(FakeDb())
        val scanner = scannerWithState(music, ScanUiState.Idle)
        val vm = HomeViewModel(music, dummyPlayerRepository(), scanner)
        assertEquals(ScanUiState.Idle, vm.scanState.value)
        vm.onScanStarted()
        assertEquals(ScanUiState.Scanning, vm.scanState.value)
        vm.onScanResult(ScanResult(found = 2, stored = 2, removedStale = 0, skipped = 0))
        assertEquals(ScanUiState.Done(found = 2, removedStale = 0), vm.scanState.value)
    }
}
