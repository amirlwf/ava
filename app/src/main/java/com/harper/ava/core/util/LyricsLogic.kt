package com.harper.ava.core.util

/**
 * Pure, Android-free LRC lyrics parsing. Everything here is unit-tested
 * with plain JUnit (no Robolectric, no device) — see LyricsLogicTest.
 *
 * LRC format handled:
 * - Timed lines: `[mm:ss.xx] text` (hundredths, or `[mm:ss.xxx]` millis).
 * - Several time tags on one line: the text is repeated at each time.
 * - Metadata tags: `[ti:]`, `[ar:]`, `[al:]`, `[by:]` (kept as title/artist).
 * - `[offset:+123]` / `[offset:-123]` shifts every timestamp (ms).
 * - Lines without a time tag: kept as plain (unsynced) text when the file
 *   has no timestamps at all; otherwise ignored (they carry no timing).
 */

data class LyricLine(val timeMs: Long, val text: String)

data class ParsedLyrics(
    val lines: List<LyricLine>,
    /** False when the file has no timestamps — show it as plain text. */
    val hasTimestamps: Boolean,
    val title: String? = null,
    val artist: String? = null,
)

private val TIME_TAG_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val META_TAG_REGEX = Regex("""\[(ti|ar|al|by):(.*)]""")
private val OFFSET_REGEX = Regex("""\[offset:([+-]?\d+)]""")

/**
 * Parses one LRC file's text. Never throws: malformed lines are skipped,
 * and an empty/garbage file parses to zero lines.
 */
fun parseLrc(text: String): ParsedLyrics {
    var offsetMs = 0L
    var title: String? = null
    var artist: String? = null
    val timed = mutableListOf<LyricLine>()
    val plain = mutableListOf<String>()
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        OFFSET_REGEX.find(line)?.let {
            offsetMs = it.groupValues[1].toLongOrNull() ?: 0L
            continue
        }
        val meta = META_TAG_REGEX.find(line)
        if (meta != null) {
            val value = meta.groupValues[2].trim().ifEmpty { null }
            when (meta.groupValues[1]) {
                "ti" -> title = value
                "ar" -> artist = value
            }
            continue
        }
        val tags = TIME_TAG_REGEX.findAll(line).toList()
        if (tags.isEmpty()) {
            if (line.startsWith("[")) continue // unknown tag line: skip, don't show as lyrics
            plain.add(line)
            continue
        }
        val lyricText = line.substringAfterLast("]").trim()
        if (lyricText.isEmpty()) continue
        for (tag in tags) {
            val minutes = tag.groupValues[1].toLongOrNull() ?: continue
            val seconds = tag.groupValues[2].toLongOrNull() ?: continue
            val frac = tag.groupValues[3]
            val millis = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100L // tenths
                2 -> frac.toLong() * 10L // hundredths
                else -> frac.take(3).toLong()
            }
            val timeMs = (minutes * 60_000L + seconds * 1_000L + millis + offsetMs)
                .coerceAtLeast(0L)
            timed.add(LyricLine(timeMs, lyricText))
        }
    }
    return if (timed.isNotEmpty()) {
        ParsedLyrics(
            lines = timed.sortedBy { it.timeMs },
            hasTimestamps = true,
            title = title,
            artist = artist,
        )
    } else {
        ParsedLyrics(
            lines = plain.map { LyricLine(-1L, it) },
            hasTimestamps = false,
            title = title,
            artist = artist,
        )
    }
}

/**
 * Index of the lyric line playing at [positionMs]: the last line whose
 * timestamp is at or before the position, or -1 before the first line.
 * Lines must be sorted by time (as [parseLrc] returns them).
 */
fun lyricIndexAt(lines: List<LyricLine>, positionMs: Long): Int {
    var index = -1
    for (i in lines.indices) {
        if (lines[i].timeMs <= positionMs) index = i else break
    }
    return index
}
