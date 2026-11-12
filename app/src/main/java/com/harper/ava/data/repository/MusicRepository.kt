package com.harper.ava.data.repository

import androidx.room.withTransaction
import com.harper.ava.data.local.db.AlbumSummary
import com.harper.ava.data.local.db.AppDatabase
import com.harper.ava.data.local.db.ArtistSummary
import com.harper.ava.data.local.db.FavoriteEntity
import com.harper.ava.data.local.db.PlaylistEntity
import com.harper.ava.data.local.db.PlaylistSongCrossRef
import com.harper.ava.data.local.db.SongEntity
import com.harper.ava.domain.model.Playlist
import com.harper.ava.domain.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Result of syncing one MediaStore scan into Room. */
data class SyncResult(val stored: Int, val removedStale: Int)

enum class SongSort { TITLE, ARTIST, DATE_ADDED, DURATION }

fun SongEntity.toModel(isFavorite: Boolean = false): Song = Song(
    id = mediaStoreId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    uri = uri,
    albumArtUri = albumArtUri,
    dateAddedMs = dateAddedMs,
    trackNumber = trackNumber,
    genre = genre,
    year = year,
    folderPath = folderPath,
    fileName = fileName,
    isFavorite = isFavorite,
)

/** Library data: Room-backed songs, playlists and favorites. */
class MusicRepository(private val db: AppDatabase) {

    fun observeSongs(sort: SongSort): Flow<List<Song>> {
        val songs = when (sort) {
            SongSort.TITLE -> db.songDao().observeAllByTitle()
            SongSort.ARTIST -> db.songDao().observeAllByArtist()
            SongSort.DATE_ADDED -> db.songDao().observeAllByDateAdded()
            SongSort.DURATION -> db.songDao().observeAllByDuration()
        }
        return combine(songs, db.favoriteDao().observeFavoriteIds()) { list, favIds ->
            val favSet = favIds.toSet()
            list.map { it.toModel(isFavorite = it.mediaStoreId in favSet) }
        }
    }

    fun observeSongCount(): Flow<Int> = db.songDao().observeCount()

    /** One song by id (used by resume-playback). Null when the file is gone. */
    suspend fun getSongById(id: Long): Song? {
        val entity = db.songDao().getById(id) ?: return null
        return entity.toModel(isFavorite = db.favoriteDao().isFavorite(id))
    }

    /**
     * Several songs by id, in the order of [ids]. Songs that no longer
     * exist are dropped (queue restore must never crash on a stale id).
     */
    suspend fun getSongsByIds(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val entities = db.songDao().getByIds(ids).associateBy { it.mediaStoreId }
        // Favorite state is not needed for playback; the restored songs
        // are marked not-favorite here and the live flows correct the UI
        // (favorite toggles) afterwards.
        return ids.mapNotNull { entities[it]?.toModel(isFavorite = false) }
    }

    /** Library totals for the Storage & library screen. No schema change. */
    data class LibraryStats(
        val songCount: Int = 0,
        val totalDurationMs: Long = 0L,
        val playlistCount: Int = 0,
        val favoriteCount: Int = 0,
    )

    suspend fun libraryStats(): LibraryStats = LibraryStats(
        songCount = db.songDao().count(),
        totalDurationMs = db.songDao().totalDurationMs(),
        playlistCount = db.playlistDao().count(),
        favoriteCount = db.favoriteDao().count(),
    )

    fun observeRecentlyPlayed(): Flow<List<Song>> =
        combine(
            db.songDao().observeRecentlyPlayed(),
            db.favoriteDao().observeFavoriteIds(),
        ) { list, favIds ->
            val favSet = favIds.toSet()
            list.map { it.toModel(isFavorite = it.mediaStoreId in favSet) }
        }

    fun observeFavorites(): Flow<List<Song>> =
        combine(
            db.songDao().observeFavorites(),
            db.favoriteDao().observeFavoriteIds(),
        ) { list, favIds ->
            val favSet = favIds.toSet()
            list.map { it.toModel(isFavorite = it.mediaStoreId in favSet) }
        }

    fun observeArtists(): Flow<List<ArtistSummary>> = db.songDao().observeArtists()

    fun observeAlbums(): Flow<List<AlbumSummary>> = db.songDao().observeAlbums()

    fun observeSongsByArtist(artist: String): Flow<List<Song>> =
        combine(
            db.songDao().observeSongsByArtist(artist),
            db.favoriteDao().observeFavoriteIds(),
        ) { list, favIds ->
            val favSet = favIds.toSet()
            list.map { it.toModel(isFavorite = it.mediaStoreId in favSet) }
        }

