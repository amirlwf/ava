package com.harper.ava

import com.harper.ava.data.preferences.ThemeMode
import com.harper.ava.ui.theme.isDarkTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge-to-edge (Harper 2026-11-13): the status-bar and navigation-bar
 * icon styles follow the APP's theme, not the phone's system theme — so
 * the decision "is the app dark?" must be pinned for every [ThemeMode]
 * in both system modes. A Dark/AMOLED in-app theme on a light system
 * (or Light on a dark system) used to leave the bar icons invisible
 * against the app's own header, because `enableEdgeToEdge()` with no
 * args reads the system night mode. Pure JUnit — no Android runtime,
 * no Compose. Register in `scripts/verify.sh` (test-compile list +
 * JUnitCore list) alongside the other logic tests.
 */
class EdgeToEdgeLogicTest {

    @Test
    fun `DARK is dark in both system modes`() {
        assertTrue(isDarkTheme(ThemeMode.DARK, systemDark = true))
        assertTrue(isDarkTheme(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun `AMOLED is dark in both system modes`() {
        assertTrue(isDarkTheme(ThemeMode.AMOLED, systemDark = true))
        assertTrue(isDarkTheme(ThemeMode.AMOLED, systemDark = false))
    }

    @Test
    fun `LIGHT is light in both system modes`() {
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun `SYSTEM follows the system mode`() {
        assertTrue(isDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }
}
