package com.harper.ava

import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.playback.notificationAccentArgb
import com.harper.ava.ui.theme.appThemeSpec
import com.harper.ava.ui.theme.argbLuminance
import com.harper.ava.ui.theme.contrastRatio
import com.harper.ava.ui.theme.glassAuroraAlpha
import com.harper.ava.ui.theme.glassAuroraBlobAlpha
import com.harper.ava.ui.theme.resolveAppTheme
import com.harper.ava.ui.theme.spec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.7.0 themes (+ 0.7.2 Galaxy): every theme must be a complete, distinct, readable
 * design system in both dark and light mode. Pure JUnit — no Android
 * runtime, no Compose. Register in `scripts/verify.sh` (test-compile
 * list + JUnitCore list) alongside the other logic tests.
 *
 * Thresholds (WCAG contrast ratios on the real ARGB values):
 * - body text vs background >= 7.0, secondary text >= 4.5
 * - primary vs background >= 3.0 (large text / UI components)
 * - on-primary label vs primary >= 4.0 (the shipped VIOLET white-on-
 *   violet is 4.35, so 4.5 would fail the one theme that must stay
 *   pixel-identical to 0.6.0; every other theme is >= 6.3)
 * - secondary / tertiary vs background >= 3.0
 */
class AppThemeTest {

