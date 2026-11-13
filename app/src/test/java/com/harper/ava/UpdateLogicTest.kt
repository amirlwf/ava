package com.harper.ava

import com.harper.ava.core.util.UPDATE_CHECK_MIN_INTERVAL_MS
import com.harper.ava.core.util.isNewerVersion
import com.harper.ava.core.util.isUpdateCheckDue
import com.harper.ava.core.util.parseJsonStringField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update-check logic (0.7.2). Pure JUnit — no Android runtime.
 * Registered in scripts/verify.sh (test-compile list + JUnitCore list).
 */
class UpdateLogicTest {

    // -- version comparison --------------------------------------------------

    @Test
    fun `v-prefixed tag beats plain current`() {
        assertTrue(isNewerVersion("v0.7.2", "0.7.1"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(isNewerVersion("v0.7.2", "0.7.2"))
        assertFalse(isNewerVersion("0.7.2", "v0.7.2"))
        assertFalse(isNewerVersion("v0.7.2", "v0.7.2"))
    }

    @Test
    fun `older tag is not newer`() {
        assertFalse(isNewerVersion("v0.7.1", "0.7.2"))
        assertFalse(isNewerVersion("0.6.0", "0.7.1"))
    }

    @Test
    fun `unequal segment counts compare numerically, not as strings`() {
        // 0.7.10 > 0.7.2: a plain string compare would say the opposite.
        assertTrue(isNewerVersion("v0.7.10", "0.7.2"))
        assertFalse(isNewerVersion("v0.7.2", "0.7.10"))
        assertTrue(isNewerVersion("v0.10.0", "0.9.9"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertFalse(isNewerVersion("1.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0"))
        assertTrue(isNewerVersion("1.0.1", "1.0"))
    }

    @Test
    fun `release beats its own pre-release`() {
        assertTrue(isNewerVersion("0.7.2", "0.7.2-beta"))
        assertFalse(isNewerVersion("0.7.2-beta", "0.7.2"))
        assertFalse(isNewerVersion("0.7.2-beta", "0.7.2-beta"))
    }

    @Test
    fun `major bump wins`() {
        assertTrue(isNewerVersion("v1.0.0", "0.7.2"))
        assertFalse(isNewerVersion("v0.7.2", "1.0.0"))
    }

    @Test
    fun `weird tags do not crash and are not newer than a real version`() {
        assertFalse(isNewerVersion("", "0.7.1"))
        assertFalse(isNewerVersion("banana", "0.7.1"))
        assertFalse(isNewerVersion("v", "0.7.1"))
    }

    // -- minimal JSON field extraction ----------------------------------------

    @Test
    fun `tag_name is extracted from a release payload`() {
        val json = """{"url":"https://api.github.com/repos/amirlwf/ava/releases/1","tag_name":"v0.7.2","name":"Ava 0.7.2","html_url":"https://github.com/amirlwf/ava/releases/tag/v0.7.2"}"""
        assertEquals("v0.7.2", parseJsonStringField(json, "tag_name"))
        assertEquals(
            "https://github.com/amirlwf/ava/releases/tag/v0.7.2",
            parseJsonStringField(json, "html_url"),
        )
    }

    @Test
    fun `missing field returns null`() {
        assertNull(parseJsonStringField("""{"tag_name":"v0.7.2"}""", "html_url"))
        assertNull(parseJsonStringField("not json at all", "tag_name"))
        assertNull(parseJsonStringField("", "tag_name"))
    }

    @Test
    fun `field with spaces around the colon still parses`() {
        assertEquals("v0.7.2", parseJsonStringField("{ \"tag_name\" : \"v0.7.2\" }", "tag_name"))
    }

    @Test
    fun `version reset 0_1 is not newer than 0_7_6 by plain semver`() {
        // 0.1 replaces the 0.7.x line by Harper's choice — the version
        // number went backwards on purpose. The comparator stays plain
        // semver (no special-casing), so a 0.7.6 install will NOT be
        // offered 0.1 by the in-app updater: Harper installs 0.1 from
        // the release link, and later versions compare normally
        // against 0.1 (0.2 > 0.1). This test pins that honest behaviour
        // so nobody "fixes" it into a hidden special case later.
        assertFalse(isNewerVersion("v0.1", "0.7.6"))
        assertTrue(isNewerVersion("v0.2", "0.1"))
        assertFalse(isNewerVersion("v0.1", "0.1"))
    }

    // -- every-entry check guard (0.2-fix, Track 3) ----------------------
    // The old 24-hour cooldown is gone: the app checks on every
    // entry. What remains is a 5-minute anti-hammer guard against
    // rapid re-entries (rotation, returning from a system screen).
    // The boundary is pinned here; the manager calls this function.

    @Test
    fun `check guard is five minutes`() {
        assertEquals(5L * 60L * 1000L, UPDATE_CHECK_MIN_INTERVAL_MS)
    }

    @Test
    fun `first ever check is due`() {
        assertTrue(isUpdateCheckDue(nowMs = 1_000_000L, lastCheckMs = 0L))
        assertTrue(isUpdateCheckDue(nowMs = 1_000_000L, lastCheckMs = -1L))
    }

    @Test
    fun `check just made is not due again`() {
        assertFalse(isUpdateCheckDue(nowMs = 1_000_000L, lastCheckMs = 1_000_000L))
        assertFalse(isUpdateCheckDue(nowMs = 1_000_000L, lastCheckMs = 999_999L))
    }

    @Test
    fun `check is due again after the five minute guard`() {
        val last = 1_000_000L
        assertFalse(isUpdateCheckDue(nowMs = last + 4L * 60L * 1000L, lastCheckMs = last))
        assertFalse(isUpdateCheckDue(nowMs = last + 5L * 60L * 1000L - 1L, lastCheckMs = last))
        assertTrue(isUpdateCheckDue(nowMs = last + 5L * 60L * 1000L, lastCheckMs = last))
        assertTrue(isUpdateCheckDue(nowMs = last + 60L * 60L * 1000L, lastCheckMs = last))
    }

    @Test
    fun `clock going backwards is not due`() {
        assertFalse(isUpdateCheckDue(nowMs = 500_000L, lastCheckMs = 1_000_000L))
    }
}
