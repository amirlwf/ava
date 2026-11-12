package com.harper.ava.core.util

import java.util.Calendar

/**
 * Pure, Android-free listening-analytics logic (0.7.0 Insights).
 * Everything here is unit-tested with plain JUnit (no Robolectric, no
 * device) — see InsightsLogicTest.
 *
 * Why this file exists: the old counters on [SongEntity] (playCount +
 * lastPlayedMs) can only answer "how many times ever" and "when last".
 * They cannot answer "how long did I listen today / this week", "what
 * are my top songs this month" or "which hour do I listen most" — those
 * need one timestamped row per real play, which is what the
 * `listening_history` table stores. This file turns those rows into the
 * numbers the Insights screen shows, with no Android dependency so the
 * math stays testable.
 */

/** One recorded play, as the Insights screen sees it (snapshot fields, so a row stays meaningful even after the song leaves the library). */
data class ListenEvent(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val durationMs: Long,
    val playedAtMs: Long,
    /** Milliseconds actually listened (0 while the song is still playing). */
    val listenedMs: Long,
    val albumArtUri: String? = null,
)

/** One ranked song: plays in the window + time actually listened. */
data class TopSong(
    val songId: Long,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val plays: Int,
    val listenedMs: Long,
)

/** One ranked artist / album / genre. */
data class TopNamed(
    val name: String,
    val plays: Int,
    val listenedMs: Long,
)

/** Listening totals for one calendar day (local time). */
data class DayTotal(
    val dayStartMs: Long,
    val listenedMs: Long,
    val plays: Int,
)

/** Everything the Insights screen shows, computed from raw events. */
data class InsightsData(
    val totalPlays: Int,
    val totalListenedMs: Long,
    val distinctDays: Int,
    val listenedTodayMs: Long,
    val playsToday: Int,
    val listenedWeekMs: Long,
    val playsWeek: Int,
    val listenedMonthMs: Long,
    val playsMonth: Int,
    val topSongs: List<TopSong>,
    val topArtists: List<TopNamed>,
    val topAlbums: List<TopNamed>,
    val topGenres: List<TopNamed>,
    /** Oldest first: the last 7 calendar days, today last. */
    val last7Days: List<DayTotal>,
    /** Hour of day (0–23, local) with the most plays, or null with no history. */
    val peakHour: Int?,
    /** Consecutive days with at least one play, ending today or yesterday. */
    val streakDays: Int,
)

/** Minimum gap between two recorded starts of the SAME song. */
const val INSIGHTS_MIN_REPLAY_GAP_MS: Long = 30_000L

private const val DAY_MS: Long = 24L * 60L * 60L * 1000L

/**
 * Whether starting [songId] at [nowMs] is a genuinely new play worth
 * recording.
 *
 * A new song always counts. The same song counts again only after
 * [minGapMs] — that swallows the double-fire of the playback callbacks
 * (playSongs + onMediaItemTransition + onIsPlayingChanged can all report
 * the same start within milliseconds) and a resume / rebuild of the
 * player, while a real replay minutes later still counts. A clock that
 * moved backwards never blocks a record.
 */
fun shouldRecordPlay(
    lastSongId: Long?,
    lastAtMs: Long,
    songId: Long,
    nowMs: Long,
    minGapMs: Long = INSIGHTS_MIN_REPLAY_GAP_MS,
): Boolean {
    if (lastSongId == null) return true
    if (songId != lastSongId) return true
    if (nowMs < lastAtMs) return true
    return nowMs - lastAtMs >= minGapMs
}

/**
 * Listened time for one stretch of a song: the player position moved
 * from [startPositionMs] to [currentPositionMs]. Clamped to [0, duration]
 * — a seek backwards never yields negative time, and a scrub forward
 * never credits more than the song's length. Songs with an unknown
 * duration (<= 0) keep the raw non-negative stretch.
 */
fun segmentListenedMs(startPositionMs: Long, currentPositionMs: Long, durationMs: Long): Long {
    val raw = (currentPositionMs - startPositionMs).coerceAtLeast(0L)
    return if (durationMs > 0L) raw.coerceAtMost(durationMs) else raw
}

