/**
 * Color palette definitions for light and dark themes.
 */
package com.alpha.spendtracker.ui.theme

import androidx.compose.ui.graphics.Color

// ----- Dark scheme (primary target — "Neon Counter": deep canvas, intentional electric accents) -----
// Recalibrated palette. The original set was garish; the first correction over-muted it into a pale,
// flat look. This middle ground restores confident, attractive saturation while keeping the accents
// purposeful: secondary = "money in" mint, error = "money out" coral, primary = electric violet.
val DarkPrimary = Color(0xFF9D8BFF)        // electric periwinkle violet — vivid but controlled
val DarkOnPrimary = Color(0xFF1B1340)
val DarkPrimaryContainer = Color(0xFF3F3580)
val DarkOnPrimaryContainer = Color(0xFFE6E1FA)

val DarkSecondary = Color(0xFF3FE0C4)      // vivid mint — semantic "money in / owed to you"
val DarkOnSecondary = Color(0xFF003730)
val DarkSecondaryContainer = Color(0xFF134E45)
val DarkOnSecondaryContainer = Color(0xFFBFEAE0)

val DarkTertiary = Color(0xFFFF93A6)       // bright rose accent
val DarkOnTertiary = Color(0xFF441921)
val DarkTertiaryContainer = Color(0xFF7A3A43)
val DarkOnTertiaryContainer = Color(0xFFFAD9DD)

val DarkBackground = Color(0xFF0A0A12)
val DarkOnBackground = Color(0xFFEDEAF5)
val DarkSurface = Color(0xFF14141F)
val DarkOnSurface = Color(0xFFEDEAF5)
val DarkSurfaceVariant = Color(0xFF1F1F2D)
val DarkOnSurfaceVariant = Color(0xFFBFBBD0)

val DarkOutline = Color(0xFF45455C)
val DarkOutlineVariant = Color(0xFF2A2A40)
val DarkError = Color(0xFFFF6F7E)          // coral — semantic "money out / over budget"
val DarkOnError = Color(0xFF49101A)
val DarkErrorContainer = Color(0xFF7A2630)
val DarkOnErrorContainer = Color(0xFFFADADE)

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
val DarkInversePrimary = Color(0xFF5D45E8)  // matched to the light primary
val DarkScrim = Color(0xFF000000)

// ----- Light scheme (paired companion — airy canvas, vivid intentional accents) -----
// Same recalibration: accents pushed back up from the over-pale pass so the theme has presence on
// the near-white canvas, while staying dark enough for accessible (AA) contrast.
val LightPrimary = Color(0xFF5D45E8)       // saturated indigo-violet
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE1DAFA)
val LightOnPrimaryContainer = Color(0xFF211A4E)

val LightSecondary = Color(0xFF0C8174)     // vivid teal — semantic "money in / owed to you"
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFB8E7DE)
val LightOnSecondaryContainer = Color(0xFF002620)

val LightTertiary = Color(0xFFD14079)      // rich rose — attractive without being hot-pink garish
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFBD7E2)
val LightOnTertiaryContainer = Color(0xFF451321)

val LightBackground = Color(0xFFF7F7FE)
val LightOnBackground = Color(0xFF191A23)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF191A23)
val LightSurfaceVariant = Color(0xFFE6E5F2)
val LightOnSurfaceVariant = Color(0xFF454654)

val LightOutline = Color(0xFF767687)
val LightOutlineVariant = Color(0xFFC6C5D6)
val LightError = Color(0xFFC32B3A)         // coral-red — semantic "money out / over budget"
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF7DADE)
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
val LightInversePrimary = Color(0xFFC9BFFF)  // matched to the dark primary
val LightScrim = Color(0xFF000000)

// ----- Brand gradient colors (shared, used in hero card & accents) -----
// Restored to a livelier indigo -> violet-magenta -> rose ramp (the over-muted version looked washed
// out). Still cohesive and "restrained hero" per the redesign direction — vivid, not neon-garish.
// White text/icons on this gradient remain high-contrast (all stops are mid-dark tones).
val BrandGradientStart = Color(0xFF6E5BE8)   // electric indigo
val BrandGradientMid = Color(0xFF9B52D6)     // violet-magenta
val BrandGradientEnd = Color(0xFFE0608F)     // rose-pink
val BrandAccentMint = Color(0xFF3FE0C4)      // matches the vivid dark secondary

