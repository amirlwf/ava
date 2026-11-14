package com.harper.ava.core.util

import kotlin.math.roundToInt

/**
 * Pure, Android-free settings logic. Everything here is unit-tested with
 * plain JUnit (no Robolectric, no device) — see SettingsLogicTest.
 *
 * The color math works on raw ARGB ints on purpose: the Compose layer
 * ([customAccentPalette] in Color.kt) is a thin conversion on top of
 * [deriveAccentColors], so the derivation itself stays testable here.
 */

// ---------------------------------------------------------------------------
// Playback
// ---------------------------------------------------------------------------

/** Speeds offered in Playback Settings (and the Now Playing speed control). */
val SPEED_OPTIONS: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** Clamps a stored or requested speed into the supported 0.5x–2.0x range. */
fun clampPlaybackSpeed(speed: Float): Float =
    if (speed.isNaN()) 1.0f else speed.coerceIn(0.5f, 2.0f)

// ---------------------------------------------------------------------------
// File / library sizes (Storage screen)
// ---------------------------------------------------------------------------

/** Human-readable byte count: 512 B, 1.5 KB, 23.4 MB, 1.2 GB. */
fun formatFileSize(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    if (b < 1_024L) return "$b B"
    val kb = b / 1_024.0
    if (kb < 1_024.0) return "%.1f KB".format(kb)
    val mb = kb / 1_024.0
    if (mb < 1_024.0) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1_024.0)
}

// ---------------------------------------------------------------------------
// Custom accent color
// ---------------------------------------------------------------------------

/** The six tones a full accent palette needs, as raw ARGB ints. */
data class DerivedAccentColors(
    val primary: Int,
    val onPrimary: Int,
    val secondary: Int,
    val tertiary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
)

private fun alpha(c: Int): Int = (c ushr 24) and 0xFF
private fun red(c: Int): Int = (c ushr 16) and 0xFF
private fun green(c: Int): Int = (c ushr 8) and 0xFF
private fun blue(c: Int): Int = c and 0xFF

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or
        (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255))

/** Mixes [color] towards white by [amount] (0 = unchanged, 1 = white). */
fun lightenArgb(color: Int, amount: Float): Int {
    val a = amount.coerceIn(0f, 1f)
    return argb(
        alpha(color),
        (red(color) + (255 - red(color)) * a).roundToInt(),
        (green(color) + (255 - green(color)) * a).roundToInt(),
        (blue(color) + (255 - blue(color)) * a).roundToInt(),
    )
}

/** Mixes [color] towards black by [amount] (0 = unchanged, 1 = black). */
fun darkenArgb(color: Int, amount: Float): Int {
    val a = amount.coerceIn(0f, 1f)
    return argb(
        alpha(color),
        (red(color) * (1f - a)).roundToInt(),
        (green(color) * (1f - a)).roundToInt(),
        (blue(color) * (1f - a)).roundToInt(),
    )
}

/** Relative luminance (0 = black, 1 = white) — the WCAG definition. */
fun luminanceArgb(color: Int): Float {
    fun channel(v: Int): Float {
        val c = v / 255f
        return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * channel(red(color)) + 0.7152f * channel(green(color)) + 0.0722f * channel(blue(color))
}

/** True when [color] is light enough that dark text reads better on it. */
fun isLightColor(color: Int): Boolean = luminanceArgb(color) > 0.5f

/**
 * Derives a complete accent palette from one user-picked color.
 * The picked color is kept verbatim as [DerivedAccentColors.primary];
 * the other tones are derived from it so the palette always stays
 * harmonious and the on-primary text always has contrast:
 * light primaries get near-black text, dark primaries get white text.
 */
fun deriveAccentColors(argbColor: Int): DerivedAccentColors {
    val primary = argb(0xFF, red(argbColor), green(argbColor), blue(argbColor))
    val light = isLightColor(primary)
    return DerivedAccentColors(
        primary = primary,
        onPrimary = if (light) argb(0xFF, 0x1C, 0x19, 0x17) else argb(0xFF, 0xFF, 0xFF, 0xFF),
        secondary = lightenArgb(primary, 0.35f),
        tertiary = lightenArgb(primary, 0.6f),
        // Dark container (matches the hand-built palettes, which are dark
        // in both schemes) with a light tint of the primary on top of it.
        primaryContainer = darkenArgb(primary, 0.72f),
        onPrimaryContainer = lightenArgb(primary, 0.75f),
    )
}

// ---------------------------------------------------------------------------
// HSV color picker (pure math — no android.graphics dependency)
// ---------------------------------------------------------------------------

/** HSV (hue 0–360, saturation/value 0–1) to opaque ARGB. */
fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return argb(0xFF, ((r + m) * 255f).roundToInt(), ((g + m) * 255f).roundToInt(), ((b + m) * 255f).roundToInt())
}

/** Opaque ARGB to HSV: Triple(hue 0–360, saturation 0–1, value 0–1). */
fun argbToHsv(argbColor: Int): Triple<Float, Float, Float> {
    val r = red(argbColor) / 255f
    val g = green(argbColor) / 255f
    val b = blue(argbColor) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> (60f * ((g - b) / delta) + 360f) % 360f
        max == g -> 60f * ((b - r) / delta) + 120f
        else -> 60f * ((r - g) / delta) + 240f
    }
    val saturation = if (max == 0f) 0f else delta / max
    return Triple(hue, saturation, max)
}

/** Hex string ("#7C5CFF") for a color, for the picker preview. */
fun argbToHex(argbColor: Int): String =
    "#%02X%02X%02X".format(red(argbColor), green(argbColor), blue(argbColor))
