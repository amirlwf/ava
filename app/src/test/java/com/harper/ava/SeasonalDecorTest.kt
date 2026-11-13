package com.harper.ava

import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.ui.theme.GALAXY_BIG_METEOR_MAX_ALPHA
import com.harper.ava.ui.theme.GALAXY_BRIGHT_STAR_COUNT
import com.harper.ava.ui.theme.GALAXY_FAR_STAR_COUNT
import com.harper.ava.ui.theme.GALAXY_METEOR_COUNT
import com.harper.ava.ui.theme.GALAXY_METEOR_MAX_ALPHA
import com.harper.ava.ui.theme.GALAXY_MID_STAR_COUNT
import com.harper.ava.ui.theme.GALAXY_MILKY_WAY_DUST_COUNT
import com.harper.ava.ui.theme.GALAXY_NEBULA_BLOBS
import com.harper.ava.ui.theme.METEOR_TAIL_ALPHAS
import com.harper.ava.ui.theme.METEOR_TAIL_FRACTIONS
import com.harper.ava.ui.theme.METEOR_TAIL_WIDTHS_DP
import com.harper.ava.ui.theme.MILKY_WAY_GLOW_STOPS
import com.harper.ava.ui.theme.SOLAR_PLANETS
import com.harper.ava.ui.theme.SOLAR_SUN_X
import com.harper.ava.ui.theme.SOLAR_SUN_Y
import com.harper.ava.ui.theme.SeasonalDecorKind
import com.harper.ava.ui.theme.argbLuminance
import com.harper.ava.ui.theme.contrastRatio
import com.harper.ava.ui.theme.galaxyParallaxDrift
import com.harper.ava.ui.theme.galaxyStarAlpha
import com.harper.ava.ui.theme.galaxyStarPosition
import com.harper.ava.ui.theme.galaxyStarRadiusDp
import com.harper.ava.ui.theme.meteorMaxAlpha
import com.harper.ava.ui.theme.meteorState
import com.harper.ava.ui.theme.meteorTailLength
import com.harper.ava.ui.theme.meteorTailWidthFactor
import com.harper.ava.ui.theme.milkyWayBandCenterY
import com.harper.ava.ui.theme.milkyWayDensity
import com.harper.ava.ui.theme.milkyWayDustAlpha
import com.harper.ava.ui.theme.milkyWayDustPosition
import com.harper.ava.ui.theme.seasonalDecorKind
import com.harper.ava.ui.theme.solarMoonAngle
import com.harper.ava.ui.theme.solarMoonOffset
import com.harper.ava.ui.theme.solarPlanetAngle
import com.harper.ava.ui.theme.solarPlanetOffset
import com.harper.ava.ui.theme.spec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

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
 *   modes is covered by the AppThemeTest loop over all ten themes.
 * - 0.1-fix1: the calm redesign — one rare meteor, sparse stars, four
 *   faint nebula clouds; the solar system, constellations, planets and
 *   moons are gone (Harper: "not too crowded, just beautiful and real").
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
        // Darker than every other theme's dark background — except
        // IPHONE (0.1), whose pure-black background is Apple's dark
        // mode by design, not a space aesthetic: it is the one theme
        // allowed to be blacker than Galaxy.
        for (theme in AppTheme.entries) {
            if (theme == AppTheme.GALAXY || theme == AppTheme.IPHONE) continue
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

    // --- 0.7.3 galaxy: shooting stars (pure helpers) ---
    // 0.3-feedback: THREE meteors, staggered across the loop — the
    // first one BIG — so the sky is no longer empty for minutes at a
    // time, but a meteor is still an event, not a shower.

    @Test
    fun `three meteors appear for only part of the loop`() {
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
            // Each meteor streaks for ~10-15% of the loop (~4-5 s
            // of 36 s), then waits: visible sometimes, invisible most of
            // the time.
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
                    // The BIG meteor (index 0) is allowed 0.55; the
                    // ordinary ones cap at 0.30.
                    assertTrue("alpha ${a.alpha}", a.alpha in 0f..meteorMaxAlpha(i))
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
            // Relative to each meteor's own peak (the BIG meteor's 0.55
            // peak makes its first sampled alpha ~0.057 in absolute
            // terms — still only ~10% of its peak, so it fades in
            // rather than popping).
            assertTrue("first alpha $firstAlpha", firstAlpha!! < 0.2f * meteorMaxAlpha(i))
            assertTrue("last alpha $lastAlpha", lastAlpha!! < 0.2f * meteorMaxAlpha(i))
            assertTrue("peak $peak", peak > 0.15f)
        }
    }

    // --- 0.7.4 galaxy deep redesign: starfield layers, Milky Way, nebula ---
    // 0.1-fix1: fewer, sparser, fainter — a believable night sky.

    @Test
    fun `starfield has three depth layers with distinct sizes`() {
        // 0.3-feedback: denser and bigger — 64 / 42 / 14 (was 24 / 18 / 6).
        assertEquals(64, GALAXY_FAR_STAR_COUNT)
        assertEquals(42, GALAXY_MID_STAR_COUNT)
        assertEquals(14, GALAXY_BRIGHT_STAR_COUNT)
        for (i in 0 until GALAXY_FAR_STAR_COUNT) {
            val r = galaxyStarRadiusDp(i, 0)
            assertTrue("far radius $r", r in 0.8f..1.9f)
            assertEquals(r, galaxyStarRadiusDp(i, 0))
        }
        for (i in 0 until GALAXY_MID_STAR_COUNT) {
            assertTrue(galaxyStarRadiusDp(i, 1) in 1.8f..5.0f)
        }
        for (i in 0 until GALAXY_BRIGHT_STAR_COUNT) {
            assertTrue(galaxyStarRadiusDp(i, 2) in 3.5f..7.0f)
        }
        // Far stars are always smaller than bright stars: depth reads.
        assertTrue(0.8f + 1.1f < 3.5f)
    }

    @Test
    fun `star alpha twinkles inside its layer band and changes over the loop`() {
        // 0.3-feedback: brighter bands — far 0.08–0.28, mid 0.10–0.40,
        // bright 0.15–0.55 (was 0.06–0.20 / 0.08–0.32 / 0.10–0.30).
        val bands = listOf(0 to (0.08f to 0.28f), 1 to (0.10f to 0.40f), 2 to (0.15f to 0.55f))
        for ((layer, band) in bands) {
            var min = 1f
            var max = 0f
            var p = 0f
            while (p < 1f) {
                val a = galaxyStarAlpha(3, layer, p)
                assertTrue("layer $layer alpha $a", a in band.first..band.second)
                assertEquals(a, galaxyStarAlpha(3, layer, p))
                if (a < min) min = a
                if (a > max) max = a
                p += 0.02f
            }
            assertTrue("layer $layer never twinkles: $min..$max", max - min > 0.05f)
        }
    }

    @Test
    fun `parallax drift is a smooth sway deeper for nearer layers`() {
        // Zero at both ends of the loop: no jump on restart.
        for (layer in 0..2) {
            assertEquals(0.0, galaxyParallaxDrift(layer, 0f).toDouble(), 1e-6)
            assertTrue(abs(galaxyParallaxDrift(layer, 1f)) < 1e-5f)
        }
        // At a quarter loop the sine is 1: drift equals the amplitude.
        assertEquals(0.006, galaxyParallaxDrift(0, 0.25f).toDouble(), 1e-5)
        assertEquals(0.013, galaxyParallaxDrift(1, 0.25f).toDouble(), 1e-5)
        assertEquals(0.020, galaxyParallaxDrift(2, 0.25f).toDouble(), 1e-5)
        // Bounded everywhere, and nearer layers always drift further.
        var p = 0f
        while (p < 1f) {
            val d0 = abs(galaxyParallaxDrift(0, p))
            val d1 = abs(galaxyParallaxDrift(1, p))
            val d2 = abs(galaxyParallaxDrift(2, p))
            assertTrue(d2 <= 0.020001f)
            assertTrue(d0 <= d1)
            assertTrue(d1 <= d2)
            p += 0.01f
        }
    }

    @Test
    fun `star positions stay on screen and move with parallax`() {
        for (layer in 0..2) {
            for (i in 0 until 10) {
                for (p in listOf(0f, 0.25f, 0.5f)) {
                    val (x, y) = galaxyStarPosition(i, layer, p)
                    assertTrue("layer $layer star $i x $x", x in 0f..1f)
                    assertTrue("layer $layer star $i y $y", y in 0f..1f)
                }
            }
        }
        // The near layer visibly sways between loop start and quarter loop.
        val (x0, _) = galaxyStarPosition(0, 2, 0f)
        val (x1, _) = galaxyStarPosition(0, 2, 0.25f)
        assertTrue(abs(x1 - x0) > 0.01f)
    }

    @Test
    fun `milky way band runs from bottom-left to top-right`() {
        assertEquals(0.78, milkyWayBandCenterY(0f).toDouble(), 1e-6)
        assertEquals(0.18, milkyWayBandCenterY(1f).toDouble(), 1e-6)
        assertEquals(0.48, milkyWayBandCenterY(0.5f).toDouble(), 1e-6)
        assertTrue(milkyWayBandCenterY(0.2f) > milkyWayBandCenterY(0.8f))
    }

    @Test
    fun `milky way density peaks on the band and fades away`() {
        assertEquals(1.0, milkyWayDensity(0.5f, 0.48f).toDouble(), 1e-6)
        assertEquals(0.0, milkyWayDensity(0.5f, 0.48f + 0.17f).toDouble(), 1e-6)
        assertEquals(0.0, milkyWayDensity(0.5f, 0.48f + 0.5f).toDouble(), 1e-6)
        assertEquals(0.25, milkyWayDensity(0.5f, 0.48f + 0.085f).toDouble(), 0.01)
        var x = 0f
        while (x <= 1f) {
            var y = 0f
            while (y <= 1f) {
                val d = milkyWayDensity(x, y)
                assertTrue("density $d at $x,$y", d in 0f..1f)
                y += 0.1f
            }
            x += 0.1f
        }
    }

    @Test
    fun `milky way dust sits inside the band`() {
        assertEquals(36, GALAXY_MILKY_WAY_DUST_COUNT)
        for (i in 0 until GALAXY_MILKY_WAY_DUST_COUNT) {
            val (x, y) = milkyWayDustPosition(i)
            assertTrue("dust $i x $x", x in 0f..1f)
            assertTrue("dust $i y $y", y in 0f..1f)
            assertEquals(milkyWayDustPosition(i), milkyWayDustPosition(i))
            assertTrue("dust $i density ${milkyWayDensity(x, y)}", milkyWayDensity(x, y) > 0.2f)
        }
    }

    @Test
    fun `dust alpha is faint deterministic and varies`() {
        var min = 1f
        var max = 0f
        var p = 0f
        while (p < 1f) {
            val a = milkyWayDustAlpha(0, p)
            assertTrue("dust alpha $a", a in 0.04f..0.16f)
            assertEquals(a, milkyWayDustAlpha(0, p))
            if (a < min) min = a
            if (a > max) max = a
            p += 0.02f
        }
        assertTrue("dust never twinkles: $min..$max", max - min > 0.02f)
    }

    @Test
    fun `nebula has four faint multi-colour clouds`() {
        assertEquals(4, GALAXY_NEBULA_BLOBS.size)
        val colors = mutableSetOf<Int>()
        for (blob in GALAXY_NEBULA_BLOBS) {
            assertTrue("alpha ${blob.alpha}", blob.alpha in 0.03f..0.08f)
            assertTrue("cx ${blob.centerX}", blob.centerX in 0f..1f)
            assertTrue("cy ${blob.centerY}", blob.centerY in 0f..1f)
            assertTrue("radius ${blob.radius}", blob.radius in 0.15f..0.45f)
            assertTrue("color ${blob.colorIndex}", blob.colorIndex in 0..3)
            colors += blob.colorIndex
        }
        assertTrue("only ${colors.size} nebula colours", colors.size >= 3)
    }

    @Test
    fun `the big meteor peaks at the big max alpha`() {
        // 0.3-feedback: index 0 is the BIG meteor — 0.55, nearly twice
        // the ordinary 0.30 — with a 1.8x tail and width.
        assertEquals(GALAXY_BIG_METEOR_MAX_ALPHA, meteorMaxAlpha(0))
        assertEquals(GALAXY_METEOR_MAX_ALPHA, meteorMaxAlpha(1))
        assertEquals(GALAXY_METEOR_MAX_ALPHA, meteorMaxAlpha(2))
        assertEquals(1.8f, meteorTailWidthFactor(0))
        assertEquals(1.0f, meteorTailWidthFactor(1))
        var peak = 0f
        var p = 0f
        while (p < 1f) {
            val s = meteorState(0, p)
            if (s != null && s.alpha > peak) peak = s.alpha
            p += 0.002f
        }
        assertTrue("big meteor peak $peak", peak in 0.45f..0.55f)
        var ordinaryPeak = 0f
        p = 0f
        while (p < 1f) {
            val s = meteorState(1, p)
            if (s != null && s.alpha > ordinaryPeak) ordinaryPeak = s.alpha
            p += 0.002f
        }
        assertTrue("ordinary meteor peak $ordinaryPeak", ordinaryPeak in 0.25f..0.30f)
    }

    @Test
    fun `meteor tail is long and diagonal`() {
        // The BIG meteor's tail is 1.8x the ordinary (0.13..0.22) range.
        assertTrue(meteorTailLength(0) in 0.23f..0.40f)
        assertTrue(meteorTailLength(1) in 0.13f..0.22f)
        assertEquals(meteorTailLength(0), meteorTailLength(0))
        // Find a visible moment and measure the head-to-tail distance.
        var p = 0f
        var found: Float? = null
        while (p < 1f && found == null) {
            val s = meteorState(0, p)
            if (s != null) {
                val dx = s.tailX - s.headX
                val dy = s.tailY - s.headY
                found = sqrt(dx * dx + dy * dy)
            }
            p += 0.002f
        }
        assertNotNull("meteor never visible", found)
        // Tail is diagonal: length = tailLen * sqrt(1 + 0.56^2).
        assertEquals(
            meteorTailLength(0).toDouble() * 1.1461,
            found!!.toDouble(),
            0.002,
        )
        assertTrue("tail length $found", found in 0.26f..0.46f)
    }

    // --- Improve run 3 (2026-11-12): per-frame allocations removed ---
    // The draw code used to rebuild these small tables every frame
    // (60×/s); they are now hoisted to file-level vals. These tests pin
    // the exact numbers that were previously inline literals, so a
    // transcription slip fails here instead of silently changing the sky.

    @Test
    fun `meteor tail tables are consistent and fade outward`() {
        // 5 segments need 6 fraction stops (head..tip) and 5 alphas/widths.
        assertEquals(6, METEOR_TAIL_FRACTIONS.size)
        assertEquals(5, METEOR_TAIL_ALPHAS.size)
        assertEquals(5, METEOR_TAIL_WIDTHS_DP.size)
        assertEquals(0f, METEOR_TAIL_FRACTIONS.first())
        assertEquals(1f, METEOR_TAIL_FRACTIONS.last())
        for (i in 1 until METEOR_TAIL_FRACTIONS.size) {
            assertTrue(METEOR_TAIL_FRACTIONS[i] > METEOR_TAIL_FRACTIONS[i - 1])
        }
        // Brightest + thickest at the head, faintest + thinnest at the tip.
        assertEquals(1f, METEOR_TAIL_ALPHAS.first())
        assertEquals(0.12f, METEOR_TAIL_ALPHAS.last())
        assertEquals(2.2f, METEOR_TAIL_WIDTHS_DP.first())
        assertEquals(0.8f, METEOR_TAIL_WIDTHS_DP.last())
        for (i in 1 until METEOR_TAIL_ALPHAS.size) {
            assertTrue(METEOR_TAIL_ALPHAS[i] < METEOR_TAIL_ALPHAS[i - 1])
            assertTrue(METEOR_TAIL_WIDTHS_DP[i] < METEOR_TAIL_WIDTHS_DP[i - 1])
        }
    }

    @Test
    fun `milky way glow stops are violet pink violet`() {
        assertEquals(listOf(0.25f, 0.50f, 0.75f), MILKY_WAY_GLOW_STOPS)
    }

    // --- 0.3-feedback (Track 7): the solar system is back ---

    @Test
    fun `solar system has five planets on widening slow orbits`() {
        assertEquals(5, SOLAR_PLANETS.size)
        assertTrue("sun $SOLAR_SUN_X,$SOLAR_SUN_Y", SOLAR_SUN_X in 0.5f..0.9f)
        assertTrue("sun $SOLAR_SUN_X,$SOLAR_SUN_Y", SOLAR_SUN_Y in 0.05f..0.3f)
        var previousOrbit = 0f
        var previousSpeed = Float.MAX_VALUE
        for (planet in SOLAR_PLANETS) {
            // Orbits widen outward; speeds fall with distance, as in
            // the real system.
            assertTrue("orbit ${planet.orbitRadius}", planet.orbitRadius > previousOrbit)
            assertTrue("speed ${planet.orbitsPerLoop}", planet.orbitsPerLoop < previousSpeed)
            assertTrue("size ${planet.sizeDp}", planet.sizeDp in 2f..6f)
            previousOrbit = planet.orbitRadius
            previousSpeed = planet.orbitsPerLoop
        }
        // Only Saturn has a ring; Earth and Saturn have one moon each.
        assertEquals(listOf(false, false, false, false, true), SOLAR_PLANETS.map { it.hasRing })
        assertEquals(listOf(0, 0, 1, 0, 1), SOLAR_PLANETS.map { it.moonCount })
    }

    @Test
    fun `planets orbit the sun on tilted ellipses and advance slowly`() {
        for (i in SOLAR_PLANETS.indices) {
            // On the tilted ellipse: dx^2 + (dy / 0.55)^2 = 1.
            for (p in listOf(0f, 0.13f, 0.5f, 0.87f)) {
                val (dx, dy) = solarPlanetOffset(i, p)
                val onEllipse = dx * dx + (dy / 0.55f) * (dy / 0.55f)
                assertEquals("planet $i at $p", 1.0, onEllipse.toDouble(), 1e-5)
                assertEquals(solarPlanetOffset(i, p), solarPlanetOffset(i, p))
            }
            // Angles advance with progress — slowly: less than a
            // quarter orbit per loop for every planet.
            val a0 = solarPlanetAngle(i, 0f)
            val a1 = solarPlanetAngle(i, 1f)
            val advanced = a1 - a0
            assertTrue("planet $i advanced $advanced", advanced > 0f)
            assertTrue("planet $i advanced $advanced", advanced < Math.PI.toFloat() / 2f)
        }
    }

    @Test
    fun `moons orbit their planets on tilted ellipses`() {
        for (i in SOLAR_PLANETS.indices) {
            for (m in 0 until SOLAR_PLANETS[i].moonCount) {
                for (p in listOf(0f, 0.31f, 0.7f)) {
                    val (dx, dy) = solarMoonOffset(i, m, p)
                    val onEllipse = dx * dx + (dy / 0.55f) * (dy / 0.55f)
                    assertEquals("moon $m of $i at $p", 1.0, onEllipse.toDouble(), 1e-5)
                }
                // Moons move: the angle changes over the loop.
                assertTrue(solarMoonAngle(i, m, 0.5f) != solarMoonAngle(i, m, 0f))
            }
        }
    }
}