    @Test
    fun `all ten themes exist`() {
        assertEquals(10, AppTheme.entries.size)
        assertEquals(
            setOf("SPRING", "SUMMER", "AUTUMN", "WINTER", "EMERALD", "VIOLET", "OCEAN", "SUNSET", "GALAXY", "IPHONE"),
            AppTheme.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `each theme has fa and en names and moods`() {
        for (theme in AppTheme.entries) {
            val spec = theme.spec
            assertTrue(theme.name, spec.nameFa.isNotBlank())
            assertTrue(theme.name, spec.nameEn.isNotBlank())
            assertTrue(theme.name, spec.moodFa.isNotBlank())
            assertTrue(theme.name, spec.moodEn.isNotBlank())
        }
        assertEquals(10, AppTheme.entries.map { it.spec.nameFa }.toSet().size)
        assertEquals(10, AppTheme.entries.map { it.spec.nameEn }.toSet().size)
    }

    @Test
    fun `dark primaries are distinct per theme`() {
        val primaries = AppTheme.entries.map { it.spec.primaryDark }
        assertEquals(10, primaries.toSet().size)
    }

    @Test
    fun `dark mode text is readable on every theme`() {
        for (theme in AppTheme.entries) {
            val s = theme.spec
            assertTrue("${theme.name} body", contrastRatio(s.darkOnBackground, s.darkBackground) >= 7.0)
            assertTrue("${theme.name} surface text", contrastRatio(s.darkOnSurface, s.darkSurface) >= 7.0)
            assertTrue("${theme.name} secondary text", contrastRatio(s.darkOnSurfaceVariant, s.darkSurfaceVariant) >= 4.5)
            assertTrue("${theme.name} primary vs bg", contrastRatio(s.primaryDark, s.darkBackground) >= 3.0)
            assertTrue("${theme.name} on-primary", contrastRatio(s.onPrimaryDark, s.primaryDark) >= 4.0)
            assertTrue("${theme.name} secondary", contrastRatio(s.secondaryDark, s.darkBackground) >= 3.0)
            assertTrue("${theme.name} tertiary", contrastRatio(s.tertiaryDark, s.darkBackground) >= 3.0)
            assertTrue("${theme.name} container text", contrastRatio(s.onPrimaryContainerDark, s.primaryContainerDark) >= 4.5)
        }
    }

    @Test
    fun `light mode text is readable on every theme`() {
        for (theme in AppTheme.entries) {
            val s = theme.spec
            assertTrue("${theme.name} body", contrastRatio(s.lightOnBackground, s.lightBackground) >= 7.0)
            assertTrue("${theme.name} surface text", contrastRatio(s.lightOnSurface, s.lightSurface) >= 7.0)
            assertTrue("${theme.name} secondary text", contrastRatio(s.lightOnSurfaceVariant, s.lightSurfaceVariant) >= 4.5)
            assertTrue("${theme.name} primary vs bg", contrastRatio(s.primaryLight, s.lightBackground) >= 3.0)
            assertTrue("${theme.name} on-primary", contrastRatio(s.onPrimaryLight, s.primaryLight) >= 4.5)
            assertTrue("${theme.name} secondary", contrastRatio(s.secondaryLight, s.lightBackground) >= 3.0)
            assertTrue("${theme.name} tertiary", contrastRatio(s.tertiaryLight, s.lightBackground) >= 3.0)
            assertTrue("${theme.name} container text", contrastRatio(s.onPrimaryContainerLight, s.primaryContainerLight) >= 4.5)
        }
    }

    @Test
    fun `amoled surfaces stay effectively black`() {
        for (theme in AppTheme.entries) {
            val s = theme.spec
            assertTrue("${theme.name} amoled surface", argbLuminance(s.amoledSurface) < 0.01)
            assertTrue("${theme.name} amoled variant", argbLuminance(s.amoledSurfaceVariant) < 0.01)
            // Surfaces are tinted, not pure black — each theme keeps a whisper of hue.
            assertNotEquals("${theme.name} surface", 0xFF000000L, s.amoledSurface)
        }
    }

    @Test
    fun `hero gradients have three distinct stops per mode`() {
        for (theme in AppTheme.entries) {
            val s = theme.spec
            assertEquals(3, s.heroDark.size)
            assertEquals(3, s.heroLight.size)
            assertEquals(3, s.heroDark.toSet().size)
            assertEquals(3, s.heroLight.toSet().size)
        }
    }

    @Test
    fun `radii are ordered small to extra-large and in range`() {
        for (theme in AppTheme.entries) {
            val s = theme.spec
            assertTrue("${theme.name} small", s.radiusSmallDp in 4..40)
            assertTrue("${theme.name} order", s.radiusSmallDp <= s.radiusMediumDp)
            assertTrue("${theme.name} order", s.radiusMediumDp <= s.radiusLargeDp)
            assertTrue("${theme.name} order", s.radiusLargeDp <= s.radiusExtraLargeDp)
            assertTrue("${theme.name} xl", s.radiusExtraLargeDp in 4..40)
        }
        // The four seasons / moods really differ in shape language:
        // Winter is the crispest, Summer and Sunset the roundest.
        assertTrue(appThemeSpec(AppTheme.WINTER).radiusExtraLargeDp < appThemeSpec(AppTheme.SUMMER).radiusExtraLargeDp)
        assertEquals(appThemeSpec(AppTheme.SUMMER).radiusExtraLargeDp, appThemeSpec(AppTheme.SUNSET).radiusExtraLargeDp)
    }

    @Test
    fun `violet keeps the exact 0_6_0 surfaces and radii`() {
        val s = AppTheme.VIOLET.spec
        assertEquals(0xFF12121AL, s.darkBackground)
        assertEquals(0xFF1C1C28L, s.darkSurface)
        assertEquals(0xFF282838L, s.darkSurfaceVariant)
        assertEquals(0xFFFAFAFCL, s.lightBackground)
        assertEquals(0xFF0A0A0AL, s.amoledSurface)
        assertEquals(0xFF161616L, s.amoledSurfaceVariant)
        assertEquals(12, s.radiusSmallDp)
        assertEquals(16, s.radiusMediumDp)
        assertEquals(20, s.radiusLargeDp)
        assertEquals(28, s.radiusExtraLargeDp)
    }

    @Test
    fun `resolveAppTheme falls back to violet on unknown input`() {
        assertEquals(AppTheme.SPRING, resolveAppTheme("SPRING"))
        assertEquals(AppTheme.SUNSET, resolveAppTheme("SUNSET"))
        assertEquals(AppTheme.VIOLET, resolveAppTheme("NOPE"))
        assertEquals(AppTheme.VIOLET, resolveAppTheme(null))
        assertEquals(AppTheme.VIOLET, resolveAppTheme(""))
    }

    @Test
    fun `notification accent follows the theme primary`() {
        for (theme in AppTheme.entries) {
            assertEquals(
                theme.name,
                theme.spec.primaryDark.toInt(),
                notificationAccentArgb(theme, null),
            )
        }
        // A custom colour still wins over the theme.
        val custom = notificationAccentArgb(AppTheme.OCEAN, 0xFFFF0000.toInt())
        assertNotEquals(AppTheme.OCEAN.spec.primaryDark.toInt(), custom)
    }

    @Test
    fun `contrast helper matches known values`() {
        assertEquals(21.0, contrastRatio(0xFFFFFFFF, 0xFF000000), 0.01)
        assertEquals(1.0, contrastRatio(0xFF888888, 0xFF888888), 0.01)
        assertEquals(0.0, argbLuminance(0xFF000000), 0.001)
        assertEquals(1.0, argbLuminance(0xFFFFFFFF), 0.001)
    }

    @Test
    fun `exactly one glass theme, and it is iPhone`() {
        // 0.1: glass is a deliberate design language, not a decoration
        // sprinkled across themes — only IPHONE renders frosted glass.
        val glass = AppTheme.entries.filter { it.spec.isGlass }
        assertEquals(listOf(AppTheme.IPHONE), glass)
        // The glass theme is still a full theme: its contrast, radii
        // and hero checks above already cover it.
        assertEquals("آیفون", AppTheme.IPHONE.spec.nameFa)
        assertEquals("iPhone", AppTheme.IPHONE.spec.nameEn)
    }

    @Test
    fun `iphone backgrounds are unmistakable`() {
        // 0.1-fix1 (Track C): Harper felt no change when selecting
        // IPHONE — partly because its backgrounds were not distinct
        // enough to notice. Pin them: pure-black dark (Apple's dark
        // mode) and #F2F2F7 light (Apple's grouped background), and no
        // other theme shares either value.
        val iphone = AppTheme.IPHONE.spec
        assertEquals(0xFF000000L, iphone.darkBackground)
        assertEquals(0xFFF2F2F7L, iphone.lightBackground)
        for (theme in AppTheme.entries) {
            if (theme == AppTheme.IPHONE) continue
            assertNotEquals(theme.name, 0xFF000000L, theme.spec.darkBackground)
            assertNotEquals(theme.name, 0xFFF2F2F7L, theme.spec.lightBackground)
        }
    }

    @Test
    fun `glass aurora strength is faint and mode-aware`() {
        // 0.1-fix1 (Track C): the aurora behind the glass panels must
        // be visible (dark 0.14, light 0.10) but never loud — every
        // blob stays in 0.05..0.16, and AMOLED is the quietest (0.065).
        assertEquals(0.14f, glassAuroraAlpha(0.0, false))
        assertEquals(0.10f, glassAuroraAlpha(0.9, false))
        assertEquals(0.065f, glassAuroraAlpha(0.0, true))
        for (amoled in listOf(false, true)) {
            for (luminance in listOf(0.0, 0.5, 1.0)) {
                val base = glassAuroraAlpha(luminance, amoled)
                for (blob in 0 until 3) {
                    val alpha = glassAuroraBlobAlpha(base, blob)
                    assertTrue("alpha $alpha", alpha in 0.05f..0.16f)
                }
            }
        }
        // The blue field is strongest, violet the quietest: one sky
        // with a direction, not three equal spots.
        val base = glassAuroraAlpha(0.0, false)
        assertEquals(base, glassAuroraBlobAlpha(base, 0))
        assertTrue(glassAuroraBlobAlpha(base, 0) > glassAuroraBlobAlpha(base, 1))
        assertTrue(glassAuroraBlobAlpha(base, 1) > glassAuroraBlobAlpha(base, 2))
    }
}
