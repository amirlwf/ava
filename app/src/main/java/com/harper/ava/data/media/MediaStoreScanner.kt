package com.harper.ava.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.harper.ava.core.util.RawScannedSong
import com.harper.ava.core.util.albumArtUriForAlbum
import com.harper.ava.core.util.dedupeScannedSongs
import com.harper.ava.core.util.normalizeScannedSong
import com.harper.ava.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of one library scan (always produced on Dispatchers.IO). */
data class ScanResult(
    val found: Int,
    val stored: Int,
    val removedStale: Int,
    val skipped: Int,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = error == null
}

/**
 * Reads the device's audio library through MediaStore and syncs it into
 * Room. Rules:
 * - Only rows with IS_MUSIC set (no ringtones / notifications).
 * - Runs fully on Dispatchers.IO; the UI only observes the result.
 * - A corrupt or unreadable row is counted in [ScanResult.skipped] and
 *   never aborts the scan; a revoked permission aborts with [ScanResult.error].
 * - After the scan, songs that no longer exist on the device are removed
 *   from the database (favorites and playlists survive: they are separate
 *   tables keyed by song id, and playlist cross-refs cascade on delete).
 * - Genre is fetched with a *batched* join (stage 5): one query for the
 *   genre list, then one members query per genre (tens of queries, not
 *   one per track). A failure here never aborts the scan — genres just
 *   stay unknown for that run.
 * - RELATIVE_PATH + DISPLAY_NAME are stored too: the Folders tab groups
 *   by folder, and lyrics/details use the file name.
 */
