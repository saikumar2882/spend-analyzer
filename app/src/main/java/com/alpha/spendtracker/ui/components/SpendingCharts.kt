/**
 * Visual components for rendering spending analytics through charts and graphs.
 */
package com.alpha.spendtracker.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.ui.viewmodel.TrendPoint
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.alpha.spendtracker.ui.theme.*
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round

/**
 * Returns a theme-aware color map for spending categories.
 */
@Composable
fun getCategoryColors(): Map<String, Color> {
    val isDark = isAppInDarkTheme
    return remember(isDark) {
        if (isDark) {
            mapOf(
                "UPI Apps" to CatDark_UPI,
                "Quick Commerce" to CatDark_QuickComm,
                "E-Commerce" to CatDark_Ecommerce,
                "Banking & Cards" to CatDark_Banking,
                "Friend Lending" to CatDark_Lending,
                "Other" to CatDark_Other
            )
        } else {
            mapOf(
                "UPI Apps" to CatLight_UPI,
                "Quick Commerce" to CatLight_QuickComm,
                "E-Commerce" to CatLight_Ecommerce,
                "Banking & Cards" to CatLight_Banking,
                "Friend Lending" to CatLight_Lending,
                "Other" to CatLight_Other
            )
        }
    }
}

/**
 * Returns a theme-aware color map for spending purposes.
 */
@Composable
fun getPurposeColors(): Map<String, Color> {
    val isDark = isAppInDarkTheme
    return remember(isDark) {
        if (isDark) {
            mapOf(
                "Groceries & Food" to PurposeDark_Food,
                "Shopping & Apparels" to PurposeDark_Shopping,
                "Lending" to PurposeDark_Lending,
                "Borrowing" to PurposeDark_Borrowing,
                "Credit Card Bill" to PurposeDark_CreditCard,
                "Rent & Utilities" to PurposeDark_Utilities,
                "Travel & Commute" to PurposeDark_Travel,
                "Subscription & Leisure" to PurposeDark_Leisure,
                "Healthcare & Medical" to PurposeDark_Health,
                "Others" to PurposeDark_Other
            )
        } else {
            mapOf(
                "Groceries & Food" to PurposeLight_Food,
                "Shopping & Apparels" to PurposeLight_Shopping,
                "Lending" to PurposeLight_Lending,
                "Borrowing" to PurposeLight_Borrowing,
                "Credit Card Bill" to PurposeLight_CreditCard,
                "Rent & Utilities" to PurposeLight_Utilities,
                "Travel & Commute" to PurposeLight_Travel,
                "Subscription & Leisure" to PurposeLight_Leisure,
                "Healthcare & Medical" to PurposeLight_Health,
                "Others" to PurposeLight_Other
            )
        }
    }
}

/**
 * A beautiful, custom Canvas-drawn Pie/Donut Chart for category breakdown
 */
