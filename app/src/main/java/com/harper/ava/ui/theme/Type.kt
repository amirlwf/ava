package com.harper.ava.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.harper.ava.R

/**
 * Two typefaces, both SIL OFL 1.1 (free for commercial use and app
 * embedding; licences in `assets/OFL.txt` and `assets/OFL-lalezar.txt`):
 *
 * - Vazirmatn is the body / UI face: every body, label and title style
 *   below, so fa/en render with one consistent voice. Weights shipped:
 *   Regular 400, Medium 500, SemiBold 600, Bold 700.
 * - Lalezar (لاله‌زار, single weight 400, display font) is the display
 *   face: displayLarge / headlineMedium / headlineSmall — big titles,
 *   the app name and section headers, where the character lives.
 *   Lalezar covers Latin fully (52/52) so Latin headlines need no
 *   fallback; being single-weight, headline hierarchy comes from size,
 *   not weight — all three display styles use FontWeight.Normal.
 *
 * Manshoor was deliberately NOT bundled: it is a paid commercial font
 * and the user has not bought it. Dana is out as well. The Manshoor
 * package stays parked untouched in `fonts/manshoor/` for a possible
 * later decision.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

val Lalezar = FontFamily(
    Font(R.font.lalezar_regular, FontWeight.Normal),
)

val AvaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Lalezar,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Lalezar,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Lalezar,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
