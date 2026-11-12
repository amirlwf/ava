package com.harper.ava.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------

/** Cached copy of a MediaStore audio row. The MediaStore id is the primary key. */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumArtUri: String? = null,
    val dateAddedMs: Long = 0L,
    val trackNumber: Int? = null,
    val genre: String? = null,
    val year: Int? = null,
    /** MediaStore RELATIVE_PATH (stage 5): the Folders tab groups by this. */
    val folderPath: String? = null,
    /** MediaStore DISPLAY_NAME (stage 5): lyrics lookup + song details. */
    val fileName: String? = null,
    val lastPlayedMs: Long? = null,
    val playCount: Int = 0,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAtMs: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["mediaStoreId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("songId")],
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    /** Position inside the playlist (0-based). */
    val position: Int,
)

/** A favorite is a standalone row so it survives a library rescan. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAtMs: Long,
)

/**
 * One recorded play (0.7.0 Insights). The song's title/artist/album/
 * genre/duration/art are snapshotted into the row so Insights keeps
 * working after the song leaves the library (rescan / deleted file) —
 * and so the top lists need no join. Deliberately NO foreign key to
 * `songs`: deleting a song must not erase its listening history.
 * `listenedMs` starts at 0 and is filled in as the song is actually
 * listened to (pause / track change / stop); a row with 0 means the
 * song started but was skipped almost immediately.
 */
@Entity(
    tableName = "listening_history",
    indices = [Index("playedAtMs"), Index("songId")],
)
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val songId: Long,
    val playedAtMs: Long,
    val listenedMs: Long = 0L,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String? = null,
    val durationMs: Long = 0L,
    val albumArtUri: String? = null,
)

/** One artist row for the Library Artists tab: name + song count + a sample cover. */
data class ArtistSummary(
    val name: String,
    val songCount: Int,
    val albumArtUri: String?,
)

/** One album row for the Library Albums tab. */
data class AlbumSummary(
    val name: String,
    val artist: String,
    val songCount: Int,
    val albumArtUri: String?,
)

/** One genre row for the Library Genres tab (only songs with a known genre). */
data class GenreSummary(
    val name: String,
    val songCount: Int,
    val albumArtUri: String?,
)

/** One folder row for the Library Folders tab (MediaStore RELATIVE_PATH). */
data class FolderSummary(
    val name: String,
    val songCount: Int,
    val albumArtUri: String?,
)

data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "mediaStoreId",
        associateBy = androidx.room.Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId",
        ),
    )
    val songs: List<SongEntity>,
)

