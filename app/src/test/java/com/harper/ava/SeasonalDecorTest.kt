package com.harper.ava

import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.ui.theme.SeasonalDecorKind
import com.harper.ava.ui.theme.argbLuminance
import com.harper.ava.ui.theme.contrastRatio
import com.harper.ava.ui.theme.seasonalDecorKind
import com.harper.ava.ui.theme.spec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.7.2 seasonal decor + Galactic Black theme. Pure JUnit — no Android
 * runtime, no Compose. The integrator registers this file in
 * `scripts/verify.sh` (test-compile list + JUnitCore list); do not edit
 * verify.sh from this round.
 *
 * Covered here:
 * - [seasonalDecorKind]: the toggle + theme → decor mapping (pure logic).
 * - Galaxy spec: deep-space identity, distinct from AMOLED and from the
 *   other eight themes, with a nebula hero gradient. Readability in both
 *   modes is covered by the AppThemeTest loop over all nine themes.
 */
class SeasonalDecorTest {

    @Test
    fun `seasons and galaxy get decor when effects are on`() {
        assertEquals(SeasonalDecorKind.SPRING_LEAVES, seasonalDecorKind(AppTheme.SPRING, true))
        assertEquals(SeasonalDecorKind.SUMMER_SUN, seasonalDecorKind(AppTheme.SUMMER, true))
        assertEquals(SeasonalDecorKind.AUTUMN_LEAVES, seasonalDecorKind(AppTheme.AUTUMN, true))
        assertEquals(SeasonalDecorKind.WINTER_SNOW, seasonalDecorKind(AppTheme.WINTER, true))
        assertEquals(SeasonalDecorKind.GALAXY_STARS, seasonalDecorKind(AppTheme.GALAXY, true))
    }

    @Test
    fun `non-seasonal themes get no decor even when effects are on`() {
        for (theme in listOf(AppTheme.EMERALD, AppTheme.VIOLET, AppTheme.OCEAN, AppTheme.SUNSET)) {
            assertEquals(theme.name, SeasonalDecorKind.NONE, seasonalDecorKind(theme, true))
        }
    }

    @Test
    fun `effects off hides every decor including galaxy`() {
        for (theme in AppTheme.entries) {
            assertEquals(theme.name, SeasonalDecorKind.NONE, seasonalDecorKind(theme, false))
        }
    }

    @Test
    fun `every decor kind is used by exactly one theme`() {
        val kinds = AppTheme.entries.map { seasonalDecorKind(it, true) }.filter { it != SeasonalDecorKind.NONE }
        assertEquals(5, kinds.size)
        assertEquals(5, kinds.toSet().size)
    }

    @Test
    fun `galaxy is deep space black but not pure black`() {
        val s = AppTheme.GALAXY.spec
        assertTrue(argbLuminance(s.darkBackground) < 0.02)
        assertNotEquals(0xFF000000L, s.darkBackground)
        // Darker than every other theme's dark background.
        for (theme in AppTheme.entries) {
            if (theme == AppTheme.GALAXY) continue
            assertTrue(
                theme.name,
                argbLuminance(s.darkBackground) <= argbLuminance(theme.spec.darkBackground),
            )
        }
    }

    @Test
    fun `galaxy is not amoled`() {
        val galaxy = AppTheme.GALAXY.spec
        // AMOLED mode keeps a pure black background; Galaxy's identity is
        // the indigo-tinted space black + nebula gradient, in every mode.
        assertNotEquals(0xFF000000L, galaxy.darkBackground)
        assertNotEquals(0xFF000000L, galaxy.darkSurface)
        assertNotEquals(AppTheme.VIOLET.spec.darkBackground, galaxy.darkBackground)
    }

    @Test
    fun `galaxy hero gradient is a nebula`() {
        val s = AppTheme.GALAXY.spec
        assertEquals(listOf(0xFF7C3AED, 0xFFDB2777, 0xFF1E1B4B), s.heroDark)
        assertEquals(listOf(0xFFA78BFA, 0xFFF9A8D4, 0xFFE0E7FF), s.heroLight)
        // Violet + pink + blue, none of which is the hero of another theme.
        assertNotEquals(AppTheme.VIOLET.spec.heroDark, s.heroDark)
        assertNotEquals(AppTheme.OCEAN.spec.heroDark, s.heroDark)
    }

    @Test
    fun `galaxy light mode stays readable`() {
        val s = AppTheme.GALAXY.spec
        assertTrue(contrastRatio(s.lightOnBackground, s.lightBackground) >= 7.0)
        assertTrue(contrastRatio(s.lightOnSurface, s.lightSurface) >= 7.0)
        assertTrue(contrastRatio(s.onPrimaryLight, s.primaryLight) >= 4.5)
    }

    @Test
    fun `galaxy names are distinct and bilingual`() {
        val s = AppTheme.GALAXY.spec
        assertEquals("مشکی کهکشانی", s.nameFa)
        assertEquals("Galactic Black", s.nameEn)
        assertTrue(s.moodFa.isNotBlank())
        assertTrue(s.moodEn.isNotBlank())
    }
}
