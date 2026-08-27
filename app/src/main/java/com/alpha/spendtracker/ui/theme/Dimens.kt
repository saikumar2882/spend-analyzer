/**
 * Design tokens — a single spacing scale and a single shape scale so the UI has consistent
 * rhythm and corner hierarchy instead of ad-hoc per-call-site values.
 *
 * Spacing:  4 / 8 / 12 / 16 / 20 / 24 / 32 / 48   (every step divisible by 4)
 * Radius:   4 (xxs) / 8 (xs) / 12 (sm) / 16 (md) / 24 (lg) / 28 (xl)
 *
 * `AppShapes` is wired into MaterialTheme so MaterialTheme.shapes.{small,medium,large,...} resolve
 * to these tokens; the `Radius` object is for places that need an explicit dp corner.
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
}

object Radius {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 28.dp
}

/**
 * Scales a dp value by the system font scale, clamped so a huge accessibility setting can't blow the
 * layout out entirely.
 *
 * For boxes whose *contents* are text but whose height cannot simply be a minimum — a fixed-size
 * `Canvas`, most of all. Everything else should prefer `heightIn(min = …)`, which grows on its own.
 */
@Composable
@ReadOnlyComposable
fun Dp.scaledByFont(max: Float = 1.5f): Dp =
    this * LocalDensity.current.fontScale.coerceIn(1f, max)

object Sizes {
    /**
     * Floor for anything tappable. Material's own components already reserve this much touch area
     * even when they paint smaller, so this is only for hand-rolled `Surface(onClick = …)` targets,
     * which get no such treatment.
     */
    val minTouchTarget = 48.dp

    /** Icon inside a [minTouchTarget] button, and inline next to a line of text. */
    val iconAction = 20.dp
    val iconInline = 16.dp

    /** Date badge on a transaction row: compact in dense lists, standard on the dashboard. */
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
