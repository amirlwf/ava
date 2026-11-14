package com.harper.ava.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.data.preferences.ThemeMode
import com.harper.ava.ui.common.AvaMotion

/**
 * The active theme spec for screens that need more than the Material3
 * colours — e.g. the hero gradient stops ([ThemeSpec.heroDark] /
 * [ThemeSpec.heroLight]) or a theme-tinted custom surface. Provided by
 * [AvaTheme]; defaults to the VIOLET spec so previews still work.
 */
val LocalAppThemeSpec = staticCompositionLocalOf { AppTheme.VIOLET.spec }

/** Material3 shapes from a theme's corner radii. */
fun themeShapes(spec: ThemeSpec): Shapes = themeShapes(
    small = spec.radiusSmallDp.dp,
    medium = spec.radiusMediumDp.dp,
    large = spec.radiusLargeDp.dp,
    extraLarge = spec.radiusExtraLargeDp.dp,
)

/**
 * Material3 shapes from corner radii — the animated-radii form
 * (improve loop run 14): [AvaTheme] passes the radii gliding on
 * [AvaMotion.themeRadiusTween], so a theme change morphs the shape
 * scale instead of snapping it. `extraSmall` stays half of `small`,
 * exactly as the spec form above.
 */
fun themeShapes(small: Dp, medium: Dp, large: Dp, extraLarge: Dp): Shapes = Shapes(
    extraSmall = RoundedCornerShape(small / 2),
    small = RoundedCornerShape(small),
    medium = RoundedCornerShape(medium),
    large = RoundedCornerShape(large),
    extraLarge = RoundedCornerShape(extraLarge),
)

/**
 * 0.1: frosted-glass panel for the glass theme (IPHONE only).
 *
 * When the active theme is glass, the surface becomes translucent
 * (45% of the theme's surface container — 0.3-feedback Track 6, was
 * 55%: the stronger aurora behind it has to show through or the
 * glass reads as a flat panel) with a 1dp hairline border in the
 * theme outline (0.5 alpha, was 0.35) — the frosted-glass look:
 * content behind the panel shows through faintly, and the hairline
 * keeps the edge crisp against any background. On every other theme
 * this is exactly `background(baseColor, shape)`, so non-glass
 * themes are untouched.
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
            .background(baseColor.copy(alpha = 0.45f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
    } else {
        this.background(baseColor, shape)
    }
}

/**
 * 0.3-feedback (Track 6) — let the aurora show through sub-screens.
 *
 * The nested Scaffolds on the sub-screens (Settings, Appearance,
 * Language, Playback, Storage, About, Sleep, Equalizer, Lyrics,
 * Playlists) used the default opaque `surface` container colour, so
 * on the glass theme they hid the aurora completely — the theme only
 * read on Home/Library/Search. On glass their container is
 * transparent, so the sky shows through between the cards; on every
 * other theme it is exactly the default surface. The TopAppBar keeps
 * a frosted (72% surface) bar instead of full transparency, like
 * iOS's translucent nav bar: scrolling content stays readable under
 * it while the sky still shows through faintly.
 */
@Composable
fun glassAwareScaffoldContainerColor(): Color =
    if (LocalAppThemeSpec.current.isGlass) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surface
    }

/** Frosted TopAppBar container on glass; exactly the default surface elsewhere. */
@Composable
fun glassAwareTopBarContainerColor(): Color =
    if (LocalAppThemeSpec.current.isGlass) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surface
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
    // Elevation steps: a gentle lift above the variant. Non-AMOLED
    // keeps the 0.7.0 factors (0.35 / 0.6 on the dark variant).
    // 0.2-fix (Track 2): the old AMOLED steps (0.06 / 0.12) were
    // invisible on pure black (1.11:1 / 1.35:1) — dialogs, the nav
    // bar, cards and rows melted into the background. AMOLED now
    // lifts through the named steps in [amoledLiftedContainer]
    // (pure, pinned in AppThemeTest): the background stays pure
    // black, only the containers read as surfaces.
    val low = if (amoled) {
        Color(amoledLiftedContainer(spec.amoledSurface, AmoledSurfaceStep.LOW))
    } else {
        Color(spec.darkSurface)
    }
    val container = if (amoled) {
        Color(amoledLiftedContainer(spec.amoledSurfaceVariant, AmoledSurfaceStep.CONTAINER))
    } else {
        variant
    }
    val high = if (amoled) {
        Color(amoledLiftedContainer(spec.amoledSurfaceVariant, AmoledSurfaceStep.HIGH))
    } else {
        variant.lightened(0.35f)
    }
    val highest = if (amoled) {
        Color(amoledLiftedContainer(spec.amoledSurfaceVariant, AmoledSurfaceStep.HIGHEST))
    } else {
        variant.lightened(0.6f)
    }
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
    surfaceContainerLow = low,
    surfaceContainer = container,
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
 * Edge-to-edge (Harper 2026-11-13): whether the app is dark, from the
 * stored [ThemeMode] and the phone's system mode. Pure, so it is pinned
 * by a unit test — and it is the SAME decision the status-bar and
 * navigation-bar icon styles must follow (MainActivity re-applies
 * `enableEdgeToEdge` with it): the bars follow the APP's theme, not
 * the system's. `enableEdgeToEdge()` with no args picks
 * `SystemBarStyle.auto`, which reads the system night mode — so an
 * in-app Dark/AMOLED theme on a light system (or Light on a dark
 * system) left the bar icons invisible against the app's own header.
 */
