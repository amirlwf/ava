package com.harper.ava

import com.harper.ava.core.util.ListenEvent
import com.harper.ava.core.util.computeInsights
import com.harper.ava.core.util.computeStreak
import com.harper.ava.core.util.formatListeningTime
import com.harper.ava.core.util.segmentListenedMs
import com.harper.ava.core.util.seekSegment
import com.harper.ava.core.util.shouldRecordPlay
import com.harper.ava.core.util.startOfDayMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 0.7.0 Insights: unit tests for the pure aggregation / recording
 * rules. No Android runtime needed — every function under test is
 * plain JVM code (InsightsLogic).
 */
class InsightsLogicTest {

    private fun event(
        songId: Long,
        title: String = "Song $songId",
        artist: String = "Artist",
        album: String = "Album",
        genre: String? = "Pop",
        durationMs: Long = 180_000L,
        playedAtMs: Long,
        listenedMs: Long = 180_000L,
    ) = ListenEvent(songId, title, artist, album, genre, durationMs, playedAtMs, listenedMs)

    /** A fixed "now": 2026-11-12 15:30 local time. */
    private fun fixedNowMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.NOVEMBER, 12, 15, 30, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private val dayMs = 24L * 60L * 60L * 1000L

    // -- shouldRecordPlay -------------------------------------------------

    @Test
    fun `first ever play records`() {
        assertTrue(shouldRecordPlay(null, 0L, 7L, 1_000L))
    }

    @Test
    fun `a different song always records`() {
        assertTrue(shouldRecordPlay(7L, 1_000L, 8L, 1_001L))
    }

    @Test
    fun `same song within 30s does not record again`() {
        assertFalse(shouldRecordPlay(7L, 1_000L, 7L, 1_000L + 29_000L))
    }

    @Test
    fun `same song after 30s records as a replay`() {
        assertTrue(shouldRecordPlay(7L, 1_000L, 7L, 1_000L + 30_000L))
        assertTrue(shouldRecordPlay(7L, 1_000L, 7L, 1_000L + 120_000L))
    }

    @Test
    fun `clock moving backwards never blocks a record`() {
        assertTrue(shouldRecordPlay(7L, 5_000L, 7L, 1_000L))
    }

    // -- segmentListenedMs --------------------------------------------------

    @Test
    fun `segment is the position delta`() {
        assertEquals(60_000L, segmentListenedMs(0L, 60_000L, 180_000L))
    }

    @Test
    fun `seek backwards never yields negative time`() {
        assertEquals(0L, segmentListenedMs(90_000L, 10_000L, 180_000L))
    }

    @Test
    fun `segment never exceeds the song duration`() {
        assertEquals(180_000L, segmentListenedMs(0L, 300_000L, 180_000L))
    }

    @Test
    fun `unknown duration keeps the raw stretch`() {
        assertEquals(300_000L, segmentListenedMs(0L, 300_000L, 0L))
    }

    // -- seekSegment (improve loop, run 5) ------------------------------------

    @Test
    fun `forward scrub credits only the time before the jump`() {
        // Listened 0:00 -> 0:08, scrubbed to 4:00 of a 5:00 song: the
        // 3:52 jump is never credited, the next stretch starts at 4:00.
        val segment = seekSegment(0L, 8_000L, 240_000L, 300_000L)
        assertEquals(8_000L, segment.creditedMs)
        assertEquals(240_000L, segment.newSegmentStartMs)
    }

    @Test
    fun `backward scrub credits up to the old position and restarts there`() {
        val segment = seekSegment(200_000L, 210_000L, 30_000L, 300_000L)
        assertEquals(10_000L, segment.creditedMs)
        assertEquals(30_000L, segment.newSegmentStartMs)
    }

    @Test
    fun `seek to a negative position restarts the segment at zero`() {
        val segment = seekSegment(0L, 5_000L, -1_000L, 300_000L)
        assertEquals(5_000L, segment.creditedMs)
        assertEquals(0L, segment.newSegmentStartMs)
    }

    @Test
    fun `seek before the segment start credits nothing`() {
        val segment = seekSegment(90_000L, 10_000L, 120_000L, 300_000L)
        assertEquals(0L, segment.creditedMs)
        assertEquals(120_000L, segment.newSegmentStartMs)
    }

    // -- formatListeningTime --------------------------------------------------

