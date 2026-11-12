package com.harper.ava

import com.harper.ava.core.util.isNewerVersion
import com.harper.ava.core.util.parseJsonStringField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update-check logic (0.7.2). Pure JUnit — no Android runtime.
 *
 * NOTE: not yet registered in scripts/verify.sh (the integrator adds
 * the file + the JUnitCore entry); run manually, e.g.:
 *   kotlinc -cp <cp> -d /tmp/update-test-classes \
 *     app/src/main/java/com/harper/ava/core/util/UpdateLogic.kt \
 *     app/src/test/java/com/harper/ava/UpdateLogicTest.kt
 *   java -cp ... org.junit.runner.JUnitCore com.harper.ava.UpdateLogicTest
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
}
