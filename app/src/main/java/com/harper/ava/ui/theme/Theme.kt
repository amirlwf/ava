package com.harper.ava.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.data.preferences.ThemeMode

/**
 * The active theme spec for screens that need more than the Material3
 * colours — e.g. the Now Playing hero gradient
 * ([heroGradientColors]) or a theme-tinted custom surface. Provided by
 * [AvaTheme]; defaults to the VIOLET spec so previews still work.
 */
val LocalAppThemeSpec = staticCompositionLocalOf { AppTheme.VIOLET.spec }

/** Hero gradient colours for the current mode (3 stops, top → bottom). */
fun heroGradientColors(spec: ThemeSpec, dark: Boolean): List<Color> =
    (if (dark) spec.heroDark else spec.heroLight).map { Color(it) }

/** Material3 shapes from a theme's corner radii. */
fun themeShapes(spec: ThemeSpec): Shapes = Shapes(
    extraSmall = RoundedCornerShape(spec.radiusSmallDp.dp / 2),
    small = RoundedCornerShape(spec.radiusSmallDp.dp),
    medium = RoundedCornerShape(spec.radiusMediumDp.dp),
    large = RoundedCornerShape(spec.radiusLargeDp.dp),
    extraLarge = RoundedCornerShape(spec.radiusExtraLargeDp.dp),
)

/**
 * 0.1: frosted-glass panel for the glass theme (IPHONE only).
 *
 * When the active theme is glass, the surface becomes translucent
 * (72% of the theme's surface container) with a 1dp hairline border
 * in the theme outline — the frosted-glass look: content behind the
 * panel shows through faintly, and the hairline keeps the edge crisp
 * against any background. On every other theme this is exactly
 * `background(baseColor, shape)`, so non-glass themes are untouched.
 *
 * Glass is applied only where it earns its place (the floating
 * MiniPlayer pill, the bottom navigation bar, the Now Playing
 * backdrop): never on plain list rows or text, where translucency
 * would only hurt readability.
 */
@Composable
fun Modifier.glassPanel(shape: Shape, baseColor: Color): Modifier {
    val spec = LocalAppThemeSpec.current
    return if (spec.isGlass) {
        this
            .background(baseColor.copy(alpha = 0.72f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), shape)
    } else {
        this.background(baseColor, shape)
    }
}

/** Moves a colour toward white ([factor] 0..1); 0 returns the colour unchanged. */
private fun Color.lightened(factor: Float): Color {
    val f = factor.coerceIn(0f, 1f)
    return copy(red = red + (1f - red) * f, green = green + (1f - green) * f, blue = blue + (1f - blue) * f)
}

/** Moves a colour toward black ([factor] 0..1); 0 returns the colour unchanged. */
private fun Color.darkened(factor: Float): Color {
    val f = factor.coerceIn(0f, 1f)
    return copy(red = red * (1f - f), green = green * (1f - f), blue = blue * (1f - f))
}

private fun darkScheme(spec: ThemeSpec, amoled: Boolean): androidx.compose.material3.ColorScheme {
    val variant = if (amoled) Color(spec.amoledSurfaceVariant) else Color(spec.darkSurfaceVariant)
    // Elevation steps: a gentle lift above the variant. AMOLED steps stay
    // very small so surfaces remain effectively black.
    val high = variant.lightened(if (amoled) 0.06f else 0.35f)
    val highest = variant.lightened(if (amoled) 0.12f else 0.6f)
    return darkColorScheme(
    primary = Color(spec.primaryDark),
    onPrimary = Color(spec.onPrimaryDark),
    secondary = Color(spec.secondaryDark),
    tertiary = Color(spec.tertiaryDark),
    primaryContainer = Color(spec.primaryContainerDark),
    onPrimaryContainer = Color(spec.onPrimaryContainerDark),
    background = if (amoled) Color.Black else Color(spec.darkBackground),
    onBackground = Color(spec.darkOnBackground),
    surface = if (amoled) Color(spec.amoledSurface) else Color(spec.darkSurface),
    onSurface = Color(spec.darkOnSurface),
    surfaceVariant = if (amoled) Color(spec.amoledSurfaceVariant) else Color(spec.darkSurfaceVariant),
    onSurfaceVariant = Color(spec.darkOnSurfaceVariant),
    surfaceContainerLowest = if (amoled) Color.Black else Color(spec.darkBackground),
    surfaceContainerLow = if (amoled) Color(spec.amoledSurface) else Color(spec.darkSurface),
    surfaceContainer = variant,
    surfaceContainerHigh = high,
    surfaceContainerHighest = highest,
    outline = Color(spec.outlineDark),
    outlineVariant = Color(spec.outlineDark),
    )
}

