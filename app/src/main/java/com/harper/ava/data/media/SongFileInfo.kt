package com.harper.ava.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.core.net.toUri
import com.harper.ava.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File-level facts about a song. Every field may be null when unknowable. */
data class SongFileInfo(
    val fileName: String?,
    /** Human folder, e.g. "Music/Rock" — from RELATIVE_PATH when available. */
    val folder: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
)

/**
 * Reads file facts for a song from MediaStore + MediaMetadataRetriever.
 * Everything is best-effort and guarded: a missing column, a revoked
 * permission or an unreadable file yields nulls, never a crash.
 * Runs on Dispatchers.IO; callers show "unknown" for null fields.
 */
suspend fun loadSongFileInfo(context: Context, song: Song): SongFileInfo =
    withContext(Dispatchers.IO) {
        var fileName: String? = song.fileName
        var folder: String? = song.folderPath?.trimEnd('/')
        var mimeType: String? = null
        var sizeBytes: Long? = null
        runCatching {
            val cursor = context.contentResolver.query(
                android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    song.id,
                ),
                arrayOf(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SIZE,
                ),
                null,
                null,
                null,
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameCol = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val pathCol = it.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    val mimeCol = it.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                    val sizeCol = it.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    if (nameCol >= 0) fileName = it.getString(nameCol) ?: fileName
                    if (pathCol >= 0) {
                        it.getString(pathCol)?.trim()?.trimEnd('/')?.ifEmpty { null }
                            ?.let { value -> folder = value }
                    }
                    if (mimeCol >= 0) mimeType = it.getString(mimeCol)
                    if (sizeCol >= 0 && !it.isNull(sizeCol)) {
                        sizeBytes = it.getLong(sizeCol).takeIf { size -> size > 0L }
                    }
                }
            }
        }
        var bitrateKbps: Int? = null
        var sampleRateHz: Int? = null
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, song.uri.toUri())
                bitrateKbps = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_BITRATE,
                )?.toIntOrNull()?.div(1000)?.takeIf { it > 0 }
                sampleRateHz = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_SAMPLERATE,
                )?.toIntOrNull()?.takeIf { it > 0 }
            } finally {
                runCatching { retriever.release() }
            }
        }
        SongFileInfo(
            fileName = fileName,
            folder = folder,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
        )
    }

/** "audio/mpeg" -> "MP3", "audio/flac" -> "FLAC"; null when unknowable (UI shows localized "unknown"). */
fun formatOf(mimeType: String?, fileName: String?): String? {
    val fromMime = when (mimeType?.lowercase()) {
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/flac" -> "FLAC"
        "audio/x-wav", "audio/wav" -> "WAV"
        "audio/ogg", "audio/vorbis" -> "OGG"
        "audio/mp4", "audio/x-m4a" -> "M4A"
        "audio/aac" -> "AAC"
        "audio/opus" -> "OPUS"
        "audio/x-ms-wma" -> "WMA"
        else -> null
    }
    if (fromMime != null) return fromMime
    return fileName?.substringAfterLast('.', "")?.uppercase()
        ?.takeIf { it.isNotBlank() }
}
