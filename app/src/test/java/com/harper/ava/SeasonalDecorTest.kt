package com.harper.ava

import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.ui.theme.GALAXY_METEOR_COUNT
import com.harper.ava.ui.theme.GALAXY_METEOR_MAX_ALPHA
import com.harper.ava.ui.theme.GALAXY_PLANET_COUNT
import com.harper.ava.ui.theme.SeasonalDecorKind
import com.harper.ava.ui.theme.argbLuminance
import com.harper.ava.ui.theme.contrastRatio
import com.harper.ava.ui.theme.meteorState
import com.harper.ava.ui.theme.planetAngle
import com.harper.ava.ui.theme.planetOrbitRadius
import com.harper.ava.ui.theme.planetPosition
import com.harper.ava.ui.theme.seasonalDecorKind
import com.harper.ava.ui.theme.spec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

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

    // --- 0.7.3 galaxy: shooting stars + solar system (pure helpers) ---

    @Test
    fun `three meteors each appear for only part of the loop`() {
        assertEquals(3, GALAXY_METEOR_COUNT)
        for (i in 0 until GALAXY_METEOR_COUNT) {
            var visible = 0
            var total = 0
            var p = 0f
            while (p < 1f) {
                total++
                if (meteorState(i, p) != null) visible++
                p += 0.005f
            }
            // Each meteor streaks for ~10-15% of the loop (~4-5 s of 36 s),
            // then waits: visible sometimes, invisible most of the time.
            assertTrue("meteor $i never appears", visible > 0)
            assertTrue("meteor $i never disappears", visible < total)
            val fraction = visible.toFloat() / total
            assertTrue("meteor $i visible fraction $fraction", fraction in 0.05f..0.25f)
        }
    }

    @Test
    fun `meteor state is deterministic bounded and faint`() {
        for (i in 0 until GALAXY_METEOR_COUNT) {
            var p = 0f
            while (p < 1f) {
                val a = meteorState(i, p)
                val b = meteorState(i, p)
                assertEquals(a, b)
                if (a != null) {
                    assertTrue("alpha ${a.alpha}", a.alpha in 0f..GALAXY_METEOR_MAX_ALPHA)
                    assertTrue("headX ${a.headX}", a.headX in -0.3f..1.3f)
                    assertTrue("headY ${a.headY}", a.headY in -0.3f..1.3f)
                    // Tail points up-right, opposite the down-left travel.
                    assertTrue(a.tailX > a.headX)
                    assertTrue(a.tailY < a.headY)
                }
                p += 0.01f
            }
        }
    }

    @Test
    fun `meteor travels down-left while visible`() {
        for (i in 0 until GALAXY_METEOR_COUNT) {
            // Find the first visible moment, then compare a little later
            // (still inside the same ~4-5 s streak).
            var start: Float? = null
            var p = 0f
            while (p < 1f && start == null) {
                if (meteorState(i, p) != null) start = p
                p += 0.005f
            }
            val first = meteorState(i, start!!)!!
            val later = meteorState(i, start + 0.02f)
            assertNotNull("meteor $i should still be visible 0.02 later", later)
            assertTrue(later!!.headX < first.headX)
            assertTrue(later.headY > first.headY)
        }
    }

    @Test
    fun `meteor fades in and out instead of popping`() {
        for (i in 0 until GALAXY_METEOR_COUNT) {
            var firstAlpha: Float? = null
            var peak = 0f
            var lastAlpha: Float? = null
            var p = 0f
            while (p < 1f) {
                val s = meteorState(i, p)
                if (s != null) {
                    if (firstAlpha == null) firstAlpha = s.alpha
                    lastAlpha = s.alpha
                    if (s.alpha > peak) peak = s.alpha
                }
                p += 0.002f
            }
            assertNotNull(firstAlpha)
            assertNotNull(lastAlpha)
            // Near-zero at both ends of the streak, bright in the middle.
            assertTrue("first alpha $firstAlpha", firstAlpha!! < 0.05f)
            assertTrue("last alpha $lastAlpha", lastAlpha!! < 0.05f)
            assertTrue("peak $peak", peak > 0.15f)
        }
    }

    @Test
    fun `planet orbits grow outward and stay elliptical`() {
        assertEquals(3, GALAXY_PLANET_COUNT)
        var prevRx = 0f
        for (i in 0 until GALAXY_PLANET_COUNT) {
            val (rx, ry) = planetOrbitRadius(i)
            assertTrue("rx $rx", rx in 0.05f..0.4f)
            assertTrue("ry $ry < rx $rx", ry < rx)
            assertTrue("orbit $i not wider than orbit ${i - 1}", rx > prevRx)
            prevRx = rx
        }
    }

    @Test
    fun `planet stays on its ellipse at every progress`() {
        val cx = 0.70f
        val cy = 0.14f
        for (i in 0 until GALAXY_PLANET_COUNT) {
            val (rx, ry) = planetOrbitRadius(i)
            var p = 0f
            while (p < 1f) {
                val pos = planetPosition(i, p, cx, cy, rx, ry)
                val onEllipse = ((pos.x - cx) / rx).let { it * it } +
                    ((pos.y - cy) / ry).let { it * it }
                assertEquals("planet $i at $p", 1.0, onEllipse.toDouble(), 1e-4)
                // The whole system stays inside the canvas.
                assertTrue(pos.x in 0f..1f)
                assertTrue(pos.y in 0f..1f)
                p += 0.05f
            }
        }
    }

    @Test
    fun `planet angle is deterministic and wraps in zero to two pi`() {
        for (i in 0 until GALAXY_PLANET_COUNT) {
            var p = 0f
            while (p < 1f) {
                val angle = planetAngle(i, p)
                assertTrue("angle $angle", angle in 0f..(2f * PI.toFloat()))
                assertEquals(angle, planetAngle(i, p))
                p += 0.05f
            }
        }
    }

    @Test
    fun `inner planets orbit faster than outer ones`() {
        // Angle gained over a quarter loop, modulo 2pi: strictly decreasing.
        fun gained(index: Int): Float {
            val delta = planetAngle(index, 0.25f) - planetAngle(index, 0f)
            return ((delta % (2f * PI.toFloat())) + 2f * PI.toFloat()) % (2f * PI.toFloat())
        }
        val g0 = gained(0)
        val g1 = gained(1)
        val g2 = gained(2)
        assertTrue("g0 $g0 > g1 $g1", g0 > g1)
        assertTrue("g1 $g1 > g2 $g2", g1 > g2)
        assertTrue(abs(g0 - 0.15f * 2f * PI.toFloat()) < 0.01f)
    }

    @Test
    fun `planet position is deterministic`() {
        val (rx, ry) = planetOrbitRadius(1)
        assertEquals(
            planetPosition(1, 0.37f, 0.70f, 0.14f, rx, ry),
            planetPosition(1, 0.37f, 0.70f, 0.14f, rx, ry),
        )
        assertNotEquals(
            planetPosition(1, 0.37f, 0.70f, 0.14f, rx, ry),
            planetPosition(1, 0.62f, 0.70f, 0.14f, rx, ry),
        )
    }
}
