package com.harper.ava.ui.theme

import com.harper.ava.data.preferences.AppTheme
import kotlin.math.pow

/**
 * 0.7.0 design round — the ten Ava themes.
 *
 * A theme is a complete design system, not just an accent colour: its own
 * primary/secondary/tertiary, its own dark + light surfaces, its own hero
 * gradient, and its own corner radii. [ThemeSpec] is pure data (ARGB
 * [Long]s, dp [Int]s, no Compose dependency) so the mapping is unit
 * tested ([com.harper.ava.AppThemeTest]); `ui/theme/Theme.kt` converts a
 * spec into a Material3 [androidx.compose.material3.ColorScheme] and
 * [androidx.compose.material3.Shapes].
 *
 * The ten themes (default [AppTheme.VIOLET] keeps the exact 0.6.0 look):
 *
 * - SPRING / بهار — fresh leaf green + cherry-blossom pink, soft round shapes.
 * - SUMMER / تابستان — sunny cyan + amber, the roundest, most playful shapes.
 * - AUTUMN / پاییز — burnt amber + maple red on warm brown-charcoal.
 * - WINTER / زمستان — ice blue + frost on cold navy, the crispest (least round) shapes.
 * - EMERALD / زمردی — deep emerald green, elegant.
 * - VIOLET / بنفش — Ava's original violet (the 0.6.0 look, unchanged).
 * - OCEAN / اقیانوس — midnight deep-blue ocean.
 * - SUNSET / غروب — dusk orange + rose + purple, the roundest shapes with Summer.
 * - GALAXY / مشکی کهکشانی (0.7.2) — deep space black with nebula violet /
 *   pink / blue. NOT the same as AMOLED: AMOLED is pure black with no
 *   identity, Galaxy is a space aesthetic — starfield decor, a nebula
 *   hero gradient and indigo-tinted surfaces in every mode.
 * - IPHONE / آیفون (0.1) — Apple's design language: frosted-glass
 *   surfaces, pure-black dark background, airy #F2F2F7 light background,
 *   large continuous corners. The only glass theme ([ThemeSpec.isGlass]);
 *   it shows no seasonal decor — the glass itself is its identity.
 *
 * Every theme works in all three [com.harper.ava.data.preferences.ThemeMode]s:
 * dark uses the theme-tinted charcoal surfaces, light uses a theme-tinted
 * paper, and AMOLED keeps a **pure black background** with only a whisper
 * of theme tint in the surfaces ([amoledSurface]/[amoledSurfaceVariant]
 * luminance < 0.01 — pixels stay effectively off).
 *
 * Contrast (verified in AppThemeTest, WCAG ratios on the real ARGB values):
 * body text vs background >= 7, secondary text >= 4.5, primary vs
 * background >= 3, on-primary label vs primary >= 4.0. Bright dark-mode
 * primaries therefore take a very dark on-primary; light-mode primaries
 * are a darker shade of the same hue so white labels stay >= 5.
 */
data class ThemeSpec(
    val nameFa: String,
    val nameEn: String,
    val moodFa: String,
    val moodEn: String,
    // Accent colours — one shade per mode so contrast holds on both.
    val primaryDark: Long,
    val onPrimaryDark: Long,
    val primaryLight: Long,
    val onPrimaryLight: Long,
    val secondaryDark: Long,
    val secondaryLight: Long,
    val tertiaryDark: Long,
    val tertiaryLight: Long,
    val primaryContainerDark: Long,
    val onPrimaryContainerDark: Long,
    val primaryContainerLight: Long,
    val onPrimaryContainerLight: Long,
    // Dark surfaces (theme-tinted charcoal, never pure black here).
    val darkBackground: Long,
    val darkSurface: Long,
    val darkSurfaceVariant: Long,
    val darkOnBackground: Long,
    val darkOnSurface: Long,
    val darkOnSurfaceVariant: Long,
    // Light surfaces (theme-tinted paper).
    val lightBackground: Long,
    val lightSurface: Long,
    val lightSurfaceVariant: Long,
    val lightOnBackground: Long,
    val lightOnSurface: Long,
    val lightOnSurfaceVariant: Long,
    // AMOLED surfaces — near-black tinted surfaces; the background itself
    // stays Color.Black (see Theme.kt), never a spec colour.
    val amoledSurface: Long,
    val amoledSurfaceVariant: Long,
    val outlineDark: Long,
    val outlineLight: Long,
    /** Hero gradient stops (3): Now Playing / header backgrounds. */
    val heroDark: List<Long>,
    val heroLight: List<Long>,
    /** Corner radii in dp — small / medium / large / extra-large. */
    val radiusSmallDp: Int,
    val radiusMediumDp: Int,
    val radiusLargeDp: Int,
    val radiusExtraLargeDp: Int,
    /**
     * 0.1: true when the theme renders frosted-glass surfaces (the
     * IPHONE theme only). Glass is a deliberate design language, not a
     * decoration: translucent surfaces + hairline borders + stronger
     * background blur, applied in [Theme.kt]'s `glassPanel` and the
     * screens that read this flag. Every other theme is false, so
     * glass never leaks into a theme that didn't ask for it.
     */
    val isGlass: Boolean = false,
)

