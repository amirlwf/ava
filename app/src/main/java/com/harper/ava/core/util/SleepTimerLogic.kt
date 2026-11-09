package com.harper.ava.core.util

/**
 * Pure, Android-free sleep-timer logic. Everything here is unit-tested
 * with plain JUnit (no Robolectric, no device) — see SleepTimerLogicTest.
 *
 * The timer is an absolute deadline (`endsAtMs`, epoch millis): the
 * [com.harper.ava.data.repository.PlayerRepository] owns the countdown
 * job, so it keeps running while the app is closed (playback runs in a
 * foreground service) and survives an Activity recreate. The deadline is
 * also persisted in DataStore so a process restart can re-arm it.
 */

/** Ready-made durations offered on the Sleep Timer screen (minutes). */
val SLEEP_TIMER_PRESETS_MINUTES: List<Int> = listOf(5, 10, 15, 30, 45, 60, 90)

const val SLEEP_TIMER_MIN_MINUTES: Int = 1
const val SLEEP_TIMER_MAX_MINUTES: Int = 480

/** The volume fades out over this long before the timer stops playback. */
const val SLEEP_TIMER_FADE_MS: Long = 10_000L

/** Clamps a user-typed custom duration into the supported 1–480 min range. */
fun clampCustomSleepMinutes(minutes: Int): Int =
    minutes.coerceIn(SLEEP_TIMER_MIN_MINUTES, SLEEP_TIMER_MAX_MINUTES)

/** Absolute deadline for a timer of [durationMinutes] started at [nowMs]. */
fun sleepTimerEndsAtMs(nowMs: Long, durationMinutes: Int): Long =
    nowMs + clampCustomSleepMinutes(durationMinutes) * 60_000L

/** Milliseconds left until [endsAtMs]; never negative. */
fun sleepTimerRemainingMs(endsAtMs: Long, nowMs: Long): Long =
    (endsAtMs - nowMs).coerceAtLeast(0L)

/** True when the deadline has passed. */
fun isSleepTimerExpired(endsAtMs: Long, nowMs: Long): Boolean = nowMs >= endsAtMs

/**
 * Recovers the set duration (minutes, rounded up) from a persisted
 * deadline after a process restart — stage 6: the preset chip still
 * highlights when the timer is re-armed from DataStore.
 */
fun restoreSleepTimerMinutes(endsAtMs: Long, nowMs: Long): Int =
    ((endsAtMs - nowMs + 59_999L) / 60_000L).toInt().coerceIn(1, 480)

/** What the UI shows for a running (or absent) sleep timer. */
data class SleepTimerState(
    val active: Boolean = false,
    val endsAtMs: Long = 0L,
    val remainingMs: Long = 0L,
    /** The duration the timer was set for (minutes) — stage 6: the preset
     * chips highlight the active preset; 0 when no timer runs. */
    val durationMinutes: Int = 0,
)
