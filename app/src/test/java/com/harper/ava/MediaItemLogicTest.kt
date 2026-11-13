package com.harper.ava

import com.harper.ava.core.util.DEFAULT_ARTWORK_URI
import com.harper.ava.core.util.artworkUriFor
import com.harper.ava.core.util.songMediaMetadata
import com.harper.ava.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    ) = Song(
        id = 1000201063L,
        title = title,
        artist = artist,
        album = album,
        durationMs = 214_000L,
        uri = "content://media/external/audio/media/1000201063",
        albumArtUri = albumArtUri,
        fileName = fileName,
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
}
