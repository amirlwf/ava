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
 * Setting the trimmed clip as the system ringtone (0.2, Track G).
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
 *
 * Improve loop, run 25 — no more duplicate rows: pass Ava's own row
 * Uri for this song as [existingUri] (the one the editor stored on
 * the previous successful set) and the row is REUSED — its display
 * name is refreshed and its bytes are overwritten in place
 * (`openOutputStream` with `"wt"` truncates) — instead of inserting
 * a fresh row on every tap. A row the user deleted is detected by
 * the update returning 0 and falls through to a fresh insert, so a
 * stale stored Uri self-heals. A write failure on the reused row
 * deletes that row rather than leaving a truncated, broken ringtone
 * behind. The same cleanup applies to the insert path: a failed
 * copy after insert deletes the new row instead of leaving an empty
 * entry in the Ringtones folder.
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
    existingUri: Uri? = null,
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
            if (existingUri != null) {
                try {
                    if (reuseRingtoneRow(resolver, existingUri, values, trimmedFile)) {
                        RingtoneManager.setActualDefaultRingtoneUri(
                            context,
                            RingtoneManager.TYPE_RINGTONE,
                            existingUri,
                        )
                        return@withContext SetRingtoneResult.Success(existingUri)
                    }
                    // The row is gone (the user deleted it) — fall
                    // through to a fresh insert below.
                } catch (t: Throwable) {
                    // Ava's own row, now holding a truncated copy —
                    // remove it rather than leave a broken ringtone.
                    runCatching { resolver.delete(existingUri, null, null) }
                    return@withContext SetRingtoneResult.Failure(t)
                }
            }
            val uri = resolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return@withContext SetRingtoneResult.Failure(null)
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    trimmedFile.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Could not open ringtone output stream")
                RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_RINGTONE,
                    uri,
                )
                SetRingtoneResult.Success(uri)
            } catch (t: Throwable) {
                // Don't leave an empty row behind in the Ringtones folder.
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
        }.getOrElse { SetRingtoneResult.Failure(it) }
    }
}

/**
 * Overwrites Ava's own ringtone row [uri] with [trimmedFile] and
 * refreshes its display name from [values] (a renamed song gets its
 * new name). Returns true when the row now holds the new bytes, false
 * when the row no longer exists (update touches 0 rows). Throws when
 * the row exists but the new bytes could not be written — the caller
 * deletes the truncated row.
 *
 * `openOutputStream(uri, "wt")` truncates the row's content first;
 * the plain `"w"` mode would leave a tail of the old, longer clip
 * behind when the new segment is shorter.
 */
private fun reuseRingtoneRow(
    resolver: android.content.ContentResolver,
    uri: Uri,
    values: ContentValues,
    trimmedFile: File,
): Boolean {
    if (resolver.update(uri, values, null, null) <= 0) return false
    resolver.openOutputStream(uri, "wt")?.use { out ->
        trimmedFile.inputStream().use { input -> input.copyTo(out) }
    } ?: throw IllegalStateException("Could not open ringtone output stream")
    return true
}
