package com.harper.ava.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.ThemeMode

private fun darkScheme(palette: AccentPalette, amoled: Boolean) = darkColorScheme(
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    secondary = palette.secondary,
    tertiary = palette.tertiary,
    primaryContainer = palette.primaryContainer,
    onPrimaryContainer = palette.onPrimaryContainer,
    background = if (amoled) AvaSurfaces.amoledBackground else AvaSurfaces.darkBackground,
    onBackground = AvaSurfaces.darkOnBackground,
    surface = if (amoled) AvaSurfaces.amoledSurface else AvaSurfaces.darkSurface,
    onSurface = AvaSurfaces.darkOnSurface,
    surfaceVariant = if (amoled) AvaSurfaces.amoledSurfaceVariant else AvaSurfaces.darkSurfaceVariant,
    onSurfaceVariant = AvaSurfaces.darkOnSurfaceVariant,
    surfaceContainerLowest = if (amoled) Color.Black else Color(0xFF0D0D13),
    surfaceContainerLow = if (amoled) AvaSurfaces.amoledSurface else AvaSurfaces.darkSurface,
    surfaceContainer = if (amoled) AvaSurfaces.amoledSurfaceVariant else AvaSurfaces.darkSurfaceVariant,
    surfaceContainerHigh = if (amoled) Color(0xFF1F1F1F) else Color(0xFF2E2E40),
    surfaceContainerHighest = if (amoled) Color(0xFF2A2A2A) else Color(0xFF3A3A4E),
    outline = Color(0xFF3D3D52),
    outlineVariant = Color(0xFF2A2A3A),
)

private fun lightScheme(palette: AccentPalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    secondary = palette.secondary,
    tertiary = palette.tertiary,
    primaryContainer = palette.primaryContainer,
    onPrimaryContainer = palette.onPrimaryContainer,
    background = AvaSurfaces.lightBackground,
    onBackground = AvaSurfaces.lightOnBackground,
    surface = AvaSurfaces.lightSurface,
    onSurface = AvaSurfaces.lightOnSurface,
    surfaceVariant = AvaSurfaces.lightSurfaceVariant,
    onSurfaceVariant = AvaSurfaces.lightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF0F0F5),
    surfaceContainerHigh = Color(0xFFE8E8EF),
    surfaceContainerHighest = Color(0xFFDFDFE8),
    outline = Color(0xFFC9C9D6),
    outlineVariant = Color(0xFFE0E0EA),
)

/**
 * The app theme. [ThemeMode] + [Accent] come from DataStore (persisted) and
 * every screen reads the same scheme, so a theme change recolors the whole
 * app at once. No hard-coded colors live in screens.
 */
@Composable
fun AvaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: Accent = Accent.VIOLET,
    /** When non-null, this color overrides the [accent] palette everywhere. */
    customAccentArgb: Int? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = customAccentArgb?.let { customAccentPalette(it) } ?: accentPalette(accent)
    val colorScheme = when {
        !dark -> lightScheme(palette)
        themeMode == ThemeMode.AMOLED -> darkScheme(palette, amoled = true)
        else -> darkScheme(palette, amoled = false)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AvaTypography,
        content = content,
    )
}
