package com.harper.ava

import com.harper.ava.core.util.DEFAULT_ARTWORK_URI
import com.harper.ava.core.util.albumArtUriForAlbum
import com.harper.ava.core.util.artworkUriFor
import com.harper.ava.core.util.effectiveAlbumArtUri
import com.harper.ava.core.util.isBrokenAlbumArtUri
import com.harper.ava.core.util.notificationSubText
import com.harper.ava.core.util.notificationTitle
import com.harper.ava.core.util.songMediaMetadata
import com.harper.ava.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Improve loop, run 1 — the media items carried no MediaMetadata, so
 * the notification showed no title, no artist and no cover art
 * (verified in the media3-session 1.11.1 bytecode). These tests pin
 * the metadata that now travels with every song. The artwork uri
 * itself is attached in `songToMediaItem`, which needs `Uri.parse`
 * and therefore cannot run in this offline JVM suite.
 */
class MediaItemLogicTest {

    private fun song(
        title: String = "Midnight Rain",
        artist: String = "Ava Band",
        album: String = "Night Sessions",
        albumArtUri: String? = "content://media/external/audio/albumart/7",
        fileName: String? = null,
        genre: String? = null,
        year: Int? = null,
        trackNumber: Int? = null,
    ) = Song(
        id = 1000201063L,
        title = title,
        artist = artist,
        album = album,
        durationMs = 214_000L,
        uri = "content://media/external/audio/media/1000201063",
        albumArtUri = albumArtUri,
        fileName = fileName,
        genre = genre,
        year = year,
        trackNumber = trackNumber,
    )

    @Test
    fun `metadata carries title artist and album`() {
        val metadata = songMediaMetadata(song())
        assertEquals("Midnight Rain", metadata.title)
        assertEquals("Ava Band", metadata.artist)
        assertEquals("Night Sessions", metadata.albumTitle)
    }

    @Test
    fun `metadata keeps unknown artist and album as empty text`() {
        // normalizeScannedSong stores "" for unknown artist/album; the
        // provider's text override falls back to the album, so the
        // metadata must keep the empty strings, not turn them null.
        val metadata = songMediaMetadata(song(artist = "", album = ""))
        assertEquals("", metadata.artist)
        assertEquals("", metadata.albumTitle)
    }

    @Test
    fun `metadata has no artwork uri without artwork`() {
        // Artwork is attached separately in songToMediaItem (Uri.parse);
        // the pure metadata builder must not invent an artwork uri.
        val metadata = songMediaMetadata(song(albumArtUri = null))
        assertNull(metadata.artworkUri)
        assertNull(metadata.artworkData)
    }

    @Test
    fun `metadata title is the scanned fallback, never blank`() {
        val metadata = songMediaMetadata(song(title = "track-1000201063"))
        assertEquals("track-1000201063", metadata.title)
    }

    @Test
    fun `blank title falls back to the file name without its extension`() {
        // 0.7.6: a Song built outside the scanner must still never
        // post an empty notification title.
        val metadata = songMediaMetadata(song(title = "  ", fileName = "my-song.mp3"))
        assertEquals("my-song", metadata.title)
    }

    @Test
    fun `blank title without a file name falls back to the track id`() {
        val metadata = songMediaMetadata(song(title = "", fileName = null))
        assertEquals("track-1000201063", metadata.title)
    }

    // -- notificationTitle, direct -------------------------------------------
    // Improve loop, run 12 — the fallback chain above is pinned
    // through songMediaMetadata, but two edges only live in
    // notificationTitle itself: a padded (not blank) title is trimmed
    // rather than replaced, and a whitespace-only file name is not a
    // file name at all.

    @Test
    fun `notificationTitle trims a padded title instead of falling back`() {
        assertEquals("Midnight Rain", notificationTitle(song(title = "  Midnight Rain  ")))
    }

    @Test
    fun `notificationTitle falls back to the track id for a whitespace-only file name`() {
        assertEquals("track-1000201063", notificationTitle(song(title = "", fileName = "   ")))
    }

    // --- 0.1-fix1 (Track A): the notification always has artwork ---

    @Test
    fun `artwork uri is the song album art when present`() {
        assertEquals(
            "content://media/external/audio/albumart/7",
            artworkUriFor(song()),
        )
    }

    @Test
    fun `artwork uri is the ava default cover without album art`() {
        assertEquals(DEFAULT_ARTWORK_URI, artworkUriFor(song(albumArtUri = null)))
        assertEquals(DEFAULT_ARTWORK_URI, artworkUriFor(song(albumArtUri = "")))
        assertEquals(DEFAULT_ARTWORK_URI, artworkUriFor(song(albumArtUri = "   ")))
    }

    @Test
    fun `default artwork uri is an android resource uri to a raster drawable`() {
        // The stock BitmapLoader loads artworkUri through
        // DefaultDataSource, which routes android.resource URIs to
        // RawResourceDataSource — a vector drawable would fail to
        // decode there, so the default art must be a PNG drawable.
        assertEquals(
            "android.resource://com.harper.ava/drawable/ava_default_artwork",
            DEFAULT_ARTWORK_URI,
        )
    }

    // --- 0.2-fix (Track 1): the albumart uri form ---

