package com.harper.ava.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Design tokens — the single source of truth for spacing, corner radii and
 * motion. No screen hard-codes these values; every composable reads them
 * from here so the whole app stays visually consistent.
 */
object AvaTokens {
    // Spacing (4dp grid)
    val space2 = 2.dp
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space48 = 48.dp
    val space64 = 64.dp

    // Corner radii
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
    val radiusExtraLarge = 24.dp
    val radiusFull = 100.dp

    // Component sizes
    val miniPlayerHeight = 64.dp
    val bottomBarHeight = 80.dp
    val songRowHeight = 64.dp
    /** Queue rows are a fixed height: the drag math in QueueScreen relies on it. */
    val queueRowHeight = 72.dp
    val nowPlayingCoverSize = 300.dp
    val coverSmall = 48.dp
    val coverMedium = 56.dp
    val emptyStateIconSize = 96.dp
    val touchTargetMin = 48.dp
}