private fun lightScheme(spec: ThemeSpec): androidx.compose.material3.ColorScheme {
    val variant = Color(spec.lightSurfaceVariant)
    return lightColorScheme(
    primary = Color(spec.primaryLight),
    onPrimary = Color(spec.onPrimaryLight),
    secondary = Color(spec.secondaryLight),
    tertiary = Color(spec.tertiaryLight),
    primaryContainer = Color(spec.primaryContainerLight),
    onPrimaryContainer = Color(spec.onPrimaryContainerLight),
    background = Color(spec.lightBackground),
    onBackground = Color(spec.lightOnBackground),
    surface = Color(spec.lightSurface),
    onSurface = Color(spec.lightOnSurface),
    surfaceVariant = Color(spec.lightSurfaceVariant),
    onSurfaceVariant = Color(spec.lightOnSurfaceVariant),
    surfaceContainerLowest = Color(spec.lightSurface),
    surfaceContainerLow = Color(spec.lightSurfaceVariant),
    surfaceContainer = variant,
    surfaceContainerHigh = variant.darkened(0.05f),
    surfaceContainerHighest = variant.darkened(0.1f),
    outline = Color(spec.outlineLight),
    outlineVariant = Color(spec.outlineLight),
    )
}

/**
 * The app theme (0.7.0: [AppTheme] + [ThemeMode]).
 *
 * Precedence: a user-picked custom colour ([customAccentArgb], the HSV
 * picker) still wins over everything; otherwise the chosen [AppTheme]
 * supplies the whole palette, gradient and shapes. The old [Accent]
 * preset is kept in the signature only so existing call sites keep
 * compiling — it no longer drives any colour. The preset choice stays
 * stored in DataStore (untouched) in case a future round wants it back,
 * but picking a theme replaces it visually; see AppearanceScreen.
 *
 * [ThemeMode] is unchanged: SYSTEM follows the phone, DARK/LIGHT force a
 * mode, AMOLED forces dark with a pure black background (the spec only
 * tints the near-black surfaces).
 */
@Composable
fun AvaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: Accent = Accent.VIOLET,
    /** When non-null, this color overrides the [appTheme] palette everywhere. */
    customAccentArgb: Int? = null,
    appTheme: AppTheme = AppTheme.VIOLET,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val spec = appTheme.spec
    val colorScheme = when {
        !dark -> lightScheme(spec)
        themeMode == ThemeMode.AMOLED -> darkScheme(spec, amoled = true)
        else -> darkScheme(spec, amoled = false)
    }
    // A custom colour replaces the theme's primary family only; the
    // theme's surfaces, gradient and shapes stay, so the app never loses
    // its identity when a user picks one favourite colour.
    val schemeWithCustom = customAccentArgb?.let {
        val palette = customAccentPalette(it)
        colorScheme.copy(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
        )
    } ?: colorScheme
    CompositionLocalProvider(LocalAppThemeSpec provides spec) {
        MaterialTheme(
            colorScheme = schemeWithCustom,
            typography = AvaTypography,
            shapes = themeShapes(spec),
            content = content,
        )
    }
}