    @Test
    fun `formatListeningTime handles zero minutes and hours`() {
        assertEquals("0m", formatListeningTime(0L))
        assertEquals("0m", formatListeningTime(-5_000L))
        assertEquals("45m", formatListeningTime(45L * 60_000L))
        assertEquals("2h 05m", formatListeningTime((2L * 60L + 5L) * 60_000L))
        assertEquals("1h 00m", formatListeningTime(60L * 60_000L))
    }

    // -- computeInsights: empty ------------------------------------------------

    @Test
    fun `empty history yields zeros and no peak hour`() {
        val data = computeInsights(emptyList(), fixedNowMs())
        assertEquals(0, data.totalPlays)
        assertEquals(0L, data.totalListenedMs)
        assertEquals(0, data.distinctDays)
        assertEquals(0, data.streakDays)
        assertNull(data.peakHour)
        assertTrue(data.topSongs.isEmpty())
        assertTrue(data.topArtists.isEmpty())
        assertEquals(7, data.last7Days.size)
    }

    // -- computeInsights: windows, tops, streak --------------------------------

    @Test
    fun `today week month windows and top songs`() {
        val now = fixedNowMs()
        val today = startOfDayMs(now) + 10L * 60L * 60L * 1000L // 10:00 today
        val yesterday = today - dayMs
        val tenDaysAgo = today - 10L * dayMs
        val events = listOf(
            event(1L, playedAtMs = today, listenedMs = 180_000L),
            event(1L, playedAtMs = today + 60_000L, listenedMs = 120_000L),
            event(2L, title = "Other", playedAtMs = yesterday, listenedMs = 180_000L),
            // 10 days ago: inside the 30-day top window, outside the week.
            event(3L, title = "Old", playedAtMs = tenDaysAgo, listenedMs = 60_000L),
        )
        val data = computeInsights(events, now)
        assertEquals(4, data.totalPlays)
        assertEquals(540_000L, data.totalListenedMs)
        assertEquals(300_000L, data.listenedTodayMs)
        assertEquals(2, data.playsToday)
        assertEquals(480_000L, data.listenedWeekMs)
        assertEquals(3, data.playsWeek)
        assertEquals(540_000L, data.listenedMonthMs)
        assertEquals(4, data.playsMonth)
        assertEquals(3, data.distinctDays)
        assertEquals(2, data.streakDays) // today + yesterday
        assertEquals(10, data.peakHour)
        // Top song is #1 with 2 plays; last-7-days ends with today (2 plays).
        assertEquals(1L, data.topSongs.first().songId)
        assertEquals(2, data.topSongs.first().plays)
        assertEquals(2, data.last7Days.last().plays)
        assertEquals(300_000L, data.last7Days.last().listenedMs)
        assertEquals(0, data.last7Days.first().plays)
    }

    @Test
    fun `top lists rank by plays then listened time and skip blanks`() {
        val now = fixedNowMs()
        val at = startOfDayMs(now) + 9L * 60L * 60L * 1000L
        val events = listOf(
            event(1L, artist = "A", album = "X", genre = "Pop", playedAtMs = at),
            event(1L, artist = "A", album = "X", genre = "Pop", playedAtMs = at + 1_000L),
            event(2L, title = "B", artist = "B", album = "", genre = "", playedAtMs = at + 2_000L),
            event(2L, title = "B", artist = "B", album = "", genre = "", playedAtMs = at + 3_000L),
            event(2L, title = "B", artist = "B", album = "", genre = null, playedAtMs = at + 4_000L),
        )
        val data = computeInsights(events, now)
        // Song 2 wins on plays (3 vs 2); artist B wins the same way.
        assertEquals(2L, data.topSongs.first().songId)
        assertEquals("B", data.topArtists.first().name)
        // Blank album / genre never appear as a ranked row.
        assertEquals(listOf("X"), data.topAlbums.map { it.name })
        assertEquals(listOf("Pop"), data.topGenres.map { it.name })
    }

    @Test
    fun `streak survives a not-yet-listened today but breaks on a gap`() {
        val now = fixedNowMs()
        val todayStart = startOfDayMs(now)
        val days = setOf(todayStart - dayMs, todayStart - 2L * dayMs)
        assertEquals(2, computeStreak(days, todayStart))
        val gapped = setOf(todayStart, todayStart - 2L * dayMs)
        assertEquals(1, computeStreak(gapped, todayStart))
        assertEquals(0, computeStreak(emptySet(), todayStart))
    }
}