fun isDarkTheme(themeMode: ThemeMode, systemDark: Boolean): Boolean =
    when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
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
    val dark = isDarkTheme(themeMode, isSystemInDarkTheme())
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
    // 0.3-feedback fix (Track 2) — ROOT CAUSE of Harper's "black
    // icons in AMOLED" report, proven in the shipped Material3 1.4.0
    // bytecode: `MaterialTheme` provides LocalColorScheme, Shapes,
    // Typography, Indication and TextSelectionColors — but NOT
    // `LocalContentColor`, whose default is `Color.Black`. A bare
    // `Icon()` (no `tint =`) therefore renders BLACK wherever no
    // parent provides a content colour (TopAppBar, NavigationBar,
    // Button and Surface do; a plain Row/Column/Scaffold does not).
    // In light themes black-on-white merely looks normal, so nobody
    // noticed; in ANY dark theme (plain dark AND AMOLED, every
    // theme's dark palette) the icons vanish into the background —
    // the Home scan/settings icons Harper reported, and the same
    // fault on every other bare icon in the app. Text was never hit
    // because every Text passes an explicit scheme colour.
    //
    // The fix is one provider, here, at the theme root: the content
    // colour is the computed scheme's `onSurface`, so it flips light
    // in every dark theme and stays near-black in light themes
    // (visually identical there). Components that provide their own
    // content colour (TopAppBar, NavigationBarItem, Button, Surface)
    // still override locally, as before. [AnimatedThemeColors] in
    // MainActivity re-provides the ANIMATED onSurface so icons glide
    // with the rest of the scheme on a theme change instead of
    // snapping ahead of it.
    // Improve loop run 14 — the radii glide with the colours: each
    // theme's corner radii animate to their new values on the same
    // 350 ms theme tween ([AvaMotion.themeRadiusTween]), so switching
    // themes morphs cards, buttons and dialogs instead of snapping
    // their corners while the palette glides (the 0.7.0 cosmetic gap
    // "theme radii snap while colors glide"). First composition starts
    // at the target — no animation from zero on launch.
    val radiusSmall by animateDpAsState(
        targetValue = spec.radiusSmallDp.dp,
        animationSpec = AvaMotion.themeRadiusTween,
        label = "theme-radius-small",
    )
    val radiusMedium by animateDpAsState(
        targetValue = spec.radiusMediumDp.dp,
        animationSpec = AvaMotion.themeRadiusTween,
        label = "theme-radius-medium",
    )
    val radiusLarge by animateDpAsState(
        targetValue = spec.radiusLargeDp.dp,
        animationSpec = AvaMotion.themeRadiusTween,
        label = "theme-radius-large",
    )
    val radiusExtraLarge by animateDpAsState(
        targetValue = spec.radiusExtraLargeDp.dp,
        animationSpec = AvaMotion.themeRadiusTween,
        label = "theme-radius-extra-large",
    )
    CompositionLocalProvider(
        LocalAppThemeSpec provides spec,
        LocalContentColor provides schemeWithCustom.onSurface,
    ) {
        MaterialTheme(
            colorScheme = schemeWithCustom,
            typography = AvaTypography,
            shapes = themeShapes(radiusSmall, radiusMedium, radiusLarge, radiusExtraLarge),
            content = content,
        )
    }
}