    fun observeSongsByAlbum(album: String): Flow<List<Song>> =
        combine(
            db.songDao().observeSongsByAlbum(album),
            db.favoriteDao().observeFavoriteIds(),
        ) { list, favIds ->
            val favSet = favIds.toSet()
            list.map { it.toModel(isFavorite = it.mediaStoreId in favSet) }
        }

    fun observeGenres(): Flow<List<com.harper.ava.data.local.db.GenreSummary>> =
        db.songDao().observeGenres()

    fun observeSongsByGenre(genre: String): Flow<List<Song>> =
        combine(
            db.songDao().observeSongsByGenre(genre),
            db.favoriteDao().observeFavoriteIds(),
        ) { list, favIds ->
            val favSet = favIds.toSet()
            list.map { it.toModel(isFavorite = it.mediaStoreId in favSet) }
        }

    fun observeFolders(): Flow<List<com.harper.ava.data.local.db.FolderSummary>> =
        db.songDao().observeFolders()

    fun observeSongsByFolder(folderPath: String): Flow<List<Song>> =
        combine(
            db.songDao().observeSongsByFolder(folderPath),
            db.favoriteDao().observeFavoriteIds(),
        ) { list, favIds ->
            val favSet = favIds.toSet()
            list.map { it.toModel(isFavorite = it.mediaStoreId in favSet) }
        }

    /**
     * Replaces the library with the latest scan, atomically: upserts every
     * scanned song, then deletes songs the device no longer has. Favorites
     * and playlist memberships of surviving songs are untouched (they live
     * in their own tables); cross-refs of deleted songs cascade away.
     *
     * 0.7.0: the scanned rows are fresh (playCount 0, lastPlayedMs null),
     * so the stored counters are carried over first — before this fix a
     * rescan silently reset every song to 0 plays.
     */
    suspend fun syncLibrary(entities: List<SongEntity>): SyncResult {
        val beforeIds = db.songDao().allIds()
        val keepIds = entities.map { it.mediaStoreId }.toSet()
        val removedStale = beforeIds.count { it !in keepIds }
        db.withTransaction {
            val playStates = db.songDao().playStates().associateBy { it.mediaStoreId }
            val withCounters = entities.map { entity ->
                val saved = playStates[entity.mediaStoreId]
                if (saved == null) entity else entity.copy(
                    playCount = saved.playCount,
                    lastPlayedMs = saved.lastPlayedMs,
                )
            }
            db.songDao().upsertAll(withCounters)
            if (keepIds.isEmpty()) {
                db.songDao().deleteAllSongs()
            } else {
                db.songDao().deleteNotIn(keepIds.toList())
            }
        }
        return SyncResult(stored = entities.size, removedStale = removedStale)
    }

