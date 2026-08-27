/**
 * The app's own icon family — hand-authored [ImageVector]s for the five concepts that carry the
 * product's personality: Dashboard, History, Notes, Theme (light / dark / auto) and AI.
 *
 * These replace the stock `androidx.compose.material.icons` glyphs at those call sites. Everything
 * else in the app still uses Material's set, so this family is deliberately drawn to one rulebook
 * rather than mixed styles:
 *
 * - **Geometry**: 24×24 viewport, 24dp nominal size, all ink inside ~2..22 so the glyphs stay
 *   optically the same size as Material's at 20dp (`Sizes.iconAction`) and 16dp (`Sizes.iconInline`).
 * - **One approach: strokes, never fills.** Every path is an unfilled outline stroked at
 *   [StrokeWeight] (2dp at nominal size) with round caps and round joins. No path carries a fill,
 *   so nothing needs a fill rule and the weight is uniform across the family.
 * - **No baked color.** Paths stroke with `SolidColor(Color.Black)`; the `Icon` composable's tint
 *   recolors them, exactly like Material's generated icons.
 * - **Shared motifs**: Dashboard, History and the Theme triad are all built on the same 7.4-unit
 *   circle (as the rim itself, or as the envelope the sun's ray tips reach), so the set reads as
 *   one alphabet. The triad is sun disc + rays / crescent / crescent + rays, which lets cycling
 *   the theme animate as one coherent family instead of three unrelated glyphs.
 *
 * Each vector is built once and cached in a private backing field (Material's own pattern), so
 * recomposition never rebuilds a path list.
 */
package com.alpha.spendtracker.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 2dp at the nominal 24dp size — the single weight the whole family is drawn at. */
private const val StrokeWeight = 2.0f

private const val ViewportSize = 24f

private fun appIcon(
    name: String,
    block: ImageVector.Builder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = ViewportSize,
    viewportHeight = ViewportSize
).apply(block).build()

/**
 * The family's only paint: an unfilled outline stroked at [StrokeWeight]. `Color.Black` is a
 * placeholder that `Icon`'s tint replaces — no icon carries its own color.
 */
private fun ImageVector.Builder.outline(
    pathBuilder: PathBuilder.() -> Unit
): ImageVector.Builder = path(
    fill = null,
    fillAlpha = 1f,
    stroke = SolidColor(Color.Black),
    strokeAlpha = 1f,
    strokeLineWidth = StrokeWeight,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    strokeLineMiter = 4f,
    pathFillType = PathFillType.NonZero,
    pathBuilder = pathBuilder
)

object AppIcons {

    /**
     * **Dashboard** — a segmented donut chart: three arcs of unequal length (138° / 78° / 72°) on
     * one 7.4-unit circle, with round-capped ends and even gaps. Reads as "where the money went"
     * rather than a generic app grid.
     */
    val Dashboard: ImageVector
        get() = _dashboard ?: appIcon("AppIcons.Dashboard") {
            outline {
                // Dominant segment, 12° → 150° (clockwise from 12 o'clock).
                moveTo(13.539f, 4.762f)
                arcTo(7.4f, 7.4f, 0f, false, true, 15.7f, 18.409f)
                // 174° → 252°
                moveTo(12.774f, 19.359f)
                arcTo(7.4f, 7.4f, 0f, false, true, 4.962f, 14.287f)
                // 276° → 348°
                moveTo(4.641f, 11.226f)
                arcTo(7.4f, 7.4f, 0f, false, true, 10.461f, 4.762f)
            }
        }.also { _dashboard = it }

    /**
     * **History** — a clock whose rim breaks open at the upper left into a sharp counter-clockwise
     * arrowhead, with the hands set to 4 o'clock. Same 7.4-unit circle as [Dashboard]; the 44° rim
     * gap gives it a silhouette you can pick out of a nav bar.
     */
    val History: ImageVector
        get() = _history ?: appIcon("AppIcons.History") {
            outline {
                // Rim: 330° clockwise all the way round to 286°, split so no arc exceeds 180°.
                moveTo(8.3f, 5.591f)
                arcTo(7.4f, 7.4f, 0f, false, true, 17.831f, 16.556f)
                arcTo(7.4f, 7.4f, 0f, false, true, 4.887f, 9.96f)
                // Arrowhead: its base straddles the rim's open end radially and its tip runs
                // 2.4 further along the counter-clockwise tangent, so it stays clear of the rim.
                moveTo(7.35f, 3.945f)
                lineTo(6.222f, 6.791f)
                lineTo(9.25f, 7.237f)
                // Hands: 12 → centre → 4 o'clock, one polyline.
                moveTo(12f, 8f)
                lineTo(12f, 12f)
                lineTo(15.3f, 13.7f)
            }
        }.also { _history = it }

    /**
     * **Notes** — a page with a dog-eared top-right corner and two content lines of deliberately
     * different length. The 5.2-unit dog-ear and the uneven lines keep the silhouette asymmetric,
     * so it still reads as a note at 16dp where a symmetric page would just read as a rectangle.
     */
    val Notes: ImageVector
        get() = _notes ?: appIcon("AppIcons.Notes") {
            outline {
                // Page outline, clockwise from just past the top-left corner.
                moveTo(7.8f, 3.6f)
                lineTo(13.2f, 3.6f)
                lineTo(18.4f, 8.8f)
                lineTo(18.4f, 18.2f)
                quadTo(18.4f, 20.4f, 16.2f, 20.4f)
                lineTo(7.8f, 20.4f)
                quadTo(5.6f, 20.4f, 5.6f, 18.2f)
                lineTo(5.6f, 5.8f)
                quadTo(5.6f, 3.6f, 7.8f, 3.6f)
                close()
                // The folded corner itself.
                moveTo(13.2f, 3.6f)
                lineTo(13.2f, 8.8f)
                lineTo(18.4f, 8.8f)
                // Two content lines, long then short.
                moveTo(8.4f, 12.2f)
                lineTo(15.6f, 12.2f)
                moveTo(8.4f, 16.4f)
                lineTo(12.8f, 16.4f)
            }
        }.also { _notes = it }

