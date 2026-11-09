package com.harper.ava

import com.harper.ava.core.util.argbToHex
import com.harper.ava.core.util.argbToHsv
import com.harper.ava.core.util.clampPlaybackSpeed
import com.harper.ava.core.util.deriveAccentColors
import com.harper.ava.core.util.formatFileSize
import com.harper.ava.core.util.hsvToArgb
import com.harper.ava.core.util.isLightColor
import com.harper.ava.core.util.localeTagFor
import com.harper.ava.data.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SettingsLogicTest {

    // -- playback speed ------------------------------------------------------

    @Test
    fun `speed inside range is unchanged`() {
        assertEquals(1.25f, clampPlaybackSpeed(1.25f), 0.001f)
    }

    @Test
    fun `speed clamps to 0_5 and 2_0`() {
        assertEquals(0.5f, clampPlaybackSpeed(0.1f), 0.001f)
        assertEquals(2.0f, clampPlaybackSpeed(3.0f), 0.001f)
    }

    @Test
    fun `NaN speed falls back to 1x`() {
        assertEquals(1.0f, clampPlaybackSpeed(Float.NaN), 0.001f)
    }

    // -- file sizes ----------------------------------------------------------

    @Test
    fun `file size formats bytes KB MB GB`() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("1.5 KB", formatFileSize(1_536L))
        assertEquals("23.4 MB", formatFileSize(24_543_232L))
        assertEquals("1.2 GB", formatFileSize(1_288_490_189L))
    }

    @Test
    fun `negative file size clamps to zero`() {
        assertEquals("0 B", formatFileSize(-100L))
    }

    // -- language ------------------------------------------------------------

    @Test
    fun `locale tags map fa en and system null`() {
        assertEquals("fa", localeTagFor(AppLanguage.FA))
        assertEquals("en", localeTagFor(AppLanguage.EN))
        assertNull(localeTagFor(AppLanguage.SYSTEM))
    }

    // -- custom accent -------------------------------------------------------

    @Test
    fun `derived palette keeps the picked color as primary`() {
        val picked = 0xFF7C5CFF.toInt()
        val palette = deriveAccentColors(picked)
        assertEquals(picked, palette.primary)
        assertEquals("#7C5CFF", argbToHex(palette.primary))
    }

    @Test
    fun `dark primary gets white text, light primary gets dark text`() {
        val dark = deriveAccentColors(0xFF1E1B4B.toInt())
        assertEquals(0xFFFFFFFF.toInt(), dark.onPrimary)
        val light = deriveAccentColors(0xFFFDE68A.toInt())
        assertEquals(0xFF1C1917.toInt(), light.onPrimary)
    }

    @Test
    fun `isLightColor separates black white and mid violet`() {
        assertTrue(isLightColor(0xFFFFFFFF.toInt()))
        assertFalse(isLightColor(0xFF000000.toInt()))
        assertFalse(isLightColor(0xFF7C5CFF.toInt()))
        assertTrue(isLightColor(0xFFFDE68A.toInt()))
    }

    // -- HSV picker math -----------------------------------------------------

    @Test
    fun `hsv round trip preserves the color`() {
        val original = 0xFF7C5CFF.toInt()
        val (h, s, v) = argbToHsv(original)
        val back = hsvToArgb(h, s, v)
        assertTrue(
            "round trip drifted: ${argbToHex(original)} -> ${argbToHex(back)}",
            abs(((back ushr 16) and 0xFF) - ((original ushr 16) and 0xFF)) <= 1 &&
                abs(((back ushr 8) and 0xFF) - ((original ushr 8) and 0xFF)) <= 1 &&
                abs((back and 0xFF) - (original and 0xFF)) <= 1,
        )
    }

    @Test
    fun `hsv primaries convert exactly`() {
        assertEquals(0xFFFF0000.toInt(), hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), hsvToArgb(240f, 1f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), hsvToArgb(0f, 0f, 1f))
        assertEquals(0xFF000000.toInt(), hsvToArgb(0f, 0f, 0f))
    }

    @Test
    fun `hue wraps past 360`() {
        assertEquals(hsvToArgb(0f, 1f, 1f), hsvToArgb(360f, 1f, 1f))
        assertEquals(hsvToArgb(30f, 1f, 1f), hsvToArgb(390f, 1f, 1f))
    }
}
