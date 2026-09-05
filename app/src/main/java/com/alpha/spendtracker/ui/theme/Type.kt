/**
 * Typography configuration for the application's theme.
 *
 * Two bundled variable fonts give the app a deliberate voice:
 *  - Space Grotesk — display / headlines / titles / currency figures (geometric, editorial fintech).
 *  - Inter         — body / labels (highly legible UI text).
 */
package com.alpha.spendtracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.R

@OptIn(ExperimentalTextApi::class)
private fun interFont(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

@OptIn(ExperimentalTextApi::class)
private fun groteskFont(weight: Int) = Font(
    R.font.space_grotesk_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Inter = FontFamily(
    interFont(400), interFont(500), interFont(600), interFont(700)
)

val Grotesk = FontFamily(
    groteskFont(400), groteskFont(500), groteskFont(600), groteskFont(700)
)

fun TextStyle.asMoney(): TextStyle = copy(
    fontFamily = Grotesk,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp,
)

val Typography = Typography(
    // Display: 32 / Bold
    displaySmall = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp
    ),
    // LargeAmount: 28 / Bold
    headlineMedium = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp
    ),
    // PageTitle: 24 / SemiBold
    headlineSmall = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp
    ),
    // Section: 18 / SemiBold
    titleLarge = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    // Body: 16 / Regular
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp
    ),
    // Secondary: 14 / Regular
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp
    ),
    // Caption: 12 / Medium
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp
    ),
)
