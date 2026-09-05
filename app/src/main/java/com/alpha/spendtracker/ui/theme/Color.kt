/**
 * Color system for Spendly — "Neon Counter": deep violet-tinted canvas, intentional electric accents.
 *
 * Identity is violet + mint (see the redesign brief §10). The three roles that get colour:
 *   Brand     — interaction: selected states, primary CTA, AI.            (violet)
 *   Semantic  — money in / money out / due-date urgency.                  (mint / coral / amber)
 *   Category  — chart slices and data labels ONLY, never UI chrome.       (Cat* / Purpose*)
 * Everything else is a violet-tinted neutral.
 *
 * Note on neutrals: the greys carry a violet cast (#14141F, not slate #111827) so surfaces sit in the
 * same family as the brand instead of reading as a separate blue design system.
 */
package com.alpha.spendtracker.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// 1. BRAND & CORE TOKENS
// ============================================================================

// Dark Theme Base Tokens (pitch-dark slate canvas matching modern clean dark mode)
val DarkBackground          = Color(0xFF0B0E14)   // pitch dark canvas
val DarkSurface             = Color(0xFF0E1118)   // soft dark surface
val DarkSurfaceElevated     = Color(0xFF12151E)   // subtle elevated container
val DarkBorder              = Color(0xFF181B26)   // soft hairline border

val DarkTextPrimary         = Color(0xFFFFFFFF)   // crisp white text
val DarkTextSecondary       = Color(0xFFA0A5B5)   // clean subtext
val DarkTextDisabled        = Color(0xFF505565)   // disabled / placeholder

val DarkBrand               = Color(0xFF9D8BFF)   // electric periwinkle violet — vivid but controlled
val DarkBrandContainer      = Color(0xFF1E1C38)   // smooth deep violet container

// Semantic Financial Indicators (Dark)
val DarkIncomeGreen         = Color(0xFFFFFFFF)   // crisp white for dues / income text
val DarkExpenseRed          = Color(0xFFFF6F7E)   // coral — Expense / Borrowed / Debit
val DarkWarningAmber        = Color(0xFFFBBF24)   // amber gold — due dates / pending / Notes accent
val DarkInfoBlue            = Color(0xFF6E9BFF)   // informational blue

val DarkSuccess             = DarkIncomeGreen
val DarkDanger              = DarkExpenseRed
val DarkWarning             = DarkWarningAmber

// Dark Mode Material 3 Mappings
val DarkPrimary             = DarkBrand
val DarkOnPrimary           = Color(0xFF1B1340)
val DarkPrimaryContainer    = DarkBrandContainer
val DarkOnPrimaryContainer  = Color(0xFFE6E1FA)

val DarkSecondary           = DarkBrand
val DarkOnSecondary         = Color(0xFF1B1340)
val DarkSecondaryContainer  = DarkBrandContainer
val DarkOnSecondaryContainer= Color(0xFFE6E1FA)

// Tertiary stays amber: it tints the Notes shortcut in the dashboard header.
val DarkTertiary            = DarkWarningAmber
val DarkOnTertiary          = Color(0xFF412D03)
val DarkTertiaryContainer   = Color(0xFF38290B)
val DarkOnTertiaryContainer = Color(0xFFFDEBC4)

val DarkOnBackground        = DarkTextPrimary
val DarkOnSurface           = DarkTextPrimary
val DarkSurfaceVariant      = DarkSurfaceElevated
val DarkOnSurfaceVariant    = DarkTextSecondary

val DarkOutline             = Color(0xFF181B26).copy(alpha = 0.3f)
val DarkOutlineVariant      = Color(0xFF141722)   // subtle hairline stroke
val DarkError               = DarkExpenseRed
val DarkOnError             = Color(0xFF49101A)
val DarkErrorContainer      = Color(0xFF3D1B22)
val DarkOnErrorContainer    = Color(0xFFFADADE)

