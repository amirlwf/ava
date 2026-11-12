package com.harper.ava.core.util

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.harper.ava.domain.model.Song

/**
 * MediaItem building for a [Song] (improve loop, run 1 — 2026-11-12).
 *
 * Until this run `PlayerRepository` built media items with uri + media
 * id only and NO `MediaMetadata`. That left the notification (and the
 * lock screen) blind: verified in the shipped media3-session 1.11.1
 * bytecode, `DefaultMediaNotificationProvider.getNotificationContentTitle`
 * returns `metadata.title` with no fallback and `getNotificationContentText`
 * returns `metadata.artist` — so with empty metadata the notification
 * showed no title and no artist line at all, and `createNotification`
 * found no `artworkData` / `artworkUri` to load a large icon from.
 * The 0.7.5 KDoc in `AvaMediaNotificationProvider` recorded this as a
 * separate change; this file is that change, still inside the safe
 * subclass path (no RemoteViews — that path crashed twice on Harper's
 * phone and is never repeated).
 *
 * [songMediaMetadata] is pure and runs in the offline JUnit suite
 * (no `Uri.parse` in it). [songToMediaItem] needs `android.net.Uri`
 * for the artwork uri and the media uri, so it cannot run on the JVM
 * — it is covered by the kotlinc type-check and the manual build,
 * not by a unit test.
 */
fun songMediaMetadata(song: Song): MediaMetadata =
    MediaMetadata.Builder()
        .setTitle(notificationTitle(song))
        .setArtist(song.artist)
        .setAlbumTitle(song.album)
        .build()

/**
 * Title the notification (and lock screen) shows for a [Song] — never
 * blank (0.7.6). The scanner already guarantees a non-blank title
 * ([normalizeScannedSong]), but a Song built any other way must not
 * post an empty title: fall back to the file name without its
 * extension, then to a synthetic id-based title. Artist/album stay
 * as the scanner stores them ("" when unknown — the provider's text
 * override falls back to the album, see `MediaItemLogicTest`).
 */
fun notificationTitle(song: Song): String =
    song.title.trim().ifEmpty {
        song.fileName?.let { filenameFromUri(it) }.orEmpty()
            .ifEmpty { "track-${song.id}" }
    }

/**
 * The full [MediaItem] for a [Song]: same uri + media id as before,
 * plus the metadata above and the album-art uri when the song has one.
 * A blank/absent art uri simply means no artwork (Media3 loads no
 * large icon then, exactly as before) — never a crash: the load runs
 * through Media3's `BitmapLoader`, which reports a failed load to the
 * provider instead of throwing.
 *
 * Behaviour is otherwise unchanged: playback keys off the uri, and
 * the queue / resume / rebuild paths all keep calling this through
 * the same `toMediaItem()` call sites.
 */
fun songToMediaItem(song: Song): MediaItem {
    val metadata = songMediaMetadata(song).buildUpon().apply {
        song.albumArtUri?.takeIf { it.isNotBlank() }?.let { setArtworkUri(it.toUri()) }
    }.build()
    return MediaItem.Builder()
        .setUri(song.uri)
        .setMediaId(song.id.toString())
        .setMediaMetadata(metadata)
        .build()
}