// ---------------------------------------------------------------------------
// DAOs
// ---------------------------------------------------------------------------

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeAllByTitle(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC")
    fun observeAllByArtist(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY dateAddedMs DESC")
    fun observeAllByDateAdded(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY durationMs DESC")
    fun observeAllByDuration(): Flow<List<SongEntity>>

    @Query(
        "SELECT * FROM songs WHERE title LIKE '%' || :query || '%' " +
            "OR artist LIKE '%' || :query || '%' " +
            "OR album LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC",
    )
    fun search(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE mediaStoreId = :id")
    suspend fun getById(id: Long): SongEntity?

    /** Queue restore: fetch several songs at once (order is fixed by the caller). */
    @Query("SELECT * FROM songs WHERE mediaStoreId IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT COUNT(*) FROM songs")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    /** Total play time of the whole library, for the Storage screen. */
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM songs")
    suspend fun totalDurationMs(): Long

    @Query("SELECT mediaStoreId FROM songs")
    suspend fun allIds(): List<Long>

    @Query("SELECT * FROM songs WHERE lastPlayedMs IS NOT NULL ORDER BY lastPlayedMs DESC LIMIT 10")
    fun observeRecentlyPlayed(): Flow<List<SongEntity>>

    @Query(
        "SELECT s.* FROM songs s INNER JOIN favorites f " +
            "ON s.mediaStoreId = f.songId ORDER BY f.addedAtMs DESC",
    )
    fun observeFavorites(): Flow<List<SongEntity>>

    @Query(
        "SELECT artist AS name, COUNT(*) AS songCount, MAX(albumArtUri) AS albumArtUri " +
            "FROM songs GROUP BY artist ORDER BY artist COLLATE NOCASE ASC",
    )
    fun observeArtists(): Flow<List<ArtistSummary>>

    @Query(
        "SELECT album AS name, artist AS artist, COUNT(*) AS songCount, " +
            "MAX(albumArtUri) AS albumArtUri FROM songs " +
            "GROUP BY album, artist ORDER BY album COLLATE NOCASE ASC",
    )
    fun observeAlbums(): Flow<List<AlbumSummary>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    fun observeSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY title COLLATE NOCASE ASC")
    fun observeSongsByAlbum(album: String): Flow<List<SongEntity>>

    /** Genres with at least one song; blank/unknown genres are excluded. */
    @Query(
        "SELECT genre AS name, COUNT(*) AS songCount, MAX(albumArtUri) AS albumArtUri " +
            "FROM songs WHERE genre IS NOT NULL AND genre != '' " +
            "GROUP BY genre ORDER BY genre COLLATE NOCASE ASC",
    )
    fun observeGenres(): Flow<List<GenreSummary>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title COLLATE NOCASE ASC")
    fun observeSongsByGenre(genre: String): Flow<List<SongEntity>>

    /** Folders with at least one song; songs without a folder path are excluded. */
    @Query(
        "SELECT folderPath AS name, COUNT(*) AS songCount, MAX(albumArtUri) AS albumArtUri " +
            "FROM songs WHERE folderPath IS NOT NULL AND folderPath != '' " +
            "GROUP BY folderPath ORDER BY folderPath COLLATE NOCASE ASC",
    )
    fun observeFolders(): Flow<List<FolderSummary>>

    @Query("SELECT * FROM songs WHERE folderPath = :folderPath ORDER BY title COLLATE NOCASE ASC")
    fun observeSongsByFolder(folderPath: String): Flow<List<SongEntity>>

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    /**
     * Deletes one song from the library (0.7.2 song delete). Playlist
     * memberships cascade away via the PlaylistSongCrossRef FK; the
     * favorite row has no FK and is removed explicitly by the caller
     * ([MusicRepository.deleteSongFromLibrary]). Listening history is
     * deliberately untouched (no FK — it keeps its snapshot).
     */
    @Query("DELETE FROM songs WHERE mediaStoreId = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: SongEntity)

    /** Removes every song whose id is not in [keepIds] (stale files after a rescan). */
    @Query("DELETE FROM songs WHERE mediaStoreId NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<Long>)

    @Query("UPDATE songs SET lastPlayedMs = :atMs, playCount = playCount + 1 WHERE mediaStoreId = :id")
    suspend fun markPlayed(id: Long, atMs: Long)

    /**
     * Updates only the recency marker, without touching playCount.
     * Used by the playback tracker (0.7.0): the tracker already knows
     * the play is real, and the tap-path ViewModels still own the
     * playCount increment — incrementing here too would double-count
     * every tapped play.
     */
    @Query("UPDATE songs SET lastPlayedMs = :atMs WHERE mediaStoreId = :id")
    suspend fun updateLastPlayed(id: Long, atMs: Long)

    /**
     * The play counters of every stored song, so a library rescan can
     * carry them over to the freshly scanned rows instead of wiping
     * them (upsertAll REPLACE used to reset every song to 0 plays).
     */
    @Query("SELECT mediaStoreId, playCount, lastPlayedMs FROM songs")
    suspend fun playStates(): List<SongPlayState>
}

/** Play counters of one stored song (rescan preservation, 0.7.0). */
data class SongPlayState(
    val mediaStoreId: Long,
    val playCount: Int,
    val lastPlayedMs: Long?,
)

@Dao
interface ListeningHistoryDao {
    @Insert
    suspend fun insert(event: ListeningHistoryEntity): Long

    /** Sets the actually-listened time of one recorded play. */
    @Query("UPDATE listening_history SET listenedMs = :listenedMs WHERE id = :id")
    suspend fun setListened(id: Long, listenedMs: Long)

    @Query("SELECT * FROM listening_history ORDER BY playedAtMs DESC")
    fun observeAll(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history WHERE playedAtMs >= :sinceMs ORDER BY playedAtMs DESC")
    fun observeSince(sinceMs: Long): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT COUNT(*) FROM listening_history")
    suspend fun count(): Int
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeWithSongs(id: Long): Flow<PlaylistWithSongs?>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun observeSongCount(playlistId: Long): Flow<Int>

    /** Cover art of the first playlist song that has art (playlist cover). */
    @Query(
        "SELECT s.albumArtUri FROM playlist_songs ps JOIN songs s " +
            "ON s.mediaStoreId = ps.songId WHERE ps.playlistId = :playlistId " +
            "AND s.albumArtUri IS NOT NULL ORDER BY ps.position ASC LIMIT 1",
    )
    fun observeFirstArtUri(playlistId: Long): Flow<String?>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSong(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT songId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun observeIsFavorite(songId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)

    @Delete
    suspend fun removeEntity(favorite: FavoriteEntity)
}
