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
 * Run 27 added the 7 `accent_*` preset labels to the dead set: their
 * only consumer (`accentLabel`) was deleted with them.
 *
 * Run 46 added `online_spike_playing` / `online_spike_paused` to
 * the dead set: they were state labels ("Playing online" / "Paused")
 * used only as content descriptions on the spike screen's play/pause
 * buttons, and the run replaced them with the action labels
 * (`player_play` / `player_pause`, the MiniPlayer / Now Playing
 * rule) — so the state labels have no consumer left.
 *
 * Run 47 added `song_add_to_playlist` to the dead set: 0.4.3
 * removed the SongRow "Add to playlist" menu item (its only
 * consumer) but left the string behind in both locales; the
 * add-to-playlist flow now uses the `playlist_add_songs` family.
 *
 * Public release (0.4.4/0.5.0): the Phase 0 online spike was excised
 * from the public tree (Harper's decision — the spike stays private,
 * NewPipeExtractor is GPL-3.0). All 20 live `online_spike_*` strings
 * were removed from both locales and join the dead set here, so a
 * later re-add is caught.
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
        // The seven accent_* labels were the old Accent-preset names;
        // 0.7.0 replaced Accent presets with full themes, and their
        // only consumer (accentLabel in SettingsScreen) was deleted
        // in improve-loop run 27 — the strings went with it. The
        // Accent enum itself stays: it is still the stored compat
        // value and the notification accent fallback.
        val dead = setOf(
            "notif_action_previous", "notif_action_play", "notif_action_pause",
            "notif_action_next", "notif_action_like", "notif_unknown_artist",
            "settings_appearance_sub", "settings_language_sub",
            "settings_playback_sub", "settings_storage_sub",
            "insights_total_time", "appearance_theme_title",
            "appearance_accent_title",
            "accent_violet", "accent_electric_blue", "accent_emerald",
            "accent_crimson", "accent_amber", "accent_rose",
            "accent_monochrome",
            "online_spike_playing", "online_spike_paused",
            "online_spike_title", "online_spike_sub", "online_spike_banner",
            "online_spike_search_hint", "online_spike_search",
            "online_spike_searching", "online_spike_resolving",
            "online_spike_no_results", "online_spike_buffering",
            "online_spike_stop", "online_spike_now_playing",
            "online_spike_error_network", "online_spike_error_blocked",
            "online_spike_error_parse", "online_spike_error_unknown",
            "online_spike_jamendo_title", "online_spike_jamendo_checking",
            "online_spike_jamendo_reachable",
            "online_spike_jamendo_reachable_no_key_needed",
            "online_spike_jamendo_unreachable",
            "song_add_to_playlist",
        )
        for (file in listOf(enFile, faFile)) {
            val names = stringNames(file).toSet()
            for (name in dead) {
                assertFalse("dead string came back in ${file.path}: $name", name in names)
            }
        }
    }
}
