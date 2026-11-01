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

// Dark Theme Base Tokens (greyish canvas instead of pure black/white)
val DarkBackground          = Color(0xFF121212)   // soft dark grey neutral canvas
val DarkSurface             = Color(0xFF1E1E1E)   // slightly elevated grey surface
val DarkSurfaceElevated     = Color(0xFF2C2C2C)   // clean elevated container
val DarkBorder              = Color(0xFF383838)   // subtle neutral border

val DarkTextPrimary         = Color(0xFFE3E3E3)   // off-white/light grey text
val DarkTextSecondary       = Color(0xFFA0A0A0)   // medium grey subtext
val DarkTextDisabled        = Color(0xFF6E6E6E)   // disabled / placeholder

val DarkBrand               = Color(0xFF8AB4F8)   // light blue primary CTA & active accent
val DarkBrandContainer      = Color(0xFF1F2B3E)   // subtle blue-grey container

// Semantic Financial Indicators (Dark)
val DarkIncomeGreen         = Color(0xFF81C995)   // soft green for dues / income text
val DarkExpenseRed          = Color(0xFFEF9A9A)   // light red — Expense / Borrowed / Debit
val DarkWarningAmber        = Color(0xFFFFCC80)   // light amber — due dates / pending / Notes accent
val DarkInfoBlue            = Color(0xFF8AB4F8)   // informational light blue

val DarkSuccess             = DarkIncomeGreen
val DarkDanger              = DarkExpenseRed
val DarkWarning             = DarkWarningAmber

// Dark Mode Material 3 Mappings
val DarkPrimary             = DarkBrand
val DarkOnPrimary           = Color(0xFF121212)
val DarkPrimaryContainer    = DarkBrandContainer
val DarkOnPrimaryContainer  = Color(0xFFD2E3FC)

val DarkSecondary           = Color(0xFFB0BEC5)   // greyish for secondary elements / Lend
val DarkOnSecondary         = Color(0xFF121212)
val DarkSecondaryContainer  = Color(0xFF37474F)
val DarkOnSecondaryContainer= Color(0xFFCFD8DC)

// Tertiary stays amber: it tints the Notes shortcut in the dashboard header.
val DarkTertiary            = DarkWarningAmber
val DarkOnTertiary          = Color(0xFF121212)
val DarkTertiaryContainer   = Color(0xFF422C10)
val DarkOnTertiaryContainer = Color(0xFFFFE0B2)

val DarkOnBackground        = DarkTextPrimary
val DarkOnSurface           = DarkTextPrimary
val DarkSurfaceVariant      = DarkSurfaceElevated
val DarkOnSurfaceVariant    = DarkTextSecondary

val DarkOutline             = Color(0xFF424242)
val DarkOutlineVariant      = Color(0xFF2C2C2C)   // subtle hairline stroke
val DarkError               = DarkExpenseRed
val DarkOnError             = Color(0xFF121212)
val DarkErrorContainer      = Color(0xFF5C2929)
val DarkOnErrorContainer    = Color(0xFFFFDADA)

// Dark Tonal Surface Ladder (greyish canvas where low/container steps match canvas for box-free UI)
val DarkSurfaceDim          = Color(0xFF121212)
val DarkSurfaceBright       = Color(0xFF2C2C2C)
val DarkSurfaceContainerLowest  = Color(0xFF0F0F0F)
val DarkSurfaceContainerLow     = Color(0xFF121212)   // Matches canvas — NO background box around cards
val DarkSurfaceContainer        = Color(0xFF121212)   // Matches canvas — flat seamless rows
val DarkSurfaceContainerHigh    = Color(0xFF1E1E1E)
val DarkSurfaceContainerHighest = Color(0xFF2C2C2C)
val DarkInverseSurface      = Color(0xFFE3E3E3)
val DarkInverseOnSurface    = Color(0xFF121212)
val DarkInversePrimary      = Color(0xFF3362CC)
val DarkScrim               = Color(0xFF000000)

// ----------------------------------------------------------------------------
// Light Theme Base Tokens (airy light canvas matching modern minimal UI)
// ----------------------------------------------------------------------------
val LightBackgroundLight      = Color(0xFFFAFAFE)   // clean, ultra-light canvas
val LightSurfaceLight         = Color(0xFFFAFAFE)   // matches canvas for box-free UI
val LightSurfaceElevatedLight = Color(0xFFF2EFFB)   // subtle elevated container
val LightBorderLight          = Color(0xFFE5E2F0).copy(alpha = 0.3f)   // near-invisible soft hairline
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
val LightOutlineVariant       = Color(0xFFE5E2F0)
val LightError                = LightExpenseRed
val LightOnError              = Color(0xFFFFFFFF)
val LightErrorContainer       = Color(0xFFF7DADE)
val LightOnErrorContainer     = Color(0xFF410008)

// Light Tonal Surface Ladder (airy light canvas where low/container steps match canvas for box-free UI)
val LightSurfaceDim           = Color(0xFFECE9F5)
val LightSurfaceBright        = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest  = Color(0xFFFAFAFE)
val LightSurfaceContainerLow     = Color(0xFFFAFAFE)   // Matches canvas — NO background box around cards
val LightSurfaceContainer        = Color(0xFFFAFAFE)   // Matches canvas — flat seamless rows
val LightSurfaceContainerHigh    = Color(0xFFF1EFF8)   // Subtle elevated surface for pills & segmented toggles
val LightSurfaceContainerHighest = Color(0xFFE8E5F4)
val LightInverseSurface       = Color(0xFF2F2E3A)
val LightInverseOnSurface     = Color(0xFFF3F0FC)
val LightInversePrimary       = Color(0xFFC9BFFF)   // matched to the dark primary
val LightScrim                = Color(0xFF000000)

// ============================================================================
// 2. BRAND GRADIENT & ACCENTS (hero card, badges)
// ============================================================================
// Sleek neutral slate ramp for clean modern hero cards.
val BrandGradientStart = Color(0xFF1E293B)   // clean dark slate
val BrandGradientMid   = Color(0xFF334155)   // minimal slate grey
val BrandGradientEnd   = Color(0xFF475569)   // neutral slate
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
val CatDark_Banking     = Color(0xFF60A5FA)
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
val PurposeDark_CreditCard   = Color(0xFF818CF8)
val PurposeDark_Utilities    = Color(0xFF6E9BFF)
val PurposeDark_Travel       = Color(0xFF45BDD6)
val PurposeDark_Leisure      = Color(0xFFF472B6)
val PurposeDark_Health       = Color(0xFFFF7A72)
val PurposeDark_Other        = Color(0xFF939AA8)

// ============================================================================
// 4. ON-GRADIENT ACCENTS
// ============================================================================
// The hero paints its own indigo->rose gradient, so colorScheme.error / .secondary don't have
// enough contrast against it. These are the gradient-safe stand-ins for the same semantics.
val OnGradientMoneyUp   = Color(0xFFFFB4BC)   // soft coral — spending increased
val OnGradientMoneyDown = BrandAccentMint     // mint — spending decreased