// ----- Harmonized Category Colors (chart slices & legends) -----
// A coordinated, vivid set — saturation pushed back up from the over-muted pass for chart "pop",
// while keeping the hue order (blue / amber / rose / violet / teal / grey) so categories keep their
// identity and slices stay distinguishable from one another.
//
// Light Mode (vivid, mid-dark — readable on the near-white chart surface)
val CatLight_UPI = Color(0xFF4F7BE8)       // vivid blue
val CatLight_QuickComm = Color(0xFFED8744)  // vivid amber/ochre
val CatLight_Ecommerce = Color(0xFFD05F97)  // vivid rose
val CatLight_Banking = Color(0xFF8472EC)    // vivid violet
val CatLight_Lending = Color(0xFF2FA98C)    // vivid teal-green
val CatLight_Other = Color(0xFF868B98)      // neutral grey

// Dark Mode (vivid, lifted tones — readable on the deep canvas)
val CatDark_UPI = Color(0xFF5C95F0)        // vivid blue
val CatDark_QuickComm = Color(0xFFF08A3E)   // vivid amber
val CatDark_Ecommerce = Color(0xFFE86CA5)   // vivid rose
val CatDark_Banking = Color(0xFF9D7CF0)     // vivid violet
val CatDark_Lending = Color(0xFF2ED4A6)     // vivid teal-green
val CatDark_Other = Color(0xFF8B909C)       // neutral grey

// ----- Harmonized Purpose Colors (donut slices & legends on the "Purpose" tab) -----
// Replaces the stock 2014 Material hexes that used to sit inline in SpendingCharts — those were
// wired straight to the primary Material hues, so they read as a different design system than the
// Cat* set next to them and their lightness jumped around (#FBC02D next to #5D4037).
//
// This set holds chroma and lightness roughly constant across all ten hues so no slice shouts, and
// keeps the app's money semantics: Lending borrows the "money in" teal/mint, Borrowing the
// "money out" rose. Ordered by hue so adjacent slices stay distinguishable.
val PurposeLight_Food = Color(0xFF2F9E5E)          // green
val PurposeLight_Shopping = Color(0xFFE08A2E)      // amber
val PurposeLight_Lending = Color(0xFF0C8174)       // teal — "money in"
val PurposeLight_Borrowing = Color(0xFFD14079)     // rose — "money out"
val PurposeLight_CreditCard = Color(0xFF7A5AE0)    // violet
val PurposeLight_Utilities = Color(0xFF3E6FD9)     // slate blue
val PurposeLight_Travel = Color(0xFF0E8FA8)        // cyan
val PurposeLight_Leisure = Color(0xFFA94BC4)       // magenta-purple
val PurposeLight_Health = Color(0xFFD2453C)        // red
val PurposeLight_Other = Color(0xFF7C8290)         // neutral grey

val PurposeDark_Food = Color(0xFF4FD188)
val PurposeDark_Shopping = Color(0xFFF2A44F)
val PurposeDark_Lending = Color(0xFF3FE0C4)        // matches BrandAccentMint
val PurposeDark_Borrowing = Color(0xFFFF93A6)      // matches DarkTertiary
val PurposeDark_CreditCard = Color(0xFFA48BFF)
val PurposeDark_Utilities = Color(0xFF6E9BFF)
val PurposeDark_Travel = Color(0xFF45BDD6)
val PurposeDark_Leisure = Color(0xFFD07FE8)
val PurposeDark_Health = Color(0xFFFF7A72)
val PurposeDark_Other = Color(0xFF939AA8)

// ----- On-gradient accents -----
// The hero card paints its own indigo->rose gradient, so colorScheme.error/secondary don't have
// enough contrast against it. These two are the gradient-safe stand-ins for the same semantics.
val OnGradientMoneyUp = Color(0xFFFFB4BC)     // soft coral — spending increased
val OnGradientMoneyDown = BrandAccentMint     // mint — spending decreased
