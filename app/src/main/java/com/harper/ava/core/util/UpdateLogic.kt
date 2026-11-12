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

/** Minimum gap between automatic (background) update checks. */
const val UPDATE_CHECK_COOLDOWN_MS: Long = 24L * 60L * 60L * 1000L

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