class MediaStoreScanner(
    private val context: Context,
    private val musicRepository: MusicRepository,
) {

    /**
     * True when the MediaStore actually has a thumbnail for this
     * album. Opens the albumart uri once and closes it immediately —
     * no decode, no bitmap, just proof the file exists and opens.
     * Any failure (no thumbnail, revoked permission, provider error)
     * means "no art": the caller stores null and the notification
     * falls back to Ava's default cover. Best-effort by design — a
     * probe failure must never fail the scan, and the probe runs on
     * Dispatchers.IO with the rest of the scan.
     *
     * [probeCache] is the caller's per-scan map (one entry per
     * distinct album id, created fresh by [rescan] for each scan):
     * a thumbnail can appear or disappear between scans, so probe
     * results must never survive a scan. A field cache would keep a
     * stale false (art added after the first scan ignored until the
     * process restarts) or a stale true (a deleted thumbnail stored
     * again, shadowing the default cover the way the broken albums/
     * uri did in 0.2).
     */
    private fun hasAlbumArt(albumId: Long, probeCache: MutableMap<Long, Boolean>): Boolean =
        probeCache.getOrPut(albumId) {
            val uri = albumArtUriForAlbum(albumId) ?: return@getOrPut false
            runCatching {
                context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                    ?.use { true } ?: false
            }.getOrDefault(false)
        }

    suspend fun rescan(): ScanResult = withContext(Dispatchers.IO) {
        // 0.3-fix (improve loop, run 10): the probe cache is created
        // fresh for this scan — see hasAlbumArt.
        val albumArtProbes = mutableMapOf<Long, Boolean>()
        val rawSongs = mutableListOf<RawScannedSong>()
        var skipped = 0
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val cursor = try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )
        } catch (e: SecurityException) {
            return@withContext ScanResult(0, 0, 0, 0, error = "permission_denied")
        } catch (e: Exception) {
            return@withContext ScanResult(0, 0, 0, 0, error = e.message ?: "scan_failed")
        }
        // A null cursor means the provider failed: never sync an empty list
        // on that basis, or a transient provider error would wipe the library.
        if (cursor == null) {
            return@withContext ScanResult(0, 0, 0, 0, error = "scan_failed")
        }
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackCol = it.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearCol = it.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val albumIdCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val relativePathCol = it.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val displayNameCol = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            while (it.moveToNext()) {
                try {
                    val id = it.getLong(idCol)
                    val albumId = if (albumIdCol >= 0) it.getLong(albumIdCol) else 0L
                    val uri = ContentUris
                        .withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        .toString()
                    // 0.2-fix (Track 1): the artwork uri is the albumart
                    // form ([albumArtUriForAlbum]) AND it is probed — an
                    // album with no MediaStore thumbnail stores null, so
                    // the notification falls back to Ava's default cover
                    // instead of posting with no cover. Probed once per
                    // distinct album id, not per song (see
                    // [hasAlbumArt]): most libraries have far fewer
                    // albums than songs, and the probe is one cheap
                    // file-descriptor open with no decode.
                    val artUri = if (hasAlbumArt(albumId, albumArtProbes)) {
                        albumArtUriForAlbum(albumId)
                    } else {
                        null
                    }
                    rawSongs += RawScannedSong(
                        mediaStoreId = id,
                        title = it.getString(titleCol),
                        artist = it.getString(artistCol),
                        album = it.getString(albumCol),
                        durationMs = it.getLong(durationCol),
                        uri = uri,
                        albumArtUri = artUri,
                        dateAddedMs = it.getLong(dateAddedCol) * 1_000L,
                        trackNumber = if (trackCol >= 0 && !it.isNull(trackCol)) {
                            it.getInt(trackCol)
                        } else {
                            null
                        },
                        year = if (yearCol >= 0 && !it.isNull(yearCol)) {
                            it.getInt(yearCol).takeIf { year -> year > 0 }
                        } else {
                            null
                        },
                        folderPath = if (relativePathCol >= 0) {
                            it.getString(relativePathCol)?.trim()?.ifEmpty { null }
                        } else {
                            null
                        },
                        fileName = if (displayNameCol >= 0) {
                            it.getString(displayNameCol)?.trim()?.ifEmpty { null }
                        } else {
                            null
                        },
                    )
                } catch (e: Exception) {
                    // One bad row (corrupt metadata, unreadable value) must
                    // never abort the whole scan.
                    skipped++
                }
            }
        }
        val deduped = dedupeScannedSongs(rawSongs)
        // Batched genre join: attach each track's genre (first one wins)
        // before normalizing. A failure here only loses genres, never songs.
        val genreById = runCatching { fetchGenreMap() }.getOrDefault(emptyMap())
        val entities = deduped.mapNotNull { raw ->
            val withGenre = if (genreById.isEmpty()) raw else raw.copy(
                genre = genreById[raw.mediaStoreId] ?: raw.genre,
            )
            normalizeScannedSong(withGenre) ?: run { skipped++; null }
        }
        val sync = try {
            musicRepository.syncLibrary(entities)
        } catch (e: Exception) {
            return@withContext ScanResult(deduped.size, 0, 0, skipped, error = e.message ?: "sync_failed")
        }
        ScanResult(
            found = deduped.size,
            stored = sync.stored,
            removedStale = sync.removedStale,
            skipped = skipped,
        )
    }

    /**
     * Batched genre lookup: one query lists all genres, then one members
     * query per genre maps audio ids to that genre's name. The genre
     * count on a phone is small (tens), so this is tens of queries —
     * not one per track. Any failure returns an empty map; the caller
     * treats that as "genres unknown for this run" and keeps scanning.
     * Runs on the caller's IO dispatcher.
     */
    private fun fetchGenreMap(): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        val genreCursor = context.contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null,
        ) ?: return emptyMap()
        val genres = mutableListOf<Pair<Long, String>>()
        genreCursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (it.moveToNext()) {
                val name = it.getString(nameCol)?.trim().orEmpty()
                if (name.isNotEmpty()) genres.add(it.getLong(idCol) to name)
            }
        }
        for ((genreId, genreName) in genres) {
            val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
            val members = context.contentResolver.query(
                membersUri,
                arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                null,
                null,
                null,
            ) ?: continue
            members.use {
                val audioIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                while (it.moveToNext()) {
                    val audioId = it.getLong(audioIdCol)
                    // First genre wins: a track in two genres keeps the first.
                    result.putIfAbsent(audioId, genreName)
                }
            }
        }
        return result
    }
}