@Composable
fun SpendingDonutChart(
    categoryBreakdown: Map<String, Double>,
    modifier: Modifier = Modifier,
    inCard: Boolean = false,
    usePurposeColors: Boolean = false,
    onCategoryClick: ((String) -> Unit)? = null
) {
    if (categoryBreakdown.isEmpty()) {
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data to display",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val chartColors = if (usePurposeColors) getPurposeColors() else getCategoryColors()
    // Slice color for anything the palette doesn't name, and for the rolled-up "Others" row.
    val neutralSwatch = chartColors[if (usePurposeColors) "Others" else "Other"] ?: Color.Gray
    // Hoist the total and the sorted item list so they aren't recomputed on every recomposition
    // (the donut animates, so this composable recomposes frequently).
    val total = remember(categoryBreakdown) { categoryBreakdown.values.sum() }
    val items = remember(categoryBreakdown) { categoryBreakdown.toList().sortedByDescending { it.second } }

    val reduceMotion = rememberReduceMotion()

    var animatedProgress by remember { mutableFloatStateOf(if (reduceMotion) 1f else 0f) }
    val progressFactor by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(motionDuration(MotionDuration.CHART_DRAW, reduceMotion)),
        label = "diagram_draw"
    )

    // Read the animating progress inside the tap-gesture lambda without keying pointerInput on it.
    // Keying on progressFactor would tear down and rebuild the gesture detector every animation frame.
    val currentProgress = rememberUpdatedState(progressFactor)

    LaunchedEffect(categoryBreakdown) {
        animatedProgress = 1f
    }

    Row(
        modifier = modifier.then(
            if (inCard) Modifier
            else Modifier
                .background(chartContainerColor(), shape = RoundedCornerShape(24.dp))
                .padding(6.dp)
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Doughnut Canvas
        Box(
            // Grows with the font scale so the "Total ₹…" pair centred inside it doesn't overflow
            // the ring at a large accessibility setting.
            modifier = Modifier.size(108.dp.scaledByFont()),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(items, total) {
                        detectTapGestures { offset ->
                            if (total <= 0 || currentProgress.value < 0.9f) return@detectTapGestures

                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val dx = offset.x - centerX
                            val dy = offset.y - centerY
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                            val strokeWidth = 12.dp.toPx()
                            val outerRadius = kotlin.math.min(size.width, size.height) / 2f
                            val innerRadius = outerRadius - strokeWidth

                            if (distance in innerRadius..outerRadius) {
                                var angle =
                                    kotlin.math.atan2(dy, dx) * (180f / kotlin.math.PI).toFloat()
                                if (angle < -90f) angle += 360f
                                val clickAngle = angle + 90f // Offset to match -90f start

                                var currentAngle = 0f
                                for ((cat, amount) in items) {
                                    val sweep = (amount / total * 360f).toFloat()
                                    if (clickAngle in currentAngle..(currentAngle + sweep)) {
                                        onCategoryClick?.invoke(cat)
                                        break
                                    }
                                    currentAngle += sweep
                                }
                            }
                        }
                    }
            ) {
                val strokeWidth = 12.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)
                val rectSize = Size(radius * 2, radius * 2)
                val topLeft = Offset(center.x - radius, center.y - radius)

                var startAngle = -90f // Start from the top

                items.forEach { (cat, amount) ->
                    val rawSweepAngle = if (total > 0) ((amount / total) * 360f).toFloat() * progressFactor else 0f
                    val sweepAngle = if (rawSweepAngle.isNaN() || rawSweepAngle < 0f) 0f else rawSweepAngle
                    val color = chartColors[cat] ?: if (usePurposeColors) Color.Gray else chartColors["Other"]!!

                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = rectSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweepAngle
                }
            }

            // Total spent label inside donut
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "₹${formatCurrency(total)}",
                    style = MaterialTheme.typography.titleSmall.asMoney(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(Spacing.md))

        // Legend list
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            items.take(4).forEach { (category, amount) ->
                LegendRow(
                    label = category,
                    percent = if (total > 0) (amount / total * 100).toInt() else 0,
                    swatch = chartColors[category] ?: neutralSwatch
                )
            }
            if (items.size > 4) {
                val remainingAmount = items.drop(4).sumOf { it.second }
                LegendRow(
                    label = "Others",
                    percent = if (total > 0) (remainingAmount / total * 100).toInt() else 0,
                    swatch = neutralSwatch
                )
            }
        }
    }
}

/**
 * One "swatch — label — percent" line of a chart legend. Shared by the per-slice rows and the
 * rolled-up "Others" row, which previously drew a differently-sized swatch with different spacing.
 */
