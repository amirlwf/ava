package com.harper.ava.core.util

import com.harper.ava.data.local.db.SongEntity
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pure, Android-free library logic. Everything here is unit-tested with
 * plain JUnit (no Robolectric, no device) — see QueueLogicTest,
 * ScanNormalizeTest, ListKeyTest and LibraryLogicTest.
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

/**
 * File name (no directories, no extension) from a content/file URI string.
 * Surrounding whitespace is trimmed (run 7): a whitespace-only
 * DISPLAY_NAME must not become a whitespace title — both call sites
 * ([normalizeScannedSong] and `notificationTitle` in MediaItemLogic)
 * treat a blank result as "no name" and fall through to the next title
 * source. A real file name never starts or ends with a space that the
 * UI should keep.
 */
fun filenameFromUri(uri: String): String {
    val lastSegment = uri.substringAfterLast('/').substringBefore('?')
    return lastSegment.substringBeforeLast('.', lastSegment).trim()
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
        // 0.7.6: a real MediaStore audio URI carries no file name
        // ("content://media/external/audio/media/7" -> "7"), so an
        // untagged song used to be titled with its numeric id. Prefer
        // the stored DISPLAY_NAME; the URI segment is only a last
        // resort before the synthetic "track-<id>".
        raw.fileName?.let { filenameFromUri(it) }.orEmpty()
            .ifEmpty { filenameFromUri(raw.uri) }
            .ifEmpty { "track-${raw.mediaStoreId}" }
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
// LazyColumn item keys — unique across the WHOLE list scope, not per section
// ---------------------------------------------------------------------------

/**
 * Key for a song inside a named section of one LazyColumn. Home shows the
 * same song in up to three sections (recently played / recently added /
 * favorites) inside ONE LazyColumn, so a bare song id collides as soon as
 * a song is both recently played and recently added — that killed 0.5.5
 * on the real device (IllegalArgumentException: Key "1000201063" was
 * already used). Prefixing with the section name makes the same song a
 * different row in each section.
 */
fun homeSectionSongKey(section: String, songId: Long): String = "$section:$songId"

/**
 * Key for a queue row. A queue may legitimately hold the same song twice
 * (add-to-queue / play-next / restored checkpoint), so the song id alone
 * is not a key there either — the position distinguishes the two rows.
 * Trade-off: a drag-reorder changes the keys, so the moved rows
 * recompose instead of animating the move (animateItem has nothing
 * stable to track); the drag itself still works via translationY.
 */
fun queueRowKey(position: Int, songId: Long): String = "$position:$songId"

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

/** One (from, to) move that reorders a queue — remove at [fromIndex], insert at [toIndex]. */
data class QueueMove(val fromIndex: Int, val toIndex: Int)

/**
 * Plans the moves that turn [current] into [targetOrder] (the same items,
 * same multiset) by moving only items at or after [fromPosition] — the
 * head (the playing song and everything before it) is never touched.
 * Returns the moves in application order; applying them in order to
 * [current] yields [targetOrder].
 *
 * Improve loop, run 15: the search for the item that belongs at each
 * position starts AT that position, not at 0. A queue may hold the same
 * song twice (add-to-queue / play-next / restored checkpoint), and Song
 * is a data class, so a bare `indexOf` finds the FIRST equal item —
 * which can be the playing song itself sitting in the untouched head.
 * Searching only the not-yet-fixed suffix always finds the unfixed
 * copy, so the playing song is never moved and the head order survives.
 * A position that already holds the right item needs no move.
 */
fun <T> planReorderMoves(
    current: List<T>,
    targetOrder: List<T>,
    fromPosition: Int,
): List<QueueMove> {
    if (current.size != targetOrder.size) return emptyList()
    val working = current.toMutableList()
    val moves = mutableListOf<QueueMove>()
    for (position in fromPosition until targetOrder.size) {
        val wanted = targetOrder[position]
        if (working[position] == wanted) continue
        // The suffix multisets still match (positions below `position`
        // already hold their target items), so the wanted item is
        // somewhere at or after `position` — never in the fixed head.
        val from = (position until working.size).firstOrNull { working[it] == wanted }
            ?: continue // unreachable when targetOrder is a permutation of current
        moves += QueueMove(from, position)
        working.add(position, working.removeAt(from))
    }
    return moves
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
