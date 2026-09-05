/**
 * Design tokens — a single spacing scale and a single shape scale following 4/8pt grid rules.
 *
 * Spacing:  4 (xs) / 8 (sm) / 12 (md) / 16 (lg) / 20 (ml) / 24 (xl) / 32 (xxl) / 48 (xxxl)
 * Radius:   4 (xxs) / 8 (xs) / 12 (sm) / 16 (md) / 20 (lg) / 24 (xl)
 */
package com.alpha.spendtracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val ml = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    // Fixed component spacing rules
    val ScreenPadding = 16.dp
    val CardPadding   = 12.dp
    val CardGap       = 12.dp
    val SectionGap    = 20.dp
}

object Radius {
    val xxs = 2.dp
    val xs  = 4.dp
    val sm  = 8.dp  // Small controls (chips, small buttons)
    val md  = 12.dp // Standard cards / transaction rows
    val lg  = 16.dp // Hero card / main containers
    val xl  = 20.dp // Bottom sheets / modals
}

@Composable
@ReadOnlyComposable
fun Dp.scaledByFont(max: Float = 1.5f): Dp =
    this * LocalDensity.current.fontScale.coerceIn(1f, max)

object Sizes {
    val minTouchTarget = 48.dp
    val ButtonHeight   = 52.dp
    val InputHeight    = 56.dp
    val FabSize        = 56.dp

    val iconAction = 20.dp
    val iconInline = 16.dp

    val dateBadgeCompact = 44.dp
    val dateBadge = 48.dp
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)
