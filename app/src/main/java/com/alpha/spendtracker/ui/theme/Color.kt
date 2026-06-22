/**
 * Color palette definitions for light and dark themes.
 */
package com.alpha.spendtracker.ui.theme

import androidx.compose.ui.graphics.Color

// ----- Dark scheme (primary target — modern fintech, deep canvas, electric accents) -----
val DarkPrimary = Color(0xFF9F8BFF)
val DarkOnPrimary = Color(0xFF14072B)
val DarkPrimaryContainer = Color(0xFF3B2A82)
val DarkOnPrimaryContainer = Color(0xFFE7DEFF)

val DarkSecondary = Color(0xFF55E6CF)
val DarkOnSecondary = Color(0xFF003830)
val DarkSecondaryContainer = Color(0xFF0E5045)
val DarkOnSecondaryContainer = Color(0xFFB4F5E6)

val DarkTertiary = Color(0xFFFF9DAE)
val DarkOnTertiary = Color(0xFF49000F)
val DarkTertiaryContainer = Color(0xFF7A1F2E)
val DarkOnTertiaryContainer = Color(0xFFFFD9DF)

val DarkBackground = Color(0xFF0A0A12)
val DarkOnBackground = Color(0xFFEDEAF5)
val DarkSurface = Color(0xFF14141F)
val DarkOnSurface = Color(0xFFEDEAF5)
val DarkSurfaceVariant = Color(0xFF1F1F2D)
val DarkOnSurfaceVariant = Color(0xFFBFBBD0)

val DarkOutline = Color(0xFF45455C)
val DarkOutlineVariant = Color(0xFF2A2A40)
val DarkError = Color(0xFFFF6B7A)
val DarkOnError = Color(0xFF50000A)
val DarkErrorContainer = Color(0xFF7A1A26)
val DarkOnErrorContainer = Color(0xFFFFDADE)

// Tonal surface ladder — gives elevated cards real depth instead of flat grey defaults.
val DarkSurfaceDim = Color(0xFF0A0A12)
val DarkSurfaceBright = Color(0xFF302F42)
val DarkSurfaceContainerLowest = Color(0xFF05050B)
val DarkSurfaceContainerLow = Color(0xFF13131E)
val DarkSurfaceContainer = Color(0xFF181825)
val DarkSurfaceContainerHigh = Color(0xFF222230)
val DarkSurfaceContainerHighest = Color(0xFF2C2C3C)
val DarkInverseSurface = Color(0xFFEDEAF5)
val DarkInverseOnSurface = Color(0xFF302F42)
val DarkInversePrimary = Color(0xFF5B47E5)
val DarkScrim = Color(0xFF000000)

// ----- Light scheme (paired companion — airy lavender wash, saturated accents) -----
val LightPrimary = Color(0xFF5B47E5)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE3DDFF) // deepened from #EEF0FF — more lavender presence
val LightOnPrimaryContainer = Color(0xFF180F4E)

val LightSecondary = Color(0xFF006A60)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFBFF1E7) // deepened from #E0FAF5 — real teal tint
val LightOnSecondaryContainer = Color(0xFF00201C)

val LightTertiary = Color(0xFFE91E63)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFD9E1) // slightly richer rose
val LightOnTertiaryContainer = Color(0xFF54001A)

val LightBackground = Color(0xFFF7F7FE)
val LightOnBackground = Color(0xFF191A23)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF191A23)
val LightSurfaceVariant = Color(0xFFE6E5F2)
val LightOnSurfaceVariant = Color(0xFF454654)

val LightOutline = Color(0xFF767687)
val LightOutlineVariant = Color(0xFFC6C5D6) // stronger hairline than #C4C6D0
val LightError = Color(0xFFD3263A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFD9DE)
val LightOnErrorContainer = Color(0xFF410008)

// Tonal surface ladder — restores M3 depth/elevation in light mode (was falling back to defaults).
val LightSurfaceDim = Color(0xFFDED9EE)
val LightSurfaceBright = Color(0xFFFCFBFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF4F2FC)
val LightSurfaceContainer = Color(0xFFEEEBF8)
val LightSurfaceContainerHigh = Color(0xFFE8E4F4)
val LightSurfaceContainerHighest = Color(0xFFE2DEF0)
val LightInverseSurface = Color(0xFF2F2E3A)
val LightInverseOnSurface = Color(0xFFF3F0FC)
val LightInversePrimary = Color(0xFFC9BEFF)
val LightScrim = Color(0xFF000000)

// ----- Brand gradient colors (shared, used in hero card & accents) -----
val BrandGradientStart = Color(0xFF6E5BFF)
val BrandGradientMid = Color(0xFF9F4DFF)
val BrandGradientEnd = Color(0xFFFF5E8A)
val BrandAccentMint = Color(0xFF55E6CF)

// ----- Harmonized Category Colors -----
// Light Mode (Professional Pastel)
val CatLight_UPI = Color(0xFF4A78F0)
val CatLight_QuickComm = Color(0xFFF18C4E)
val CatLight_Ecommerce = Color(0xFFD65B9B)
val CatLight_Banking = Color(0xFF8B76F1)
val CatLight_Lending = Color(0xFF3DAE8F)
val CatLight_Other = Color(0xFF828896)

// Dark Mode (Electric/Neon)
val CatDark_UPI = Color(0xFF3B82F6)
val CatDark_QuickComm = Color(0xFFF97316)
val CatDark_Ecommerce = Color(0xFFEC4899)
val CatDark_Banking = Color(0xFF8B5CF6)
val CatDark_Lending = Color(0xFF10B981)
val CatDark_Other = Color(0xFF6B7280)
