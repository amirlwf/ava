package com.harper.ava.core.util

/**
 * Pure, Android-free update-check logic. Everything here is unit-tested
 * with plain JUnit (no Robolectric, no device) — see UpdateLogicTest.
 *
 * The network half lives in [com.harper.ava.data.update.UpdateChecker];
 * it calls into these functions so the version math and the (minimal)
 * JSON field extraction stay testable here.
 */

/** GitHub API endpoint for the latest release of this app. */
const val UPDATE_RELEASES_API_URL =
    "https://api.github.com/repos/amirlwf/ava/releases/latest"

/** Browser page the Download button opens (the release the tag belongs to). */
const val UPDATE_RELEASE_PAGE_URL =
    "https://github.com/amirlwf/ava/releases/latest"

/** Harper's website, linked from About and Settings (0.7.4). */
const val HARPER_WEBSITE_URL = "https://amirlwf.ir"

/**
 * Minimum gap between automatic update checks — the anti-hammer
 * guard (0.2-fix, Track 3).
 *
 * Harper's 0.2 report: the app must check every time the user
 * enters it, so the old 24-hour cooldown is gone. The guard that
 * remains is deliberately small (5 minutes): it only stops the
 * check from hammering GitHub when the app re-enters in quick
 * succession (a rotation, returning from a system screen, a fast
 * background/foreground bounce). GitHub's unauthenticated rate
 * limit is 60 requests/hour, so even a guard-free check on every
 * entry would stay under it — 5 minutes is belt and braces, not a
 * throttle on the feature. The check is silent when there is no
 * update and never blocks startup, so checking often costs nothing
 * visible.
 */
const val UPDATE_CHECK_MIN_INTERVAL_MS: Long = 5L * 60L * 1000L

/**
 * True when an automatic check may run now: the minimum interval
 * has passed since the last attempt, or there never was one
 * ([lastCheckMs] <= 0). A clock that went backwards (now < last)
 * is not due — the guard must not be defeatable by a clock skew.
 * Pure, so UpdateLogicTest pins the boundary.
 */
fun isUpdateCheckDue(nowMs: Long, lastCheckMs: Long): Boolean =
    lastCheckMs <= 0L || nowMs - lastCheckMs >= UPDATE_CHECK_MIN_INTERVAL_MS

/**
 * True when [latest] is a newer version than [current].
 *
 * Comparison is numeric per dot-separated segment, so `0.7.10` is newer
 * than `0.7.2` (a plain string compare would get that backwards). A
 * leading `v`/`V` (GitHub tags are `v0.7.2`) is stripped from both sides,
 * missing segments count as 0 (`1.0` == `1.0.0`), and anything after a
 * `-` or `+` (pre-release / build metadata) is ignored for the numeric
 * part — but when the numeric cores are equal, a plain release beats a
 * pre-release (`0.7.2` > `0.7.2-beta`). Non-numeric segments count as 0
 * rather than crashing on a weird tag.
 */
fun isNewerVersion(latest: String, current: String): Boolean {
    val latestCore = normalizeVersion(latest)
    val currentCore = normalizeVersion(current)
    val latestParts = latestCore.core.split(".")
    val currentParts = currentCore.core.split(".")
    val length = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until length) {
        val latestNum = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
        val currentNum = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (latestNum != currentNum) return latestNum > currentNum
    }
    // Numeric cores are equal: a release beats its own pre-release.
    return currentCore.isPreRelease && !latestCore.isPreRelease
}

private data class NormalizedVersion(val core: String, val isPreRelease: Boolean)

/** Strips a `v` prefix, whitespace, and any `-suffix` / `+suffix`. */
private fun normalizeVersion(version: String): NormalizedVersion {
    val trimmed = version.trim().removePrefix("v").removePrefix("V")
    val core = trimmed.substringBefore("-").substringBefore("+").trim()
    return NormalizedVersion(core, trimmed != core)
}

/**
 * Extracts the value of a top-level string field from a small JSON
 * object, e.g. `parseJsonStringField(body, "tag_name")` → `v0.7.2`.
 * Minimal on purpose: the GitHub release payload is large, but the
 * update check only needs two fields, so there is no JSON library to
 * drag into the unit tests. Returns null when the field is absent.
 */
fun parseJsonStringField(json: String, field: String): String? {
    val regex = Regex("\"" + Regex.escape(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
    val raw = regex.find(json)?.groupValues?.getOrNull(1) ?: return null
    return raw
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\/", "/")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
}