    @Test
    fun `album art uri uses the albumart form, not the albums row uri`() {
        // 0.2 root cause: the scanner stored the album ROW uri
        // (content://media/external/audio/albums/<id>), which is
        // metadata only — opening it as an image stream fails, so
        // Media3's BitmapLoader dropped every cover silently. The
        // albumart form is the one the MediaProvider serves as a
        // thumbnail file.
        assertEquals(
            "content://media/external/audio/albumart/7",
            albumArtUriForAlbum(7L),
        )
        assertEquals(
            "content://media/external/audio/albumart/1000201063",
            albumArtUriForAlbum(1000201063L),
        )
    }

    @Test
    fun `album art uri is null without an album id`() {
        assertNull(albumArtUriForAlbum(0L))
        assertNull(albumArtUriForAlbum(-1L))
    }

    @Test
    fun `a stored broken albums row uri falls back to the ava default cover`() {
        // 0.3-feedback fix — rows stored before 0.3 hold the broken
        // albums/ ROW uri, and Harper may never have rescanned. A
        // non-blank broken uri must not shadow the default cover:
        // the untagged song in his 0.3 screenshots posted with no
        // cover at all. The normalized uri (and with it the
        // notification, via `songToMediaItem` →
        // `setArtworkUri(artworkUriFor(song).toUri())` → the
        // provider's `getBitmap(artworkUri)`) must carry the default
        // Galaxy cover with no rescan.
        val broken = "content://media/external/audio/albums/7"
        assertEquals(DEFAULT_ARTWORK_URI, artworkUriFor(song(albumArtUri = broken)))
        val brokenBig = "content://media/external/audio/albums/1000201063"
        assertEquals(DEFAULT_ARTWORK_URI, artworkUriFor(song(albumArtUri = brokenBig)))
    }

    @Test
    fun `effective album art uri drops broken and blank uris and keeps the albumart form`() {
        assertNull(effectiveAlbumArtUri(null))
        assertNull(effectiveAlbumArtUri(""))
        assertNull(effectiveAlbumArtUri("   "))
        assertNull(effectiveAlbumArtUri("content://media/external/audio/albums/7"))
        assertNull(effectiveAlbumArtUri("content://media/external/audio/albums/1000201063"))
        val good = "content://media/external/audio/albumart/7"
        assertEquals(good, effectiveAlbumArtUri(good))
    }

    @Test
    fun `broken album art detection only matches the albums row form`() {
        assertTrue(isBrokenAlbumArtUri("content://media/external/audio/albums/7"))
        assertFalse(isBrokenAlbumArtUri(null))
        assertFalse(isBrokenAlbumArtUri(""))
        // The working albumart form must NOT be treated as broken.
        assertFalse(isBrokenAlbumArtUri("content://media/external/audio/albumart/7"))
        assertFalse(isBrokenAlbumArtUri("android.resource://com.harper.ava/drawable/ava_default_artwork"))
    }

    // --- Frontend + notification round (2026-11-14): richer metadata ---
    // The notification / lock screen / Android Auto already consume
    // duration, genre, track number and release year — Ava never
    // sent them. Both Auxio and Metro set them; the metadata builder
    // now does too (album artist deliberately NOT set — the scanner
    // stores no separate album artist).

    @Test
    fun `metadata carries duration`() {
        assertEquals(214_000L, songMediaMetadata(song()).durationMs)
    }

    @Test
    fun `metadata carries genre when present`() {
        assertEquals("Jazz", songMediaMetadata(song(genre = "Jazz")).genre)
    }

    @Test
    fun `metadata has no genre when blank or absent`() {
        assertNull(songMediaMetadata(song(genre = null)).genre)
        assertNull(songMediaMetadata(song(genre = "")).genre)
        assertNull(songMediaMetadata(song(genre = "   ")).genre)
    }

    @Test
    fun `metadata carries track number and release year when present`() {
        val metadata = songMediaMetadata(song(trackNumber = 3, year = 2024))
        assertEquals(3, metadata.trackNumber)
        assertEquals(2024, metadata.releaseYear)
    }

    @Test
    fun `metadata has no track number or release year when absent`() {
        val metadata = songMediaMetadata(song())
        assertNull(metadata.trackNumber)
        assertNull(metadata.releaseYear)
    }

    // --- Frontend + notification round (2026-11-14): notification subText ---
    // The album rides as the notification's third line — but only
    // when the artist is non-blank, so it never duplicates the
    // second line's album fallback (see notificationSubText KDoc).

    @Test
    fun `notification subText is the album when artist and album are present`() {
        val metadata = songMediaMetadata(song())
        assertEquals("Night Sessions", notificationSubText(metadata))
    }

    @Test
    fun `notification subText is null when the artist is blank`() {
        val metadata = songMediaMetadata(song(artist = ""))
        assertNull(notificationSubText(metadata))
    }

    @Test
    fun `notification subText is null when the album is blank`() {
        val metadata = songMediaMetadata(song(album = ""))
        assertNull(notificationSubText(metadata))
    }

    @Test
    fun `notification subText is null when both artist and album are blank`() {
        val metadata = songMediaMetadata(song(artist = "", album = ""))
        assertNull(notificationSubText(metadata))
    }
}
