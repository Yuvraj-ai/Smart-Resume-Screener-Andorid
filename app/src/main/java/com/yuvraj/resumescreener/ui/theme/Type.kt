package com.yuvraj.resumescreener.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yuvraj.resumescreener.R

/**
 * Inter for UI, JetBrains Mono wherever a number carries meaning.
 *
 * Both are variable fonts, so weights come from the `wght` axis rather than
 * separate files. Compose only applies that axis when `variationSettings` is
 * supplied, which is why these cannot use the plain FontWeight overload.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int): FontFamily = FontFamily(
    Font(resId, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(resId, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(resId, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(resId, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(resId, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

val InterFamily = variableFamily(R.font.inter)

/** Tabular figures: every score and statistic uses this so digits align in columns. */
val TabularFamily = variableFamily(R.font.jetbrains_mono)

private fun inter(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val AppTypography = Typography(
    displayLarge = inter(44, 48, FontWeight.Bold, -0.02),
    displayMedium = inter(34, 40, FontWeight.Bold, -0.02),
    displaySmall = inter(28, 34, FontWeight.SemiBold, -0.01),
    headlineMedium = inter(22, 28, FontWeight.SemiBold, -0.01),
    headlineSmall = inter(18, 24, FontWeight.SemiBold),
    titleLarge = inter(16, 22, FontWeight.SemiBold),
    titleMedium = inter(14, 20, FontWeight.Medium),
    bodyLarge = inter(15, 23, FontWeight.Normal),
    bodyMedium = inter(14, 21, FontWeight.Normal),
    bodySmall = inter(12, 18, FontWeight.Normal),
    labelLarge = inter(14, 18, FontWeight.SemiBold),
    labelMedium = inter(12, 16, FontWeight.Medium, 0.02),
    /** Small all-caps section labels, used throughout the design as eyebrow text. */
    labelSmall = inter(11, 14, FontWeight.SemiBold, 0.09),
)