@Composable
private fun LegendRow(label: String, percent: Int, swatch: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(Spacing.sm)
                    .background(swatch, RoundedCornerShape(Radius.xxs))
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Collapses a style's line box onto its glyphs, for text that will be drawn rotated.
 *
 * Rotated -90 degrees, a label's *line height* is what occupies horizontal space, so the 14.sp line
 * height `labelSmall` carries for a 9.sp glyph becomes 5.sp of dead width -- half a slot at ~31
 * bars, which is what made neighbouring vertical labels collide. Tracking is zeroed for the same
 * reason: it buys nothing on a 2-4 character number and only lengthens the strip.
 */
private fun TextStyle.trimmedForRotation(): TextStyle = copy(
    lineHeight = fontSize,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

/**
 * Custom Canvas-drawn bar chart for spending trends over days, weeks, months or years.
 *
 * Bars only — no average guideline and no above/below-average recolouring. Every bar is drawn the
 * same way and carries its own amount, so the chart answers "how did spending move?" and nothing
 * else.
 *
 * Two layout modes, chosen from how much room one bar's slot actually has:
 *  - **sparse** (a 7-day week, a 12-month year, the 5-6 week buckets of a month or a long custom
 *    range): amounts sit horizontally above the bar, dates horizontally below. Reads like a normal
 *    bar chart, and is what every view uses now that the month is bucketed by week.
 *  - **dense** (a short custom range at a bar per day): both amounts and dates stand on end,
 *    rotated -90 degrees, because horizontal text does not fit a narrow slot. Rotated, it is the
 *    glyph *height* that has to fit, so every bar keeps both its amount and its date.
 */
@Composable
fun SpendingTrendBarChart(
    trendPoints: List<TrendPoint>,
    modifier: Modifier = Modifier,
    inCard: Boolean = false
) {
    // The month view now emits a bar for every day of the month, so "no data" is an all-zero list
    // rather than an empty one.
    if (trendPoints.isEmpty() || trendPoints.all { it.amount <= 0.0 }) {
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(Radius.md)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No trend data in this period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Scale to a *robust* ceiling, not to the maximum. One ₹8.6k bucket among ₹200-600 ones scaled
    // every other bar down to a two-pixel stub -- the chart technically plotted the data and showed
    // nothing. The ceiling is the 90th percentile of non-empty buckets with a little headroom,
    // floored against the mean so a period of near-identical bars still gets headroom, and capped at
    // the real maximum so an outlier-free period doesn't get phantom empty space.
    //
    // Buckets above the ceiling are drawn to full height and marked as broken (see `clipped` below),
    // and every bar carries its own amount, so nothing is hidden -- only re-scaled. The mean is used
    // for scaling only; it is deliberately not drawn.
    val displayMax = remember(trendPoints) {
        val active = trendPoints.map { it.amount }.filter { it > 0.0 }.sorted()
        if (active.isEmpty()) {
            100.0
        } else {
            val mean = active.sum() / active.size
            val p90Index = (ceil(active.size * 0.9).toInt() - 1).coerceIn(0, active.size - 1)
            maxOf(active[p90Index] * 1.35, mean * 2.2)
                .coerceAtMost(active.last())
                .coerceAtLeast(1.0)
        }
    }

    val reduceMotion = rememberReduceMotion()

    var animatedProgress by remember { mutableFloatStateOf(if (reduceMotion) 1f else 0f) }
    val progressFactor by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(motionDuration(MotionDuration.CHART_DRAW, reduceMotion)),
        label = "bar_draw"
    )

    LaunchedEffect(trendPoints) {
        animatedProgress = 1f
    }

    val textMeasurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val barColor = MaterialTheme.colorScheme.primary
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)

    val textStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariantColor, fontSize = 9.sp)

    // Pre-measure every label once. These don't change as the bars animate, so measuring them
    // inside the draw lambda re-ran textMeasurer.measure for every bar on every animation frame.
    val valueStyle = remember(textStyle, barColor) {
        textStyle.copy(
            fontWeight = FontWeight.Bold,
            color = barColor.copy(alpha = 0.8f),
            fontSize = 8.sp
        )
    }
    // Rotated variants of both label kinds (see trimmedForRotation).
    // 8.sp, not 9.sp. Trimming the line box still leaves the font's own ascent+descent, which for
    // Inter is ~1.2x the point size -- a 9.sp strip measures ~29px against a ~28.7px slot at 31
    // bars, so the stride below rounded up to 2 and half the dates and amounts vanished. 8.sp
    // measures ~25px and every bar keeps both labels.
    val rotatedValueStyle = remember(textStyle) {
        textStyle.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp).trimmedForRotation()
    }
    val rotatedLabelStyle = remember(textStyle) {
        textStyle.copy(fontSize = 8.sp).trimmedForRotation()
    }

    // Both orientations are measured up front because which one is used depends on the slot width,
    // which is only known in the draw lambda -- and measuring there is exactly the per-frame cost
    // this block exists to avoid. Four short strings per bar is cheap. Colour is applied at draw
    // time via drawText's override, so it is not baked into the measured layout.
    val measuredLabels = remember(trendPoints, textStyle, valueStyle, rotatedValueStyle, rotatedLabelStyle, locale) {
        trendPoints.map { point ->
            val valueText = if (point.amount > 0) {
                if (point.amount >= 1000) {
                    String.format(locale, "%.1fk", point.amount / 1000)
                } else {
                    point.amount.toInt().toString()
                }
            } else {
                null
            }
            MeasuredBarLabels(
                label = textMeasurer.measure(text = point.label, style = textStyle),
                rotatedLabel = textMeasurer.measure(text = point.label, style = rotatedLabelStyle),
                value = valueText?.let { textMeasurer.measure(text = it, style = valueStyle) },
                rotatedValue = valueText?.let { textMeasurer.measure(text = it, style = rotatedValueStyle) }
            )
        }
    }
    val widestLabel = remember(measuredLabels) { measuredLabels.maxOfOrNull { it.label.size.width } ?: 0 }
    val tallestLabel = remember(measuredLabels) { measuredLabels.maxOfOrNull { it.label.size.height } ?: 0 }
    val widestValue = remember(measuredLabels) { measuredLabels.maxOfOrNull { it.value?.size?.width ?: 0 } ?: 0 }
    val tallestValue = remember(measuredLabels) { measuredLabels.maxOfOrNull { it.value?.size?.height ?: 0 } ?: 0 }
    // Rotated, a label's *width* is the headroom it needs above the bar, and its *height* is the
    // width of the strip standing on the bar -- i.e. what has to fit inside one slot.
    val widestRotatedValue = remember(measuredLabels) { measuredLabels.maxOfOrNull { it.rotatedValue?.size?.width ?: 0 } ?: 0 }
    val tallestRotatedValue = remember(measuredLabels) { measuredLabels.maxOfOrNull { it.rotatedValue?.size?.height ?: 0 } ?: 0 }
    val widestRotatedLabel = remember(measuredLabels) { measuredLabels.maxOfOrNull { it.rotatedLabel.size.width } ?: 0 }
    val tallestRotatedLabel = remember(measuredLabels) { measuredLabels.maxOfOrNull { it.rotatedLabel.size.height } ?: 0 }

    Column(
        modifier = modifier.then(
            if (inCard) Modifier
            else Modifier
                .background(chartContainerColor(), shape = RoundedCornerShape(Radius.lg))
                .padding(Spacing.lg)
        )
    ) {
        if (!inCard) {
            Text(
                text = "Spending over time",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                // Taller than the old 140.dp: the label bands claim real vertical space at both
                // ends, and at 140.dp the bars themselves were left with almost none. A Canvas needs
                // a concrete height, so this one grows with the system font scale — the labels are
                // sp-sized, and at a fixed 196.dp a large scale ate the whole plot area.
                .height(196.dp.scaledByFont())
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val leftPadding = Spacing.sm.toPx()
            val rightPadding = Spacing.sm.toPx()
            val chartRangeWidth = canvasWidth - leftPadding - rightPadding

            val barCount = trendPoints.size
            val slotWidth = if (barCount > 0) chartRangeWidth / barCount else chartRangeWidth

            val labelGap = Spacing.xs.toPx()
            val valueGap = Spacing.xs.toPx() // separates a bar's top from its amount

            // Horizontal labels only work while a whole label plus a gap fits in one slot -- true
            // for the 7 daily / 12 monthly buckets. Past that the text stands on end.
            val rotateValues = widestValue > 0 && widestValue + labelGap > slotWidth
            val rotateLabels = widestLabel > 0 && widestLabel + labelGap > slotWidth

            // Thin, elegant bars: ~52% of the slot, capped so a 7-bar week doesn't render as slabs
            // and floored so a long custom range stays visible.
            val barWidth = (slotWidth * 0.52f)
                .coerceAtMost(Sizes.iconInline.toPx())
                .coerceAtLeast(2.dp.toPx())
                .coerceAtMost(slotWidth)

            // Reserve the exact space each label band needs at both ends. Rotated, that is the
            // widest label's width; horizontal, its tallest height. Everything between is the plot.
            // A clipped bar's cap floats above the plot ceiling, so its amount has to clear both.
            val clipGap = 3.dp.toPx()
            val clipCapHeight = 4.dp.toPx()
            val anyClipped = trendPoints.any { it.amount > displayMax }
            val clipExtra = if (anyClipped) clipGap + clipCapHeight else 0f

            // A small inset on top of the measured band so the tallest bar's amount doesn't sit
            // flush against the canvas edge and run into whatever is above the chart.
            val topInset = Spacing.xs.toPx()
            val topLabelSpace = topInset + clipExtra + when {
                rotateValues -> widestRotatedValue + valueGap
                tallestValue > 0 -> tallestValue + valueGap
                else -> 0f
            }
            val bottomLabelSpace =
                if (rotateLabels) widestRotatedLabel + labelGap else tallestLabel + labelGap

            val axisY = canvasHeight - bottomLabelSpace
            val plotHeight = (axisY - topLabelSpace).coerceAtLeast(1f)

            // Grid lines span the plot area only, so they stay flush with the bars instead of
            // cutting through the label bands. The topmost one doubles as the visible ceiling that
            // separates the amount band from the plot.
            val gridSteps = 3
            for (i in 0..gridSteps) {
                val y = topLabelSpace + (plotHeight / gridSteps) * i
                drawLine(
                    color = gridLineColor,
                    start = Offset(leftPadding, y),
                    end = Offset(canvasWidth - rightPadding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (barCount > 0) {
                // Rotation makes room for ~31 bars, but a custom range can be arbitrarily long, and
                // past the point where even a trimmed glyph box fits the slot the vertical labels
                // would overlap each other. Thin them by slot width, measured against the strip
                // width (the rotated label's height). At a month or less both strides are 1, i.e.
                // every bar keeps its amount *and* its date.
                fun strideFor(rotated: Boolean, stripWidth: Int, horizontalWidth: Int): Int {
                    val needed = if (rotated) stripWidth.toFloat() else horizontalWidth + labelGap
                    return if (needed <= 0f) 1 else max(1, ceil(needed / slotWidth).toInt())
                }
                val labelStride = strideFor(rotateLabels, tallestRotatedLabel, widestLabel)
                val valueStride = strideFor(rotateValues, tallestRotatedValue, widestValue)

                trendPoints.forEachIndexed { index, point ->
                    val slotStart = leftPadding + (index * slotWidth)
                    val barCenterX = slotStart + (slotWidth / 2)
                    val xStart = barCenterX - (barWidth / 2)
                    val clipped = point.amount > displayMax

                    val fraction = (point.amount / displayMax).coerceAtMost(1.0)
                    val barHeight = (fraction * plotHeight).toFloat() * progressFactor
                    val yStart = axisY - barHeight
                    // A clipped bar's amount has to clear the detached cap drawn above it.
                    val valueBaseY = if (clipped) yStart - clipExtra else yStart

                    if (point.amount > 0) {
                        // Every bar gets the same treatment. Bar height already carries the
                        // comparison; recolouring against an average only added a second, weaker
                        // encoding of it.
                        val topColor = barColor
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    topColor,
                                    topColor.copy(alpha = topColor.alpha * 0.78f)
                                ),
                                startY = yStart,
                                endY = axisY
                            ),
                            topLeft = Offset(xStart, yStart),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2)
                        )
                        // Broken-bar cap: a detached stub above a day that runs past the ceiling,
                        // the conventional "continues beyond this scale" mark. Its amount is
                        // printed like every other, so the real figure is never lost.
                        if (clipped) {
                            drawRoundRect(
                                color = topColor,
                                topLeft = Offset(xStart, yStart - clipGap - clipCapHeight),
                                size = Size(barWidth, clipCapHeight),
                                cornerRadius = CornerRadius(barWidth / 2)
                            )
                        }
                    } else {
                        // Tiny line placeholder for a zero-spend day.
                        drawRoundRect(
                            color = outlineVariantColor.copy(alpha = 0.4f),
                            topLeft = Offset(xStart, axisY - 2.dp.toPx()),
                            size = Size(barWidth, 2.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx())
                        )
                    }

                    // Date label, below the axis.
                    if (index % labelStride == 0) {
                        if (rotateLabels) {
                            val labelResult = measuredLabels[index].rotatedLabel
                            // Rotating -90deg about the text's own top-left maps its box to
                            // x: [0, height] and y: [-width, 0] relative to that pivot -- so the
                            // pivot lands at the bottom-left of the drawn strip. Putting it a full
                            // strip-length below the axis grows the text up into the bottom band.
                            val pivot = Offset(
                                barCenterX - (labelResult.size.height / 2f),
                                axisY + labelGap + labelResult.size.width
                            )
                            rotate(degrees = -90f, pivot = pivot) {
                                drawText(
                                    textLayoutResult = labelResult,
                                    color = mutedTextColor,
                                    topLeft = pivot
                                )
                            }
                        } else {
                            val labelResult = measuredLabels[index].label
                            drawText(
                                textLayoutResult = labelResult,
                                color = mutedTextColor,
                                topLeft = Offset(
                                    barCenterX - (labelResult.size.width / 2),
                                    axisY + labelGap
                                )
                            )
                        }
                    }

                    // Amount, above the bar. Zero days get the placeholder line and no number.
                    if (point.amount > 0 && index % valueStride == 0) {
                        val valueColor = mutedTextColor
                        if (rotateValues) {
                            val valueResult = measuredLabels[index].rotatedValue
                            if (valueResult != null) {
                                // Same -90deg pivot geometry as the date labels: the pivot is the
                                // strip's bottom-left, so placing it one gap above the bar top and
                                // half a glyph-height left of centre grows the text upward, centred.
                                val pivot = Offset(
                                    barCenterX - (valueResult.size.height / 2f),
                                    valueBaseY - valueGap
                                )
                                rotate(degrees = -90f, pivot = pivot) {
                                    drawText(
                                        textLayoutResult = valueResult,
                                        color = valueColor,
                                        topLeft = pivot
                                    )
                                }
                            }
                        } else {
                            val valueResult = measuredLabels[index].value
                            if (valueResult != null) {
                                val valueY = valueBaseY - valueResult.size.height - valueGap
                                if (valueY > 0) {
                                    drawText(
                                        textLayoutResult = valueResult,
                                        color = valueColor,
                                        topLeft = Offset(
                                            barCenterX - (valueResult.size.width / 2),
                                            valueY
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pre-measured text for a single trend bar: its date label and (optionally) the amount drawn on
 * top, each in both the horizontal and the rotated style. Measured once in a remember block so the
 * text layout isn't recomputed every animation frame.
 */
@Immutable
private data class MeasuredBarLabels(
    val label: TextLayoutResult,
    val rotatedLabel: TextLayoutResult,
    val value: TextLayoutResult?,
    val rotatedValue: TextLayoutResult?
)


@SuppressLint("NonObservableLocale")
fun formatCurrency(amount: Double): String {
    val pattern = if (amount % 1 == 0.0) "%,.0f" else "%,.2f"
    return String.format(Locale.getDefault(), pattern, amount)
}

/**
 * Whole-currency variant for the dense summary tiles. A daily average or a projection is an
 * estimate, so rendering it to the paise ("₹34,858.93") is false precision, and the extra glyphs
 * are what pushed those tiles to truncate.
 */
fun formatCurrencyRounded(amount: Double): String = formatCurrency(round(amount))

@Composable
private fun chartContainerColor(): Color =
    MaterialTheme.colorScheme.surfaceContainer
