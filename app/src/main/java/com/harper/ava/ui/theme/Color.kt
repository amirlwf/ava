package com.harper.ava.ui.theme

import androidx.compose.ui.graphics.Color
import com.harper.ava.core.util.deriveAccentColors
import com.harper.ava.data.preferences.Accent

/** One accent palette: primary + supporting tones used across every theme mode. */
data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

/**
 * Builds a full palette from one user-picked ARGB color. The derivation
 * itself is pure and unit-tested ([deriveAccentColors]); this is only
 * the Compose Color conversion.
 */
fun customAccentPalette(argb: Int): AccentPalette {
    val derived = deriveAccentColors(argb)
    return AccentPalette(
        primary = Color(derived.primary),
        onPrimary = Color(derived.onPrimary),
        secondary = Color(derived.secondary),
        tertiary = Color(derived.tertiary),
        primaryContainer = Color(derived.primaryContainer),
        onPrimaryContainer = Color(derived.onPrimaryContainer),
    )
}

fun accentPalette(accent: Accent): AccentPalette = when (accent) {
    Accent.VIOLET -> AccentPalette(
        primary = Color(0xFF7C5CFF),
        onPrimary = Color.White,
        secondary = Color(0xFF9D8BFF),
        tertiary = Color(0xFF5EEAD4),
        primaryContainer = Color(0xFF2A2145),
        onPrimaryContainer = Color(0xFFE4DCFF),
    )
    Accent.ELECTRIC_BLUE -> AccentPalette(
        primary = Color(0xFF2E7CF6),
        onPrimary = Color.White,
        secondary = Color(0xFF7FB3FF),
        tertiary = Color(0xFF5EEAD4),
        primaryContainer = Color(0xFF16273F),
        onPrimaryContainer = Color(0xFFD6E6FF),
    )
    Accent.EMERALD -> AccentPalette(
        primary = Color(0xFF10B981),
        onPrimary = Color.White,
        secondary = Color(0xFF6EE7B7),
        tertiary = Color(0xFF7C5CFF),
        primaryContainer = Color(0xFF0F2E25),
        onPrimaryContainer = Color(0xFFD1FAE5),
    )
    Accent.CRIMSON -> AccentPalette(
        primary = Color(0xFFE11D48),
        onPrimary = Color.White,
        secondary = Color(0xFFFB7185),
        tertiary = Color(0xFFF59E0B),
        primaryContainer = Color(0xFF3B1220),
        onPrimaryContainer = Color(0xFFFFE4E6),
    )
    Accent.AMBER -> AccentPalette(
        primary = Color(0xFFF59E0B),
        onPrimary = Color(0xFF1C1917),
        secondary = Color(0xFFFCD34D),
        tertiary = Color(0xFF10B981),
        primaryContainer = Color(0xFF3A2A0B),
        onPrimaryContainer = Color(0xFFFEF3C7),
    )
    Accent.ROSE -> AccentPalette(
        primary = Color(0xFFF43F5E),
        onPrimary = Color.White,
        secondary = Color(0xFFFDA4AF),
        tertiary = Color(0xFF7C5CFF),
        primaryContainer = Color(0xFF3B1520),
        onPrimaryContainer = Color(0xFFFFE4E6),
    )
    Accent.MONOCHROME -> AccentPalette(
        primary = Color(0xFFE4E4E7),
        onPrimary = Color(0xFF18181B),
        secondary = Color(0xFFA1A1AA),
        tertiary = Color(0xFF71717A),
        primaryContainer = Color(0xFF27272A),
        onPrimaryContainer = Color(0xFFF4F4F5),
    )
}

/** Surfaces for the three dark-ish appearances and light mode. */
object AvaSurfaces {
    // Dark (charcoal, not pure black — keeps depth and elevation visible)
    val darkBackground = Color(0xFF12121A)
    val darkSurface = Color(0xFF1C1C28)
    val darkSurfaceVariant = Color(0xFF262636)
    val darkOnBackground = Color(0xFFF4F4F8)
    val darkOnSurface = Color(0xFFE8E8F0)
    val darkOnSurfaceVariant = Color(0xFF9C9CAD)

    // AMOLED (true black — pixels off)
    val amoledBackground = Color.Black
    val amoledSurface = Color(0xFF0A0A0A)
    val amoledSurfaceVariant = Color(0xFF161616)

    // Light
    val lightBackground = Color(0xFFFAFAFC)
    val lightSurface = Color.White
    val lightSurfaceVariant = Color(0xFFF0F0F5)
    val lightOnBackground = Color(0xFF17171C)
    val lightOnSurface = Color(0xFF1E1E26)
    val lightOnSurfaceVariant = Color(0xFF5F5F6E)
}
