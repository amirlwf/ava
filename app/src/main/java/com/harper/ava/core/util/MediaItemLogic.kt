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
 * The artwork URI for a [Song]: the song's own album art when it has
 * one, otherwise Ava's branded default cover
 * (`res/drawable/ava_default_artwork.png` — Galaxy hero gradient +
 * white note). 0.1-fix1 (Track A): songs without album art used to
 * post a notification (and lock screen) with NO artwork at all, so
 * the shade's media player showed a blank grey tile — the single
 * biggest reason the notification "doesn't feel like the app's".
 * The default art is a raster PNG on purpose: the stock
 * `BitmapLoader` loads `artworkUri` through `DefaultDataSource`,
 * which routes `android.resource` URIs to `RawResourceDataSource`
 * (verified in the shipped media3-datasource 1.11.1 bytecode) — a
 * vector drawable would fail to decode there. Pure (a plain string),
 * so the offline JUnit suite pins it.
 *
 * 0.2-fix (Track 1) — the root cause of Harper's "no cover at all"
 * report on 0.2: the scanner used to store the album ROW uri
 * (`content://media/external/audio/albums/<id>`) as the artwork
 * uri. That uri is for querying album metadata — it carries no
 * image data, so `ContentResolver.openInputStream` on it fails and
 * Media3's `BitmapLoader` drops the artwork silently (caught, no
 * large icon, no crash). Worse, the broken uri is non-blank, so it
 * shadowed the working default cover: every tagged song posted with
 * no cover. The correct form is the albumart uri
 * ([albumArtUriForAlbum]) — the documented
 * `content://media/external/audio/albumart/<id>`, which the
 * MediaProvider serves as a thumbnail file. The scanner also probes
 * it (see `MediaStoreScanner`): an album with no thumbnail stores
 * null, so the default cover applies and a cover is genuinely
 * guaranteed.
 */
const val DEFAULT_ARTWORK_URI =
    "android.resource://com.harper.ava/drawable/ava_default_artwork"

/**
 * The album-art URI for a MediaStore album id, or null when the song
 * has no album. The `albumart` form is the one the MediaProvider
 * serves as an image: `content://media/external/audio/albumart/<id>`
 * opens with `openInputStream` / `openFileDescriptor` (AOSP
 * MusicUtils, the documented pattern). The albums ROW uri
 * (`content://media/external/audio/albums/<id>`) looks almost the
 * same but is metadata only — opening it as a stream fails, which
 * is exactly how 0.2 lost every cover. Pure, so the offline JUnit
 * suite pins the form.
 */
fun albumArtUriForAlbum(albumId: Long): String? =
    if (albumId > 0L) "content://media/external/audio/albumart/$albumId" else null

fun artworkUriFor(song: Song): String =
    song.albumArtUri?.takeIf { it.isNotBlank() } ?: DEFAULT_ARTWORK_URI

/**
 * The full [MediaItem] for a [Song]: same uri + media id as before,
 * plus the metadata above and the album-art uri — or Ava's default
 * cover when the song has none ([artworkUriFor]), so the notification
 * and lock screen ALWAYS show artwork. A failed load is never a
 * crash: the load runs through Media3's `BitmapLoader`, which reports
 * a failed load to the provider instead of throwing.
 *
 * Behaviour is otherwise unchanged: playback keys off the uri, and
 * the queue / resume / rebuild paths all keep calling this through
 * the same `toMediaItem()` call sites.
 */
fun songToMediaItem(song: Song): MediaItem {
    val metadata = songMediaMetadata(song).buildUpon().apply {
        setArtworkUri(artworkUriFor(song).toUri())
    }.build()
    return MediaItem.Builder()
        .setUri(song.uri)
        .setMediaId(song.id.toString())
        .setMediaMetadata(metadata)
        .build()
}