/** Start of the calendar day (local time) that contains [timeMs]. */
fun startOfDayMs(timeMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Hour of day (0–23, local) that contains [timeMs]. */
fun hourOfDay(timeMs: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMs
    return cal.get(Calendar.HOUR_OF_DAY)
}

/**
 * Short listening-time label for summary cards: "45m", "2h 05m".
 * Zero or negative reads as "0m" — never blank, never negative.
 */
fun formatListeningTime(listenedMs: Long): String {
    val totalMinutes = (listenedMs.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "%dh %02dm".format(hours, minutes) else "%dm".format(minutes)
}

/**
 * Aggregates raw [events] into [InsightsData] as of [nowMs].
 *
 * Windows (local calendar days): today = since midnight, week = the
 * last 7 days including today, month = the last 30 days including
 * today. Top lists rank plays inside the 30-day window (recent taste,
 * not a lifetime fossil); totals and streaks use all of history.
 */
fun computeInsights(events: List<ListenEvent>, nowMs: Long, topN: Int = 5): InsightsData {
    val todayStart = startOfDayMs(nowMs)
    val weekStart = todayStart - 6L * DAY_MS
    val monthStart = todayStart - 29L * DAY_MS

    val todayEvents = events.filter { it.playedAtMs >= todayStart }
    val weekEvents = events.filter { it.playedAtMs >= weekStart }
    val monthEvents = events.filter { it.playedAtMs >= monthStart }

    val last7Days = (6 downTo 0).map { back ->
        val dayStart = todayStart - back * DAY_MS
        val dayEvents = events.filter { it.playedAtMs in dayStart until dayStart + DAY_MS }
        DayTotal(
            dayStartMs = dayStart,
            listenedMs = dayEvents.sumOf { it.listenedMs },
            plays = dayEvents.size,
        )
    }

    val dayStarts = events.map { startOfDayMs(it.playedAtMs) }.toSortedSet()
    val streakDays = computeStreak(dayStarts, todayStart)

    val peakHour = events
        .groupingBy { hourOfDay(it.playedAtMs) }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

    return InsightsData(
        totalPlays = events.size,
        totalListenedMs = events.sumOf { it.listenedMs },
        distinctDays = dayStarts.size,
        listenedTodayMs = todayEvents.sumOf { it.listenedMs },
        playsToday = todayEvents.size,
        listenedWeekMs = weekEvents.sumOf { it.listenedMs },
        playsWeek = weekEvents.size,
        listenedMonthMs = monthEvents.sumOf { it.listenedMs },
        playsMonth = monthEvents.size,
        topSongs = monthEvents
            .groupBy { it.songId }
            .map { (_, plays) ->
                val first = plays.first()
                TopSong(
                    songId = first.songId,
                    title = first.title,
                    artist = first.artist,
                    albumArtUri = first.albumArtUri,
                    plays = plays.size,
                    listenedMs = plays.sumOf { it.listenedMs },
                )
            }
            .sortedWith(compareByDescending<TopSong> { it.plays }.thenByDescending { it.listenedMs })
            .take(topN),
        topArtists = rankNamed(monthEvents, { it.artist }, topN),
        topAlbums = rankNamed(monthEvents.filter { it.album.isNotBlank() }, { it.album }, topN),
        topGenres = rankNamed(
            monthEvents.filter { !it.genre.isNullOrBlank() },
            { it.genre!! },
            topN,
        ),
        last7Days = last7Days,
        peakHour = peakHour,
        streakDays = streakDays,
    )
}

private fun rankNamed(
    events: List<ListenEvent>,
    nameOf: (ListenEvent) -> String,
    topN: Int,
): List<TopNamed> = events
    .filter { nameOf(it).isNotBlank() }
    .groupBy(nameOf)
    .map { (name, plays) ->
        TopNamed(name = name, plays = plays.size, listenedMs = plays.sumOf { it.listenedMs })
    }
    .sortedWith(compareByDescending<TopNamed> { it.plays }.thenByDescending { it.listenedMs })
    .take(topN)

/**
 * Consecutive days with a play, ending today or yesterday. A gap today
 * does not break the streak when yesterday had a play (the user may
 * simply not have listened yet today).
 */
fun computeStreak(dayStarts: Set<Long>, todayStart: Long): Int {
    var cursor = when {
        todayStart in dayStarts -> todayStart
        todayStart - DAY_MS in dayStarts -> todayStart - DAY_MS
        else -> return 0
    }
    var streak = 0
    while (cursor in dayStarts) {
        streak++
        cursor -= DAY_MS
    }
    return streak
}