    fun search(query: String): Flow<List<Song>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return flowOf(emptyList())
        return combine(
            db.songDao().search(trimmed),
            db.favoriteDao().observeFavoriteIds(),
        ) { list, favIds ->
            val favSet = favIds.toSet()
            list.map { it.toModel(isFavorite = it.mediaStoreId in favSet) }
        }
    }

    /**
     * Flips a song's favorite state. Round 8: the decision is read from
     * the database, never from [Song.isFavorite].
     *
     * The Song handed to the toggle is a snapshot: in Now Playing it
     * comes from the player's queue, which is built at play time and
     * never refreshed with favorite state — so `isFavorite` was always
     * false there, the toggle always inserted (IGNORE on conflict), and
     * a like could never be removed. Lists were unaffected because
     * their flows re-emit fresh Song objects after every change.
     */
    suspend fun toggleFavorite(song: Song) {
        if (db.favoriteDao().isFavorite(song.id)) {
            db.favoriteDao().remove(song.id)
        } else {
            db.favoriteDao().add(FavoriteEntity(songId = song.id, addedAtMs = System.currentTimeMillis()))
        }
    }

    /**
     * Removes a song from the library after its file was deleted
     * (0.7.2 song delete). Runs only AFTER the MediaStore delete
     * succeeded — never before, so a cancelled/failed system dialog
     * leaves the library untouched. One transaction: the song row goes
     * (playlist memberships cascade via FK) and the favorite row is
     * removed explicitly (no FK there). Listening history is kept on
     * purpose — its snapshot survives the song leaving the library.
     * Room flows re-emit, so every list refreshes by itself.
     */
    suspend fun deleteSongFromLibrary(songId: Long) {
        db.withTransaction {
            db.songDao().deleteById(songId)
            db.favoriteDao().remove(songId)
        }
    }

    fun observePlaylists(): Flow<List<Playlist>> =
        db.playlistDao().observeAll().map { playlists ->
            playlists.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    createdAtMs = entity.createdAtMs,
                    songCount = 0, // filled by observePlaylistsWithCounts when needed
                )
            }
        }

    /**
     * Playlists with their real song counts and cover art (first song's
     * art). Stage 6: the Playlists tab used to show every playlist with a
     * count of 0 because it read [observePlaylists] directly — the count
     * enrichment lived only in HomeViewModel. Both screens now share this.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePlaylistsWithCounts(): Flow<List<Playlist>> =
        observePlaylists().flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    list.map { playlist ->
                        combine(
                            db.playlistDao().observeSongCount(playlist.id),
                            db.playlistDao().observeFirstArtUri(playlist.id),
                        ) { count, artUri ->
                            playlist.copy(songCount = count, coverArtUri = artUri)
                        }
                    },
                ) { it.toList() }
            }
        }

    fun observePlaylist(id: Long): Flow<Playlist?> =
        db.playlistDao().observeById(id).map { entity ->
            entity?.let { Playlist(id = it.id, name = it.name, createdAtMs = it.createdAtMs) }
        }

    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
        combine(
            db.playlistDao().observeWithSongs(playlistId),
            db.favoriteDao().observeFavoriteIds(),
        ) { withSongs, favIds ->
            val favSet = favIds.toSet()
            withSongs?.songs.orEmpty().map { it.toModel(isFavorite = it.mediaStoreId in favSet) }
        }

    fun observePlaylistSongCount(playlistId: Long): Flow<Int> =
        db.playlistDao().observeSongCount(playlistId)

    suspend fun createPlaylist(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name must not be blank" }
        return db.playlistDao().insert(
            PlaylistEntity(name = trimmed, createdAtMs = System.currentTimeMillis()),
        )
    }

    suspend fun deletePlaylist(id: Long) = db.playlistDao().deleteById(id)

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val position = db.playlistDao().maxPosition(playlistId) + 1
        db.playlistDao().addSong(PlaylistSongCrossRef(playlistId, songId, position))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        db.playlistDao().removeSong(playlistId, songId)

    suspend fun markPlayed(songId: Long) =
        db.songDao().markPlayed(songId, System.currentTimeMillis())

    // ------------------------------------------------------------------
    // Listening history (0.7.0 Insights) — one row per real play.
    // ------------------------------------------------------------------

    /**
     * Records the start of a real play: inserts a history row (with a
     * metadata snapshot) and refreshes the song's lastPlayedMs so the
     * Home "recently played" list also sees auto-advanced and queued
     * plays. playCount is NOT touched here — the tap-path ViewModels
     * still own that increment, and doing it here too would count every
     * tapped play twice. Returns the history row id (0 on failure).
     */
    suspend fun recordPlayStart(song: Song, atMs: Long = System.currentTimeMillis()): Long {
        val row = com.harper.ava.data.local.db.ListeningHistoryEntity(
            songId = song.id,
            playedAtMs = atMs,
            listenedMs = 0L,
            title = song.title,
            artist = song.artist,
            album = song.album,
            genre = song.genre,
            durationMs = song.durationMs,
            albumArtUri = song.albumArtUri,
        )
        return try {
            db.withTransaction {
                val id = db.listeningHistoryDao().insert(row)
                db.songDao().updateLastPlayed(song.id, atMs)
                id
            }
        } catch (e: Exception) {
            0L
        }
    }

    /** Stores how long one recorded play was actually listened to. */
    suspend fun setHistoryListened(historyId: Long, listenedMs: Long) {
        if (historyId <= 0L) return
        runCatching { db.listeningHistoryDao().setListened(historyId, listenedMs.coerceAtLeast(0L)) }
    }

    fun observeListeningHistory(): Flow<List<com.harper.ava.core.util.ListenEvent>> =
        db.listeningHistoryDao().observeAll().map { rows -> rows.map { it.toEvent() } }

    fun observeListeningHistorySince(sinceMs: Long): Flow<List<com.harper.ava.core.util.ListenEvent>> =
        db.listeningHistoryDao().observeSince(sinceMs).map { rows -> rows.map { it.toEvent() } }
}

private fun com.harper.ava.data.local.db.ListeningHistoryEntity.toEvent(): com.harper.ava.core.util.ListenEvent =
    com.harper.ava.core.util.ListenEvent(
        songId = songId,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = durationMs,
        playedAtMs = playedAtMs,
        listenedMs = listenedMs,
        albumArtUri = albumArtUri,
    )
