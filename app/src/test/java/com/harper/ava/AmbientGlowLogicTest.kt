package com.harper.ava

import androidx.compose.ui.unit.IntSize
import com.harper.ava.ui.nowplaying.AMBIENT_ORBS
import com.harper.ava.ui.nowplaying.ambientBreathScale
import com.harper.ava.ui.nowplaying.ambientOrbAlpha
import com.harper.ava.ui.nowplaying.ambientOrbCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for the Now Playing ambient glow's pure math
 * ([ambientOrbCenter], [ambientOrbAlpha], [ambientBreathScale]).
 * The glow itself is a Canvas; these tests pin the numbers it draws
 * from, so a bad constant can never push an orb off-screen or make
 * the halo throb.
 */
class AmbientGlowLogicTest {

    private val canvas = IntSize(1080, 1920)

    @Test
    fun `orb centers stay inside the canvas for the whole loop`() {
        for (orb in AMBIENT_ORBS) {
            var progress = 0f
            while (progress <= 1.001f) {
                val center = ambientOrbCenter(orb, progress, canvas)
                assertTrue("orb $orb x=${center.x} at $progress", center.x in 0f..1080f)
                assertTrue("orb $orb y=${center.y} at $progress", center.y in 0f..1920f)
                progress += 0.05f
            }
        }
    }

    @Test
    fun `orb centers are deterministic`() {
        for (orb in AMBIENT_ORBS) {
            val first = ambientOrbCenter(orb, 0.37f, canvas)
            val second = ambientOrbCenter(orb, 0.37f, canvas)
            assertEquals(first, second)
        }
    }

    @Test
    fun `orb alpha rests at 45 percent and reaches base while playing`() {
        assertEquals(0.063f, ambientOrbAlpha(0.14f, 0f), 0.001f)
        assertEquals(0.14f, ambientOrbAlpha(0.14f, 1f), 0.001f)
        assertEquals(0.1015f, ambientOrbAlpha(0.14f, 0.5f), 0.001f)
    }

    @Test
    fun `orb alpha never leaves 0 to 1`() {
        for (base in listOf(0f, 0.1f, 0.14f, 1f)) {
            for (aliveness in listOf(-1f, 0f, 0.5f, 1f, 2f)) {
                val alpha = ambientOrbAlpha(base, aliveness)
                assertTrue("base=$base aliveness=$aliveness -> $alpha", alpha in 0f..1f)
            }
        }
    }

    @Test
    fun `breath scale is exactly 1 when resting`() {
        var progress = 0f
        while (progress <= 1.001f) {
            assertEquals(1f, ambientBreathScale(progress, 0f), 0.0001f)
            progress += 0.1f
        }
    }

    @Test
    fun `breath scale stays within 3_5 percent while playing`() {
        var progress = 0f
        while (progress <= 1.001f) {
            val scale = ambientBreathScale(progress, 1f)
            assertTrue("scale=$scale at $progress", abs(scale - 1f) <= 0.035f + 0.0001f)
            progress += 0.02f
        }
    }

    @Test
    fun `breath scale is periodic over one loop`() {
        assertEquals(
            ambientBreathScale(0f, 1f),
            ambientBreathScale(1f, 1f),
            0.0001f,
        )
    }
}