// Dark Tonal Surface Ladder (pitch-dark canvas where low/container steps match canvas for box-free UI)
val DarkSurfaceDim          = Color(0xFF080A0E)
val DarkSurfaceBright       = Color(0xFF141722)
val DarkSurfaceContainerLowest  = Color(0xFF0B0E14)
val DarkSurfaceContainerLow     = Color(0xFF0B0E14)   // Matches canvas — NO background box around cards
val DarkSurfaceContainer        = Color(0xFF0B0E14)   // Matches canvas — flat seamless rows
val DarkSurfaceContainerHigh    = Color(0xFF0E1118)
val DarkSurfaceContainerHighest = Color(0xFF12151E)
val DarkInverseSurface      = Color(0xFFEDEAF5)
val DarkInverseOnSurface    = Color(0xFF302F42)
val DarkInversePrimary      = Color(0xFF5D45E8)   // matched to the light primary
val DarkScrim               = Color(0xFF000000)

// ----------------------------------------------------------------------------
// Light Theme Base Tokens (airy violet-white canvas)
// ----------------------------------------------------------------------------
val LightBackgroundLight      = Color(0xFFF2F1F9)   // page sits *behind* white cards
val LightSurfaceLight         = Color(0xFFFFFFFF)
val LightSurfaceElevatedLight = Color(0xFFE6E5F2)
val LightBorderLight          = Color(0xFFDCD9EC)   // near-invisible hairline on white
val LightBrandLight           = Color(0xFF5D45E8)   // saturated indigo-violet

// Semantic Financial Indicators (Light)
val LightIncomeGreen        = Color(0xFF5D45E8)   // brand violet
val LightExpenseRed         = Color(0xFFC32B3A)   // coral-red
val LightWarningAmber       = Color(0xFFD97706)   // amber gold

// Light Mode Material 3 Mappings
val LightPrimary              = LightBrandLight
val LightOnPrimary            = Color(0xFFFFFFFF)
val LightPrimaryContainer     = Color(0xFFE1DAFA)
val LightOnPrimaryContainer   = Color(0xFF211A4E)

val LightSecondary            = LightBrandLight
val LightOnSecondary          = Color(0xFFFFFFFF)
val LightSecondaryContainer   = Color(0xFFE1DAFA)
val LightOnSecondaryContainer = Color(0xFF211A4E)

// Tertiary stays amber: it tints the Notes shortcut in the dashboard header.
val LightTertiary             = LightWarningAmber
val LightOnTertiary           = Color(0xFFFFFFFF)
val LightTertiaryContainer    = Color(0xFFFDEBC4)
val LightOnTertiaryContainer  = Color(0xFF5A3A02)

val LightBackground           = LightBackgroundLight
val LightOnBackground         = Color(0xFF191A23)
val LightSurface              = LightSurfaceLight
val LightOnSurface            = Color(0xFF191A23)
val LightSurfaceVariant       = LightSurfaceElevatedLight
val LightOnSurfaceVariant     = Color(0xFF454654)

val LightOutline              = LightBorderLight
val LightOutlineVariant       = Color(0xFFC6C5D6)
val LightError                = LightExpenseRed
val LightOnError              = Color(0xFFFFFFFF)
val LightErrorContainer       = Color(0xFFF7DADE)
val LightOnErrorContainer     = Color(0xFF410008)

// Light Tonal Surface Ladder
val LightSurfaceDim           = Color(0xFFDDDAEC)
val LightSurfaceBright        = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest  = Color(0xFFFFFFFF)
val LightSurfaceContainerLow     = Color(0xFFFFFFFF)   // cards read as clean white
val LightSurfaceContainer        = Color(0xFFF8F7FC)
val LightSurfaceContainerHigh    = Color(0xFFF1EFF9)
val LightSurfaceContainerHighest = Color(0xFFE9E6F4)
val LightInverseSurface       = Color(0xFF2F2E3A)
val LightInverseOnSurface     = Color(0xFFF3F0FC)
val LightInversePrimary       = Color(0xFFC9BFFF)   // matched to the dark primary
val LightScrim                = Color(0xFF000000)

