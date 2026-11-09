package com.harper.ava.data.media

import android.content.Context
import android.provider.MediaStore
import com.harper.ava.core.util.ParsedLyrics
import com.harper.ava.core.util.parseLrc
import com.harper.ava.domain.model.Song
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of looking for a song's lyrics. Never throws. */
data class LyricsLoadResult(
    val lyrics: ParsedLyrics?,
    /** True when an .lrc file was found and parsed (even if it had no timed lines). */
    val found: Boolean,
)

/**
 * Finds a local `.lrc` file for a song and parses it.
 *
 * Lookup order (all real, on-device sources):
 * 1. MediaStore Files: a file whose DISPLAY_NAME is the song file's name
 *    with the extension replaced by `.lrc` (e.g. `song.mp3` -> `song.lrc`).
 * 2. The song file's own folder (via the MediaStore DATA column when the
 *    system still provides it): `<same folder>/<same base name>.lrc`.
 * 3. The user's SAF-picked music folders ([folderTreeUris]): the .lrc is
 *    searched inside those trees, where Ava holds a persisted read
 *    permission. This is the reliable path on Android 13+, where
 *    MediaStore Files does not expose plain-text files to an audio-only
 *    permission and the DATA column is often null.
 *
 * When no file is found the result is `found = false` and the UI shows
 * an honest empty state telling the user where to put the .lrc file.
 * Nothing is downloaded and nothing is faked.
 */
class LyricsRepository(private val context: Context) {

    suspend fun loadLyrics(
        song: Song,
        folderTreeUris: List<String> = emptyList(),
    ): LyricsLoadResult = withContext(Dispatchers.IO) {
        val baseName = song.fileName
            ?.substringBeforeLast('.', song.fileName)
            ?.takeIf { it.isNotBlank() }
            ?: song.title.substringBeforeLast('.', song.title).takeIf { it.isNotBlank() }
            ?: return@withContext LyricsLoadResult(null, false)
        val lrcName = "$baseName.lrc"

        // 1. MediaStore Files by display name.
        runCatching { loadFromMediaStoreFiles(lrcName) }.getOrNull()?.let { text ->
            return@withContext LyricsLoadResult(parseLrc(text), true)
        }
        // 2. Sibling file in the song's own folder.
        runCatching { loadSiblingFile(song, lrcName) }.getOrNull()?.let { text ->
            return@withContext LyricsLoadResult(parseLrc(text), true)
        }
        // 3. SAF-picked music folders (persisted read permission).
        val scanner = SafFolderScanner(context)
        for (treeUriString in folderTreeUris) {
            val treeUri = runCatching { android.net.Uri.parse(treeUriString) }.getOrNull()
                ?: continue
            if (!scanner.hasPersistedAccess(treeUriString)) continue
            val found = runCatching { scanner.findNamedFile(treeUri, lrcName) }.getOrNull()
                ?: continue
            val text = runCatching {
                context.contentResolver.openInputStream(found)?.bufferedReader()?.use { it.readText() }
            }.getOrNull() ?: continue
            return@withContext LyricsLoadResult(parseLrc(text), true)
        }
        LyricsLoadResult(null, false)
    }

    private fun loadFromMediaStoreFiles(lrcName: String): String? {
        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
            arrayOf(lrcName),
            null,
        ) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
            val uri = android.content.ContentUris.withAppendedId(
                MediaStore.Files.getContentUri("external"),
                id,
            )
            return context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            }
        }
    }

    private fun loadSiblingFile(song: Song, lrcName: String): String? {
        // DATA is deprecated but still the only way to get a file path for
        // a sibling lookup; it may be null on newer Android — then this
        // path simply does not apply and the caller falls through.
        val cursor = context.contentResolver.query(
            android.content.ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                song.id,
            ),
            arrayOf(MediaStore.Audio.Media.DATA),
            null,
            null,
            null,
        ) ?: return null
        val path = cursor.use {
            if (!it.moveToFirst()) return null
            it.getString(it.getColumnIndex(MediaStore.Audio.Media.DATA))
        } ?: return null
        val sibling = File(File(path).parentFile, lrcName)
        return if (sibling.isFile && sibling.canRead()) sibling.readText() else null
    }
}