    /**
     * **Theme: light** — a 4.4-unit sun disc inside eight round-capped rays whose tips reach the
     * same envelope the other two theme glyphs fill, so the triad swaps without the icon changing
     * apparent size.
     */
    val ThemeLight: ImageVector
        get() = _themeLight ?: appIcon("AppIcons.ThemeLight") {
            outline {
                // Disc, drawn as two semicircles.
                moveTo(12f, 7.6f)
                arcTo(4.4f, 4.4f, 0f, false, true, 12f, 16.4f)
                arcTo(4.4f, 4.4f, 0f, false, true, 12f, 7.6f)
                close()
                // Eight rays, 7.4 → 9.0 from centre.
                moveTo(12f, 4.6f)
                lineTo(12f, 3f)
                moveTo(17.233f, 6.767f)
                lineTo(18.364f, 5.636f)
                moveTo(19.4f, 12f)
                lineTo(21f, 12f)
                moveTo(17.233f, 17.233f)
                lineTo(18.364f, 18.364f)
                moveTo(12f, 19.4f)
                lineTo(12f, 21f)
                moveTo(6.767f, 17.233f)
                lineTo(5.636f, 18.364f)
                moveTo(4.6f, 12f)
                lineTo(3f, 12f)
                moveTo(6.767f, 6.767f)
                lineTo(5.636f, 5.636f)
            }
        }.also { _themeLight = it }

    /**
     * **Theme: dark** — a crescent cut from the family's 7.4-unit circle: a 262° outer limb closed
     * by a shallower return arc that bites in to the centre, so the two strokes stay well apart and
     * the crescent never fills in at small sizes.
     */
    val ThemeDark: ImageVector
        get() = _themeDark ?: appIcon("AppIcons.ThemeDark") {
            outline {
                moveTo(19.39f, 12.387f)
                arcTo(7.4f, 7.4f, 0f, true, true, 11.355f, 4.628f)
                arcTo(5.7f, 5.7f, 0f, false, false, 19.39f, 12.387f)
                close()
            }
        }.also { _themeDark = it }

    /**
     * **Theme: auto** — the [ThemeDark] crescent at 5.0 units with five of [ThemeLight]'s rays
     * behind it: literally sun and moon in one mark, on the same ray envelope as the light glyph.
     */
    val ThemeAuto: ImageVector
        get() = _themeAuto ?: appIcon("AppIcons.ThemeAuto") {
            outline {
                // Crescent, same construction as ThemeDark at a smaller radius.
                moveTo(16.993f, 12.262f)
                arcTo(5f, 5f, 0f, true, true, 11.564f, 7.019f)
                arcTo(3.85f, 3.85f, 0f, false, false, 16.993f, 12.262f)
                close()
                // Rays on the crescent's closed side only, 7.4 → 9.0 from centre.
                moveTo(17.233f, 17.233f)
                lineTo(18.364f, 18.364f)
                moveTo(12f, 19.4f)
                lineTo(12f, 21f)
                moveTo(6.767f, 17.233f)
                lineTo(5.636f, 18.364f)
                moveTo(4.6f, 12f)
                lineTo(3f, 12f)
                moveTo(6.767f, 6.767f)
                lineTo(5.636f, 5.636f)
            }
        }.also { _themeAuto = it }

    /**
     * **AI** — a four-point spark with concave arms pinched to 3.65 units of the centre (much
     * sharper than Material's `AutoAwesome`), seated left of centre so two smaller twinkles can
     * hold the top-right and bottom-right. One dominant point, two minor: the AI-spark language,
     * drawn to this family's weight.
     */
    val Ai: ImageVector
        get() = _ai ?: appIcon("AppIcons.Ai") {
            outline {
                // Main spark: tips at N/E/S/W of (10, 11.2), each arm a quad pinched to the waist.
                moveTo(10f, 4.4f)
                quadTo(11.768f, 9.432f, 16.8f, 11.2f)
                quadTo(11.768f, 12.968f, 10f, 18f)
                quadTo(8.232f, 12.968f, 3.2f, 11.2f)
                quadTo(8.232f, 9.432f, 10f, 4.4f)
                close()
                // Twinkle, top right.
                moveTo(19f, 4f)
                lineTo(19f, 7.2f)
                moveTo(17.4f, 5.6f)
                lineTo(20.6f, 5.6f)
                // Twinkle, bottom right.
                moveTo(18.7f, 14.7f)
                lineTo(18.7f, 17.7f)
                moveTo(17.2f, 16.2f)
                lineTo(20.2f, 16.2f)
            }
        }.also { _ai = it }
}

private var _dashboard: ImageVector? = null
private var _history: ImageVector? = null
private var _notes: ImageVector? = null
private var _themeLight: ImageVector? = null
private var _themeDark: ImageVector? = null
private var _themeAuto: ImageVector? = null
private var _ai: ImageVector? = null