// ============================================================================
// 2. BRAND GRADIENT & ACCENTS (hero card, badges)
// ============================================================================
// Indigo -> violet-magenta -> rose ramp. All stops are mid-dark tones, so white text and icons
// drawn on the hero stay high-contrast in both themes.
val BrandGradientStart = Color(0xFF6E5BE8)   // electric indigo
val BrandGradientMid   = Color(0xFF9B52D6)   // violet-magenta
val BrandGradientEnd   = Color(0xFFE0608F)   // rose-pink
val BrandAccentMint    = Color(0xFF3FE0C4)   // matches the vivid dark secondary

// ============================================================================
// 3. HARMONIZED CATEGORY & PURPOSE PALETTES  (charts & data labels only)
// ============================================================================
// Chroma and lightness are held roughly constant across hues so no slice shouts, and the money
// semantics are preserved: Lending borrows the "money in" teal, Borrowing the "money out" rose.
val CatLight_UPI        = Color(0xFF4F7BE8)   // vivid blue
val CatLight_QuickComm  = Color(0xFFED8744)   // vivid amber/ochre
val CatLight_Ecommerce  = Color(0xFFD05F97)   // vivid rose
val CatLight_Banking    = Color(0xFF8472EC)   // vivid violet
val CatLight_Lending    = Color(0xFF2FA98C)   // vivid teal-green
val CatLight_Other      = Color(0xFF868B98)   // neutral grey

val CatDark_UPI         = Color(0xFF5C95F0)
val CatDark_QuickComm   = Color(0xFFF08A3E)
val CatDark_Ecommerce   = Color(0xFFE86CA5)
val CatDark_Banking     = Color(0xFF9D7CF0)
val CatDark_Lending     = Color(0xFF2ED4A6)
val CatDark_Other       = Color(0xFF8B909C)

val PurposeLight_Food        = Color(0xFF2F9E5E)   // green
val PurposeLight_Shopping    = Color(0xFFE08A2E)   // amber
val PurposeLight_Lending     = Color(0xFF0C8174)   // teal — "money in"
val PurposeLight_Borrowing   = Color(0xFFD14079)   // rose — "money out"
val PurposeLight_CreditCard  = Color(0xFF7A5AE0)   // violet
val PurposeLight_Utilities   = Color(0xFF3E6FD9)   // slate blue
val PurposeLight_Travel      = Color(0xFF0E8FA8)   // cyan
val PurposeLight_Leisure     = Color(0xFFA94BC4)   // magenta-purple
val PurposeLight_Health      = Color(0xFFD2453C)   // red
val PurposeLight_Other       = Color(0xFF7C8290)   // neutral grey

val PurposeDark_Food         = Color(0xFF4FD188)
val PurposeDark_Shopping     = Color(0xFFF2A44F)
val PurposeDark_Lending      = Color(0xFF3FE0C4)   // matches BrandAccentMint
val PurposeDark_Borrowing    = Color(0xFFFF93A6)
val PurposeDark_CreditCard   = Color(0xFFA48BFF)
val PurposeDark_Utilities    = Color(0xFF6E9BFF)
val PurposeDark_Travel       = Color(0xFF45BDD6)
val PurposeDark_Leisure      = Color(0xFFD07FE8)
val PurposeDark_Health       = Color(0xFFFF7A72)
val PurposeDark_Other        = Color(0xFF939AA8)

// ============================================================================
// 4. ON-GRADIENT ACCENTS
// ============================================================================
// The hero paints its own indigo->rose gradient, so colorScheme.error / .secondary don't have
// enough contrast against it. These are the gradient-safe stand-ins for the same semantics.
val OnGradientMoneyUp   = Color(0xFFFFB4BC)   // soft coral — spending increased
val OnGradientMoneyDown = BrandAccentMint     // mint — spending decreased
