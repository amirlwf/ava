package com.harper.ava

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.harper.ava.ui.theme.Almas
import com.harper.ava.ui.theme.SplashDedicationStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Improve run 9 (design & detail, 2026-11-13) — the splash dedication
 * line is the ONLY place Almas is used, and its look lives in one
 * named style in Type.kt, not as inline `fontFamily` / `fontSize` in
 * the screen (where it escaped the typography audit — the only such
 * case in the app). These tests pin that style: Almas, 16 sp on a
 * 24 sp line, centred — so the line stays centred even if it wraps
 * on a 320 dp screen or under a large system font scale.
 */
class SplashDedicationStyleTest {

    @Test
    fun `dedication is Almas at 16sp on a 24sp line, centred`() {
        assertEquals(Almas, SplashDedicationStyle.fontFamily)
        assertEquals(16.sp, SplashDedicationStyle.fontSize)
        assertEquals(24.sp, SplashDedicationStyle.lineHeight)
        assertEquals(TextAlign.Center, SplashDedicationStyle.textAlign)
    }
}
