package com.harper.ava.core.util

import com.harper.ava.data.local.db.SongEntity
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pure, Android-free library logic. Everything here is unit-tested with
 * plain JUnit (no Robolectric, no device) — see QueueLogicTest and
 * ScanNormalizeTest.
 */

// ---------------------------------------------------------------------------
// MediaStore scan: raw rows in, clean entities out
// ---------------------------------------------------------------------------

/** One raw MediaStore audio row, before any cleaning. No Android types. */
data class RawScannedSong(
    val mediaStoreId: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val uri: String,
    val albumArtUri: String?,
    val dateAddedMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    /** Genre from the batched MediaStore genre join (stage 5); null when unknown. */
    val genre: String? = null,
    /** MediaStore RELATIVE_PATH, e.g. "Music/Rock/"; null when unknown. */
    val folderPath: String? = null,
    /** MediaStore DISPLAY_NAME, e.g. "song.mp3"; null when unknown. */
    val fileName: String? = null,
)

/** File name (no directories, no extension) from a content/file URI string. */
fun filenameFromUri(uri: String): String {
    val lastSegment = uri.substringAfterLast('/').substringBefore('?')
    return lastSegment.substringBeforeLast('.', lastSegment)
}

/**
 * Turns a raw MediaStore row into a storable [SongEntity], or `null` when
 * the row must be skipped: unknown id, empty URI, or a zero/negative
 * duration (corrupt or non-music file). Missing metadata never crashes
 * the scan — blank titles fall back to the file name, blank artist/album
 * stay blank and the UI renders the localized "Unknown artist/album".
 */
fun normalizeScannedSong(raw: RawScannedSong): SongEntity? {
    if (raw.mediaStoreId <= 0L) return null
    if (raw.uri.isBlank()) return null
    if (raw.durationMs <= 0L) return null
    val title = raw.title?.trim().orEmpty().ifEmpty {
        filenameFromUri(raw.uri).ifEmpty { "track-${raw.mediaStoreId}" }
    }
    return SongEntity(
        mediaStoreId = raw.mediaStoreId,
        title = title,
        artist = raw.artist?.trim().orEmpty(),
        album = raw.album?.trim().orEmpty(),
        durationMs = raw.durationMs,
        uri = raw.uri,
        albumArtUri = raw.albumArtUri,
        dateAddedMs = raw.dateAddedMs,
        trackNumber = raw.trackNumber,
        genre = raw.genre?.trim().orEmpty().ifEmpty { null },
        year = raw.year,
        folderPath = raw.folderPath?.trim().orEmpty().ifEmpty { null },
        fileName = raw.fileName?.trim().orEmpty().ifEmpty { null },
    )
}

/**
 * Display name of a MediaStore RELATIVE_PATH: the last segment
 * ("Music/Rock/" -> "Rock", "Music/" -> "Music"). Null/blank -> null.
 */
fun folderDisplayName(path: String?): String? {
    val trimmed = path?.trim()?.trimEnd('/')?.ifEmpty { return null } ?: return null
    return trimmed.substringAfterLast('/')
}

/** Serializes a queue (song ids, in order) for DataStore persistence. */
fun encodeQueueIds(ids: List<Long>): String = ids.joinToString(",")

/** Parses [encodeQueueIds] output; garbage segments are dropped, never crash. */
fun decodeQueueIds(encoded: String?): List<Long> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0L }
}

/** Drops duplicate MediaStore ids, keeping the first occurrence. */
fun dedupeScannedSongs(raws: List<RawScannedSong>): List<RawScannedSong> {
    val seen = LinkedHashSet<Long>()
    return raws.filter { seen.add(it.mediaStoreId) }
}

// ---------------------------------------------------------------------------
// Queue operations (pure list transforms; PlayerRepository mirrors them
// into ExoPlayer with the same calls)
// ---------------------------------------------------------------------------

/** Moves an item inside a list; out-of-range indices return the list unchanged. */
fun <T> moveItem(list: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in list.indices || toIndex !in list.indices) return list
    if (fromIndex == toIndex) return list
    val mutable = list.toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}

/** Removes the item at [index]; out-of-range indices return the list unchanged. */
fun <T> removeItemAt(list: List<T>, index: Int): List<T> {
    if (index !in list.indices) return list
    return list.toMutableList().also { it.removeAt(index) }
}

/** Inserts [item] right after the currently playing index (or at the end). */
fun <T> insertPlayNext(list: List<T>, currentIndex: Int, item: T): List<T> {
    val at = (currentIndex + 1).coerceIn(0, list.size)
    return list.toMutableList().also { it.add(at, item) }
}

/**
 * Shuffles everything after the first element and keeps the first element
 * (the currently playing song) in place. A [Random] is injected so tests
 * can seed it deterministically.
 */
fun <T> shuffleKeepingFirst(list: List<T>, random: Random = Random.Default): List<T> {
    if (list.size <= 1) return list
    val head = list.first()
    val tail = list.drop(1).shuffled(random)
    return listOf(head) + tail
}

/**
 * Where a long-press drag of [rowHeightPx]-tall rows should land.
 * [dragOffsetPx] is the total vertical drag distance (positive = down).
 * The result is clamped to the list bounds.
 */
fun dragTargetIndex(fromIndex: Int, dragOffsetPx: Float, rowHeightPx: Float, size: Int): Int {
    if (size <= 0 || rowHeightPx <= 0f) return fromIndex.coerceIn(0, (size - 1).coerceAtLeast(0))
    val steps = (dragOffsetPx / rowHeightPx).roundToInt()
    return (fromIndex + steps).coerceIn(0, size - 1)
}
