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
     */
    suspend fun syncLibrary(entities: List<SongEntity>): SyncResult {
        val beforeIds = db.songDao().allIds()
        val keepIds = entities.map { it.mediaStoreId }.toSet()
        val removedStale = beforeIds.count { it !in keepIds }
        db.withTransaction {
            db.songDao().upsertAll(entities)
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

    suspend fun toggleFavorite(song: Song) {
        if (song.isFavorite) {
            db.favoriteDao().remove(song.id)
        } else {
            db.favoriteDao().add(FavoriteEntity(songId = song.id, addedAtMs = System.currentTimeMillis()))
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
}
