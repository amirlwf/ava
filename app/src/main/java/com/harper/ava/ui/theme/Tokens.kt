package com.harper.ava.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Design tokens — the single source of truth for spacing, corner radii and
 * motion. No screen hard-codes these values; every composable reads them
 * from here so the whole app stays visually consistent.
 *
 * 0.7.0: the radii below are the 0.6.0 defaults (the VIOLET theme).
 * Each of the ten themes carries its own radii in its [ThemeSpec]
 * (`ui/theme/AppTheme.kt`), applied app-wide through MaterialTheme
 * shapes in `Theme.kt` — a theme's shapes win over these defaults for
 * every Material component. These tokens stay as the single source for
 * spacing, component sizes and custom-clipped surfaces that read a
 * fixed radius; screens that want the *current theme's* radii should
 * read `MaterialTheme.shapes` (or `LocalAppThemeSpec`) instead.
 */
object AvaTokens {
    // Spacing (4dp grid, plus 6dp for tight title/subtitle gaps)
    val space2 = 2.dp
    val space4 = 4.dp
    val space6 = 6.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space40 = 40.dp
    val space48 = 48.dp
    val space64 = 64.dp

    // Corner radii — soft and round (0.6.0): every surface is one step
    // rounder than before (8/12/16/24 -> 12/16/20/28).
    val radiusSmall = 12.dp
    val radiusMedium = 16.dp
    val radiusLarge = 20.dp
    val radiusExtraLarge = 28.dp
    val radiusFull = 100.dp

    // Soft elevation for floating surfaces (mini player pill, dragged
    // rows). Deliberately gentle: a cute lift, not a hard drop shadow.
    val elevationSoft = 8.dp
    val elevationLifted = 16.dp

    // Component sizes
    val miniPlayerHeight = 72.dp
    val bottomBarHeight = 80.dp
    val songRowHeight = 68.dp
    /** Queue rows are a fixed height: the drag math in QueueScreen relies on it. */
    val queueRowHeight = 72.dp
    val nowPlayingCoverSize = 300.dp
    val coverSmall = 52.dp
    val coverMedium = 60.dp
    /** The mini player's filled play button: a clear, thumb-friendly target. */
    val miniPlayerPlaySize = 48.dp
    /** The Now Playing play button: the single hero control on the screen. */
    val nowPlayingPlaySize = 76.dp
    val emptyStateIconSize = 96.dp
    val touchTargetMin = 48.dp
}
