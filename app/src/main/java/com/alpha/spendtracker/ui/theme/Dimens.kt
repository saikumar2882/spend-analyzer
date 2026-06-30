/**
 * Design tokens — a single spacing scale and a single shape scale so the UI has consistent
 * rhythm and corner hierarchy instead of ad-hoc per-call-site values.
 *
 * Spacing:  4 / 8 / 12 / 16 / 24 / 32
 * Radius:   8 (xs) / 12 (sm) / 16 (md) / 24 (lg) / 28 (xl)
 *
 * `AppShapes` is wired into MaterialTheme so MaterialTheme.shapes.{small,medium,large,...} resolve
 * to these tokens; the `Radius` object is for places that need an explicit dp corner.
 */
package com.alpha.spendtracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 28.dp
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
