package com.harper.ava.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.harper.ava.data.preferences.AppTheme

/**
 * The app's ONE persistent backdrop (0.1-fix1) — the theme background
 * colour plus the seasonal decor (Galaxy sky, leaves, snow, sun) plus,
 * on the glass theme, the aurora. It lives in MainActivity behind the
 * NavHost and outside every nav transition, so navigation never
 * unmounts it, never fades it and never blanks it.
 *
 * Why it exists: the decor used to live inside Home and Now Playing,
 * tied to each destination's resume state (`rememberIsResumed()`).
 * Tapping an icon (e.g. Settings) dropped the destination below
 * RESUMED, the Canvas left composition instantly, and the Galaxy
 * background vanished mid-transition while the outgoing screen was
 * still fading out — the flash Harper reported in 0.1. Here the
 * `active` flag reads the ACTIVITY's resume state (the
 * [LocalLifecycleOwner] at this level is the Activity, not a
 * destination), so only the app going to the background pauses the
 * sky — and even then the last frame stays frozen instead of
 * disappearing (see [SeasonalDecor]).
 *
 * The base is the theme's background colour, painted here instead of
 * by the Scaffold (MainActivity's Scaffold is transparent): the sky
 * and the aurora need a real surface to sit on, and they must extend
 * behind the bottom bar too, so the glass bar has something to be
 * glass over. Screens that paint their own background (nested
 * Scaffolds in Settings etc.) cover the backdrop in the content area
 * exactly as before; Home and Now Playing leave it visible.
 */
@Composable
fun AppBackdrop(
    theme: AppTheme,
    effectsEnabled: Boolean,
    modifier: Modifier = Modifier,
    amoled: Boolean = false,
) {
    val appResumed = rememberIsResumed()
    val spec = LocalAppThemeSpec.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // The aurora is the glass theme's backdrop: three soft colour
        // fields behind the frosted panels (Track C). It is drawn for
        // the glass theme only, at the same persistent level as the
        // decor, so it never unmounts or fades with navigation. The
        // decor itself draws nothing for IPHONE (not a seasonal theme).
        if (spec.isGlass) {
            GlassAurora(
                amoled = amoled,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SeasonalDecor(
            theme = theme,
            effectsEnabled = effectsEnabled,
            modifier = Modifier.fillMaxSize(),
            active = appResumed,
        )
    }
}
