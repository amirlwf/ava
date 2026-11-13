package com.harper.ava.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.harper.ava.core.util.TrimRange
import com.harper.ava.core.util.sanitizeTrimRange
import com.harper.ava.domain.model.Song
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Ringtone trim (0.1-fix1, Track G) — a real audio trim, no fake UI.
 *
 * Media3 Transformer re-encodes the chosen [TrimRange] of the song to
 * AAC in an MP4 container (`.m4a`), which is what Android accepts as
 * a ringtone. Clipping cannot be a passthrough: MP3 frames cannot be
 * muxed into MP4 without re-encoding, so Transformer decodes the
 * segment and re-encodes it — the honest cost is a few seconds of
 * work for a 30 s clip, on a background thread (Transformer is
 * async; this suspends until its listener fires).
 *
 * The output lands in the app cache (`ringtone-trim/trim.m4a`) and is
 * handed to [setAsRingtone], which copies it into MediaStore. The
 * cache file is overwritten on every trim; nothing is kept in the
 * library or the user's Music folder.
 *
 * Transformer is an explicit dependency now (media3-transformer
 * 1.11.1, resolved through `scripts/maven-resolve.py`); the manual
 * build's dex audit covers its classes like every other dep.
 *
 * API note (verified against the 1.11.1 transformer jar with javap —
 * the 1.9-era shape does NOT exist in 1.11.1): listeners are added
 * with `Transformer.Builder.addListener(Listener)` (no `setListener`);
 * clipping lives on the MediaItem as
 * `MediaItem.ClippingConfiguration` (no standalone
 * `transformer.ClippingConfiguration`, no
 * `EditedMediaItem.Builder.setClippingConfiguration`); the edited item
 * is built with `EditedMediaItem.Builder(mediaItem)`.
 */
sealed interface TrimResult {
    data class Success(val file: File) : TrimResult
    data class Failure(val cause: Throwable?) : TrimResult
}

suspend fun trimSongForRingtone(
    context: Context,
    song: Song,
    range: TrimRange,
): TrimResult {
    val safeRange = sanitizeTrimRange(range.startMs, range.endMs, song.durationMs)
    val outputDir = File(context.cacheDir, "ringtone-trim").apply { mkdirs() }
    val outputFile = File(outputDir, "trim.m4a")
    if (outputFile.exists()) outputFile.delete()
    return try {
        val mediaItem = MediaItem.Builder()
            .setUri(song.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(safeRange.startMs)
                    .setEndPositionMs(safeRange.endMs)
                    .build(),
            )
            .build()
        val edited = EditedMediaItem.Builder(mediaItem).build()
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) {
                                continuation.resume(TrimResult.Success(outputFile))
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (continuation.isActive) {
                                continuation.resume(TrimResult.Failure(exportException))
                            }
                        }
                    },
                )
                .build()
            continuation.invokeOnCancellation { transformer.cancel() }
            transformer.start(edited, outputFile.absolutePath)
        }
    } catch (t: Throwable) {
        TrimResult.Failure(t)
    }
}
