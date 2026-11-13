package com.harper.ava.playback

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Setting the trimmed clip as the system ringtone (0.1-fix1, Track G).
 *
 * The real flow, no fake UI:
 * 1. The trimmed `.m4a` is inserted into MediaStore audio with
 *    `RELATIVE_PATH = "Ringtones"` and `IS_RINGTONE = 1` (scoped
 *    storage: inserting into a media collection needs no storage
 *    permission on API 29+).
 * 2. `RingtoneManager.setActualDefaultRingtoneUri` points the system
 *    ringtone at the inserted Uri.
 *
 * Step 2 needs the WRITE_SETTINGS grant (`Settings.System.canWrite`):
 * it is NOT a runtime permission — the user grants it once in system
 * Settings ("Modify system settings"). When it is missing this
 * returns [SetRingtoneResult.NeedsWriteSettings] and the editor shows
 * an explanation + a button that opens the grant screen; the trim is
 * NOT lost (the file stays in the cache, so the user can retry the
 * set step without trimming again).
 */
sealed interface SetRingtoneResult {
    data class Success(val uri: Uri) : SetRingtoneResult
    data object NeedsWriteSettings : SetRingtoneResult
    data class Failure(val cause: Throwable?) : SetRingtoneResult
}

suspend fun setAsRingtone(
    context: Context,
    trimmedFile: File,
    displayName: String,
): SetRingtoneResult {
    if (!Settings.System.canWrite(context)) return SetRingtoneResult.NeedsWriteSettings
    return withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$displayName.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones")
                put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
                put(MediaStore.Audio.Media.IS_ALARM, 0)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return@withContext SetRingtoneResult.Failure(null)
            resolver.openOutputStream(uri)?.use { out ->
                trimmedFile.inputStream().use { input -> input.copyTo(out) }
            } ?: return@withContext SetRingtoneResult.Failure(null)
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                uri,
            )
            SetRingtoneResult.Success(uri)
        }.getOrElse { SetRingtoneResult.Failure(it) }
    }
}
