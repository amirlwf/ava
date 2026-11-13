package com.harper.ava

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Improve loop, run 2 — 14 strings sat dead in both locales (leftovers
 * of the removed 0.7.2 custom notification provider, unused Settings
 * subtitles and an unused Insights label). They were removed from
 * `values/strings.xml` and `values-fa/strings.xml`; these tests pin
 * that cleanup and the fa/en parity that makes it safe.
 *
 * Runs from the project root (as `verify.sh` does); the paths below
 * are relative to it.
 */
class StringsParityTest {

    private fun stringNames(file: File): List<String> {
        assertTrue("missing strings file: ${file.path}", file.isFile)
        val names = Regex("""<string name="([^"]+)"""")
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toList()
        return names
    }

    private val enFile get() = File("app/src/main/res/values/strings.xml")
    private val faFile get() = File("app/src/main/res/values-fa/strings.xml")

    @Test
    fun `fa and en define the same string names`() {
        val en = stringNames(enFile).toSet()
        val fa = stringNames(faFile).toSet()
        assertEquals("strings only in en", emptySet<String>(), en - fa)
        assertEquals("strings only in fa", emptySet<String>(), fa - en)
    }

    @Test
    fun `no duplicate string names inside one locale`() {
        for (file in listOf(enFile, faFile)) {
            val names = stringNames(file)
            assertEquals(
                "duplicate names in ${file.path}",
                names.size, names.toSet().size,
            )
        }
    }

    @Test
    fun `removed dead strings stay removed`() {
        // Leftovers of the 0.7.2 custom notification provider (reverted
        // twice, never coming back), unused Settings subtitles and an
        // unused Insights label — removed in improve-loop run 2.
        // NOTE: player_notification_channel_desc was in this set until
        // 0.1 — dead in 0.7.2's leftovers, but 0.1 gives it a real use
        // (the playback channel's description, set in
        // AvaPlaybackService), so it is a live string again.
        val dead = setOf(
            "notif_action_previous", "notif_action_play", "notif_action_pause",
            "notif_action_next", "notif_action_like", "notif_unknown_artist",
            "settings_appearance_sub", "settings_language_sub",
            "settings_playback_sub", "settings_storage_sub",
            "insights_total_time", "appearance_theme_title",
            "appearance_accent_title",
        )
        for (file in listOf(enFile, faFile)) {
            val names = stringNames(file).toSet()
            for (name in dead) {
                assertFalse("dead string came back in ${file.path}: $name", name in names)
            }
        }
    }
}
