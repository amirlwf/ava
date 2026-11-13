package com.harper.ava.core.util

/**
 * Ringtone editing (0.1-fix1, Track G) — the pure decisions, so the
 * offline JUnit suite can pin them. The actual trim runs through
 * Media3 Transformer (re-encodes the segment to AAC/MP4) in
 * `RingtoneTrimmer`, and the system ringtone is set through
 * MediaStore + `RingtoneManager` — neither can run on the JVM.
 *
 * A ringtone is a short segment of a song: at least 1 s, at most
 * 60 s, inside the song. The editor defaults to the first 30 s
 * (or the whole song when it is shorter).
 */

/** Shortest ringtone segment the editor allows. */
const val RINGTONE_MIN_MS: Long = 1_000L

/** Longest ringtone segment the editor allows. */
const val RINGTONE_MAX_MS: Long = 60_000L

/** Default segment length when the song is long enough. */
const val RINGTONE_DEFAULT_MS: Long = 30_000L

/** A trimmed ringtone segment: [startMs, endMs) inside the song. */
data class TrimRange(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

/** The editor's starting segment for a song [durationMs] long. */
fun defaultTrimRange(durationMs: Long): TrimRange =
    TrimRange(0L, minOf(durationMs.coerceAtLeast(0L), RINGTONE_DEFAULT_MS))

/**
 * Clamps a user-chosen [startMs]/[endMs] pair into a valid segment
 * for a song [durationMs] long: start inside the song, end after
 * start by at least [RINGTONE_MIN_MS], end inside the song, length
 * at most [RINGTONE_MAX_MS]. A song shorter than the minimum still
 * yields its whole length (start 0, end = duration) rather than an
 * empty range.
 */
fun sanitizeTrimRange(startMs: Long, endMs: Long, durationMs: Long): TrimRange {
    val duration = durationMs.coerceAtLeast(0L)
    if (duration <= RINGTONE_MIN_MS) return TrimRange(0L, duration)
    val start = startMs.coerceIn(0L, duration - RINGTONE_MIN_MS)
    val end = endMs
        .coerceIn(start + RINGTONE_MIN_MS, duration)
        .coerceAtMost(start + RINGTONE_MAX_MS)
    return TrimRange(start, end)
}

/**
 * A safe file name for the trimmed ringtone, from the song [title]:
 * illegal file-name characters become `_`, surrounding whitespace
 * and dots are trimmed, an empty result falls back to
 * "ava-ringtone", and the name is capped (MediaStore display names
 * are short). The `.m4a` extension is added by the caller.
 */
fun ringtoneFileName(title: String): String {
    val cleaned = title
        .map { if (it in "/\\:*?\"<>|") '_' else it }
        .joinToString("")
        .trim()
        // A title made only of dots/illegal chars becomes only dots and
        // underscores (e.g. "///" -> "___") — trim those too, so the
        // empty fallback below fires instead of a "___" file name.
        .trim('.', '_')
        .trim()
    val base = cleaned.ifEmpty { "ava-ringtone" }
    return base.take(80)
}