/** The spec for a theme. */
fun appThemeSpec(theme: AppTheme): ThemeSpec = when (theme) {
    AppTheme.SPRING -> ThemeSpec(
        nameFa = "بهار",
        nameEn = "Spring",
        moodFa = "سبز تازه و شکوفهٔ صورتی",
        moodEn = "Fresh leaf green and blossom pink",
        primaryDark = 0xFF4ADE80,
        onPrimaryDark = 0xFF052E16,
        primaryLight = 0xFF15803D,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFFF472B6,
        secondaryLight = 0xFFBE185D,
        tertiaryDark = 0xFF38BDF8,
        tertiaryLight = 0xFF0369A1,
        primaryContainerDark = 0xFF123524,
        onPrimaryContainerDark = 0xFFBBF7D0,
        primaryContainerLight = 0xFFDCFCE7,
        onPrimaryContainerLight = 0xFF14532D,
        darkBackground = 0xFF101812,
        darkSurface = 0xFF1A241D,
        darkSurfaceVariant = 0xFF27332B,
        darkOnBackground = 0xFFF4F8F4,
        darkOnSurface = 0xFFEAF2EA,
        darkOnSurfaceVariant = 0xFFA3B1A4,
        lightBackground = 0xFFF7FBF6,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFEDF3EC,
        lightOnBackground = 0xFF141B15,
        lightOnSurface = 0xFF1B241C,
        lightOnSurfaceVariant = 0xFF5B6B5D,
        amoledSurface = 0xFF0A0F0B,
        amoledSurfaceVariant = 0xFF131A14,
        outlineDark = 0xFF3A4A3D,
        outlineLight = 0xFFC4D2C5,
        heroDark = listOf(0xFF4ADE80, 0xFFF472B6, 0xFF14532D),
        heroLight = listOf(0xFF86EFAC, 0xFFF9A8D4, 0xFFDCFCE7),
        radiusSmallDp = 16,
        radiusMediumDp = 20,
        radiusLargeDp = 24,
        radiusExtraLargeDp = 32,
    )
    AppTheme.SUMMER -> ThemeSpec(
        nameFa = "تابستان",
        nameEn = "Summer",
        moodFa = "آفتاب طلایی و دریای فیروزه‌ای",
        moodEn = "Golden sun and turquoise sea",
        primaryDark = 0xFF22D3EE,
        onPrimaryDark = 0xFF082F3A,
        primaryLight = 0xFF0E7490,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFFFBBF24,
        secondaryLight = 0xFFB45309,
        tertiaryDark = 0xFFFB7185,
        tertiaryLight = 0xFFBE123C,
        primaryContainerDark = 0xFF0B3540,
        onPrimaryContainerDark = 0xFFA5F3FC,
        primaryContainerLight = 0xFFCFFAFE,
        onPrimaryContainerLight = 0xFF164E63,
        darkBackground = 0xFF0C1B1E,
        darkSurface = 0xFF14262A,
        darkSurfaceVariant = 0xFF1F353A,
        darkOnBackground = 0xFFF2F8F8,
        darkOnSurface = 0xFFE6F1F2,
        darkOnSurfaceVariant = 0xFF9DB4B8,
        lightBackground = 0xFFFDFBF3,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFF3EEE0,
        lightOnBackground = 0xFF171A15,
        lightOnSurface = 0xFF1E2422,
        lightOnSurfaceVariant = 0xFF5F6B66,
        amoledSurface = 0xFF090E10,
        amoledSurfaceVariant = 0xFF11191C,
        outlineDark = 0xFF33505A,
        outlineLight = 0xFFC9D6D2,
        heroDark = listOf(0xFF22D3EE, 0xFFFBBF24, 0xFF155E75),
        heroLight = listOf(0xFF67E8F9, 0xFFFDE68A, 0xFFCFFAFE),
        radiusSmallDp = 20,
        radiusMediumDp = 24,
        radiusLargeDp = 28,
        radiusExtraLargeDp = 36,
    )
    AppTheme.AUTUMN -> ThemeSpec(
        nameFa = "پاییز",
        nameEn = "Autumn",
        moodFa = "کهربای سوخته و قرمز افرا",
        moodEn = "Burnt amber and maple red",
        primaryDark = 0xFFF59E0B,
        onPrimaryDark = 0xFF1C1917,
        primaryLight = 0xFFB45309,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFFEF4444,
        secondaryLight = 0xFFB91C1C,
        tertiaryDark = 0xFFEAB308,
        tertiaryLight = 0xFFA16207,
        primaryContainerDark = 0xFF3A1F0D,
        onPrimaryContainerDark = 0xFFFED7AA,
        primaryContainerLight = 0xFFFFEDD5,
        onPrimaryContainerLight = 0xFF7C2D12,
        darkBackground = 0xFF1C1410,
        darkSurface = 0xFF281E17,
        darkSurfaceVariant = 0xFF38291F,
        darkOnBackground = 0xFFF8F3EE,
        darkOnSurface = 0xFFF1E9E0,
        darkOnSurfaceVariant = 0xFFB3A294,
        lightBackground = 0xFFFBF6F0,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFF4EAE0,
        lightOnBackground = 0xFF1B1611,
        lightOnSurface = 0xFF241C15,
        lightOnSurfaceVariant = 0xFF6B5D4F,
        amoledSurface = 0xFF0F0B08,
        amoledSurfaceVariant = 0xFF181109,
        outlineDark = 0xFF4A382C,
        outlineLight = 0xFFD8C9B8,
        heroDark = listOf(0xFFF59E0B, 0xFFEF4444, 0xFF7C2D12),
        heroLight = listOf(0xFFFCD34D, 0xFFFCA5A5, 0xFFFFEDD5),
        radiusSmallDp = 12,
        radiusMediumDp = 16,
        radiusLargeDp = 20,
        radiusExtraLargeDp = 28,
    )
    AppTheme.WINTER -> ThemeSpec(
        nameFa = "زمستان",
        nameEn = "Winter",
        moodFa = "آبی یخی و برف",
        moodEn = "Ice blue and snow",
        primaryDark = 0xFF7DD3FC,
        onPrimaryDark = 0xFF0C2A3D,
        primaryLight = 0xFF0369A1,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFFA5F3FC,
        secondaryLight = 0xFF0E7490,
        tertiaryDark = 0xFFC4B5FD,
        tertiaryLight = 0xFF6D28D9,
        primaryContainerDark = 0xFF0F2E44,
        onPrimaryContainerDark = 0xFFE0F2FE,
        primaryContainerLight = 0xFFE0F2FE,
        onPrimaryContainerLight = 0xFF0C4A6E,
        darkBackground = 0xFF0E1622,
        darkSurface = 0xFF182230,
        darkSurfaceVariant = 0xFF243144,
        darkOnBackground = 0xFFF2F7FB,
        darkOnSurface = 0xFFE8F0F7,
        darkOnSurfaceVariant = 0xFF9FB2C3,
        lightBackground = 0xFFF4F8FB,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFE9F1F6,
        lightOnBackground = 0xFF121A22,
        lightOnSurface = 0xFF1A2632,
        lightOnSurfaceVariant = 0xFF5A6B7A,
        amoledSurface = 0xFF080C12,
        amoledSurfaceVariant = 0xFF10161F,
        outlineDark = 0xFF33465C,
        outlineLight = 0xFFC3D2DE,
        heroDark = listOf(0xFF7DD3FC, 0xFFC4B5FD, 0xFF0C4A6E),
        heroLight = listOf(0xFFBAE6FD, 0xFFDDD6FE, 0xFFE0F2FE),
        radiusSmallDp = 8,
        radiusMediumDp = 12,
        radiusLargeDp = 16,
        radiusExtraLargeDp = 24,
    )
    AppTheme.EMERALD -> ThemeSpec(
        nameFa = "زمردی",
        nameEn = "Emerald",
        moodFa = "زمرد عمیق و طلایی",
        moodEn = "Deep emerald and gold",
        primaryDark = 0xFF34D399,
        onPrimaryDark = 0xFF022C22,
        primaryLight = 0xFF047857,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFF6EE7B7,
        secondaryLight = 0xFF065F46,
        tertiaryDark = 0xFFF59E0B,
        tertiaryLight = 0xFF92400E,
        primaryContainerDark = 0xFF0B2E23,
        onPrimaryContainerDark = 0xFFA7F3D0,
        primaryContainerLight = 0xFFD1FAE5,
        onPrimaryContainerLight = 0xFF064E3B,
        darkBackground = 0xFF0C1712,
        darkSurface = 0xFF16241C,
        darkSurfaceVariant = 0xFF223329,
        darkOnBackground = 0xFFF2F8F4,
        darkOnSurface = 0xFFE8F2EA,
        darkOnSurfaceVariant = 0xFF9DB3A4,
        lightBackground = 0xFFF4FAF6,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFEAF3ED,
        lightOnBackground = 0xFF121A15,
        lightOnSurface = 0xFF1A241D,
        lightOnSurfaceVariant = 0xFF5A6B5E,
        amoledSurface = 0xFF080D0A,
        amoledSurfaceVariant = 0xFF101813,
        outlineDark = 0xFF33463A,
        outlineLight = 0xFFC2D2C6,
        heroDark = listOf(0xFF34D399, 0xFF6EE7B7, 0xFF064E3B),
        heroLight = listOf(0xFF6EE7B7, 0xFFA7F3D0, 0xFFD1FAE5),
        radiusSmallDp = 12,
        radiusMediumDp = 16,
        radiusLargeDp = 24,
        radiusExtraLargeDp = 32,
    )
    AppTheme.VIOLET -> ThemeSpec(
        nameFa = "بنفش",
        nameEn = "Violet",
        moodFa = "بنفش آوا — همان ظاهر آشنا",
        moodEn = "Ava's own violet — the familiar look",
        primaryDark = 0xFF7C5CFF,
        onPrimaryDark = 0xFFFFFFFF,
        primaryLight = 0xFF6D4DE0,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFF9D8BFF,
        secondaryLight = 0xFF5B21B6,
        tertiaryDark = 0xFF5EEAD4,
        tertiaryLight = 0xFF0F766E,
        primaryContainerDark = 0xFF2A2145,
        onPrimaryContainerDark = 0xFFE4DCFF,
        primaryContainerLight = 0xFFEDE9FE,
        onPrimaryContainerLight = 0xFF4C1D95,
        // The exact 0.6.0 surfaces (AvaSurfaces) — this theme must look
        // identical to 0.6.0 in every mode.
        darkBackground = 0xFF12121A,
        darkSurface = 0xFF1C1C28,
        darkSurfaceVariant = 0xFF282838,
        darkOnBackground = 0xFFF4F4F8,
        darkOnSurface = 0xFFE8E8F0,
        darkOnSurfaceVariant = 0xFF9C9CAD,
        lightBackground = 0xFFFAFAFC,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFF4F4F9,
        lightOnBackground = 0xFF17171C,
        lightOnSurface = 0xFF1E1E26,
        lightOnSurfaceVariant = 0xFF5F5F6E,
        amoledSurface = 0xFF0A0A0A,
        amoledSurfaceVariant = 0xFF161616,
        outlineDark = 0xFF3D3D52,
        outlineLight = 0xFFC9C9D6,
        heroDark = listOf(0xFF7C5CFF, 0xFF5EEAD4, 0xFF2A2145),
        heroLight = listOf(0xFFA78BFA, 0xFF99F6E4, 0xFFEDE9FE),
        radiusSmallDp = 12,
        radiusMediumDp = 16,
        radiusLargeDp = 20,
        radiusExtraLargeDp = 28,
    )
    AppTheme.OCEAN -> ThemeSpec(
        nameFa = "اقیانوس",
        nameEn = "Ocean",
        moodFa = "نیمه‌شب آبیِ عمیق",
        moodEn = "Deep midnight blue",
        primaryDark = 0xFF60A5FA,
        onPrimaryDark = 0xFF0A1F44,
        primaryLight = 0xFF1D4ED8,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFF22D3EE,
        secondaryLight = 0xFF0E7490,
        tertiaryDark = 0xFFA78BFA,
        tertiaryLight = 0xFF6D28D9,
        primaryContainerDark = 0xFF12294D,
        onPrimaryContainerDark = 0xFFDBEAFE,
        primaryContainerLight = 0xFFDBEAFE,
        onPrimaryContainerLight = 0xFF1E3A8A,
        darkBackground = 0xFF0A1128,
        darkSurface = 0xFF131D3A,
        darkSurfaceVariant = 0xFF1E2C4E,
        darkOnBackground = 0xFFF2F5FB,
        darkOnSurface = 0xFFE6ECF7,
        darkOnSurfaceVariant = 0xFF9DAECB,
        lightBackground = 0xFFF2F6FC,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFE8EEF7,
        lightOnBackground = 0xFF121826,
        lightOnSurface = 0xFF1A2236,
        lightOnSurfaceVariant = 0xFF5A6A85,
        amoledSurface = 0xFF070B18,
        amoledSurfaceVariant = 0xFF0E1526,
        outlineDark = 0xFF2E3D63,
        outlineLight = 0xFFC2CEDF,
        heroDark = listOf(0xFF60A5FA, 0xFF22D3EE, 0xFF1E3A8A),
        heroLight = listOf(0xFF93C5FD, 0xFF67E8F9, 0xFFDBEAFE),
        radiusSmallDp = 16,
        radiusMediumDp = 20,
        radiusLargeDp = 24,
        radiusExtraLargeDp = 32,
    )
    AppTheme.SUNSET -> ThemeSpec(
        nameFa = "غروب",
        nameEn = "Sunset",
        moodFa = "غروب کویر — نارنجی، رز، بنفش",
        moodEn = "Desert dusk — orange, rose, violet",
        primaryDark = 0xFFFB923C,
        onPrimaryDark = 0xFF2A1200,
        primaryLight = 0xFFC2410C,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFFF43F5E,
        secondaryLight = 0xFFBE123C,
        tertiaryDark = 0xFF8B5CF6,
        tertiaryLight = 0xFF6D28D9,
        primaryContainerDark = 0xFF3A1A10,
        onPrimaryContainerDark = 0xFFFFEDD5,
        primaryContainerLight = 0xFFFFEDD5,
        onPrimaryContainerLight = 0xFF7C2D12,
        darkBackground = 0xFF1E1210,
        darkSurface = 0xFF2A1A17,
        darkSurfaceVariant = 0xFF3A2620,
        darkOnBackground = 0xFFF9F2EE,
        darkOnSurface = 0xFFF2E7E0,
        darkOnSurfaceVariant = 0xFFB5A197,
        lightBackground = 0xFFFFF7F2,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFF9EAE2,
        lightOnBackground = 0xFF1D1512,
        lightOnSurface = 0xFF261C17,
        lightOnSurfaceVariant = 0xFF6E5B50,
        amoledSurface = 0xFF100A08,
        amoledSurfaceVariant = 0xFF191009,
        outlineDark = 0xFF4C352D,
        outlineLight = 0xFFDCC9BC,
        heroDark = listOf(0xFFFB923C, 0xFFF43F5E, 0xFF4C1D95),
        heroLight = listOf(0xFFFDBA74, 0xFFFDA4AF, 0xFFEDE9FE),
        radiusSmallDp = 20,
        radiusMediumDp = 24,
        radiusLargeDp = 28,
        radiusExtraLargeDp = 36,
    )
    AppTheme.GALAXY -> ThemeSpec(
        nameFa = "مشکی کهکشانی",
        nameEn = "Galactic Black",
        moodFa = "آسمان شب، ستاره‌ها و سحابی",
        moodEn = "Night sky, stars and nebula",
        // Starlight lavender primary: bright enough to read on deep
        // space black in dark mode, deep indigo in light mode.
        primaryDark = 0xFFC4B5FD,
        onPrimaryDark = 0xFF1E1B4B,
        primaryLight = 0xFF4338CA,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFFF9A8D4,
        secondaryLight = 0xFFA21CAF,
        tertiaryDark = 0xFF93C5FD,
        tertiaryLight = 0xFF1D4ED8,
        primaryContainerDark = 0xFF1E1B4B,
        onPrimaryContainerDark = 0xFFE0E7FF,
        primaryContainerLight = 0xFFE0E7FF,
        onPrimaryContainerLight = 0xFF1E1B4B,
        // Deep space black — darker than every other theme's dark
        // background, but indigo-tinted, never the pure black AMOLED uses.
        darkBackground = 0xFF05060F,
        darkSurface = 0xFF0D0F1E,
        darkSurfaceVariant = 0xFF171A2E,
        darkOnBackground = 0xFFF2F3FA,
        darkOnSurface = 0xFFE8EAF6,
        darkOnSurfaceVariant = 0xFFA8ADC9,
        // Light mode: pale starlight paper, same nebula accents.
        lightBackground = 0xFFF6F5FB,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFEDEAF6,
        lightOnBackground = 0xFF14121F,
        lightOnSurface = 0xFF1C1A2E,
        lightOnSurfaceVariant = 0xFF5B5872,
        amoledSurface = 0xFF05060F,
        amoledSurfaceVariant = 0xFF0B0D1C,
        outlineDark = 0xFF2E3352,
        outlineLight = 0xFFC6C2D8,
        // Nebula hero: violet → pink → deep indigo.
        heroDark = listOf(0xFF7C3AED, 0xFFDB2777, 0xFF1E1B4B),
        heroLight = listOf(0xFFA78BFA, 0xFFF9A8D4, 0xFFE0E7FF),
        radiusSmallDp = 16,
        radiusMediumDp = 20,
        radiusLargeDp = 24,
        radiusExtraLargeDp = 32,
    )
    AppTheme.IPHONE -> ThemeSpec(
        nameFa = "آیفون",
        nameEn = "iPhone",
        moodFa = "شیشه‌ای و مینیمال، به سبک اپل",
        moodEn = "Frosted glass, minimal — Apple's way",
        // iOS system blue, deepened just enough that white labels hold
        // WCAG >= 4.0 (dark) / >= 4.5 (light): pure #0A84FF / #007AFF
        // with white text score 3.65 / 4.02 and would fail the test.
        primaryDark = 0xFF0071E3,
        onPrimaryDark = 0xFFFFFFFF,
        primaryLight = 0xFF0066CC,
        onPrimaryLight = 0xFFFFFFFF,
        secondaryDark = 0xFF64D2FF,
        secondaryLight = 0xFF0A6E9E,
        tertiaryDark = 0xFFBF5AF2,
        tertiaryLight = 0xFFAF52DE,
        primaryContainerDark = 0xFF0A2A4A,
        onPrimaryContainerDark = 0xFFDBEAFE,
        primaryContainerLight = 0xFFE5F2FF,
        onPrimaryContainerLight = 0xFF00376B,
        // Apple's own dark surfaces: pure-black background, #1C1C1E /
        // #2C2C2E cards. Light: #F2F2F7 grouped background, white cards.
        darkBackground = 0xFF000000,
        darkSurface = 0xFF1C1C1E,
        darkSurfaceVariant = 0xFF2C2C2E,
        darkOnBackground = 0xFFFFFFFF,
        darkOnSurface = 0xFFFFFFFF,
        darkOnSurfaceVariant = 0xFFAEAEB2,
        lightBackground = 0xFFF2F2F7,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFE5E5EA,
        lightOnBackground = 0xFF000000,
        lightOnSurface = 0xFF000000,
        lightOnSurfaceVariant = 0xFF3C3C43,
        amoledSurface = 0xFF0A0A0C,
        amoledSurfaceVariant = 0xFF14141A,
        outlineDark = 0xFF38383A,
        outlineLight = 0xFFC7C7CC,
        heroDark = listOf(0xFF0071E3, 0xFF64D2FF, 0xFF0A2A4A),
        heroLight = listOf(0xFF0066CC, 0xFF64D2FF, 0xFFE5F2FF),
        // Apple's large continuous corners.
        radiusSmallDp = 16,
        radiusMediumDp = 20,
        radiusLargeDp = 28,
        radiusExtraLargeDp = 36,
        isGlass = true,
    )
}

/** The spec for this theme. */
val AppTheme.spec: ThemeSpec get() = appThemeSpec(this)

/** Safe parse of a stored theme name; unknown / null values fall back to VIOLET. */
fun resolveAppTheme(raw: String?): AppTheme =
    raw?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.VIOLET

// -- WCAG contrast helpers (pure; shared by the unit test) ----------------

/** Relative luminance of an ARGB colour (WCAG 2.x definition). */
fun argbLuminance(argb: Long): Double {
    fun channel(c: Long): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    val r = channel((argb shr 16) and 0xFF)
    val g = channel((argb shr 8) and 0xFF)
    val b = channel(argb and 0xFF)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG contrast ratio between two ARGB colours (1.0–21.0). */
fun contrastRatio(a: Long, b: Long): Double {
    val la = argbLuminance(a)
    val lb = argbLuminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}
