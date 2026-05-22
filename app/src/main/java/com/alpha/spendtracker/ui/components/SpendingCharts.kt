/**
 * Visual components for rendering spending analytics through charts and graphs.
 */
package com.alpha.spendtracker.ui.components

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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.ui.viewmodel.TrendPoint
import java.util.Locale

// Dynamic Colors for Spending Categories / Apps
val CategoryColors = mapOf(
    "UPI Apps" to Color(0xFF3B82F6),        // Google Pay, PhonePe (Blue)
    "Quick Commerce" to Color(0xFFF97316),  // Swiggy, Zepto (Orange)
    "E-Commerce" to Color(0xFFEC4899),      // Amazon, Flipkart (Pink)
    "Banking & Cards" to Color(0xFF8B5CF6),  // ICICI, SBI Credit Cards (Purple)
    "Friend Lending" to Color(0xFF10B981),   // Friend Lending (Emerald Green)
    "Other" to Color(0xFF6B7280)            // Generic Other (Gray)
)

/**
 * A beautiful, custom Canvas-drawn Pie/Donut Chart for category breakdown
 */
@Composable
fun SpendingDonutChart(
    categoryBreakdown: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    if (categoryBreakdown.isEmpty()) {
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No category data to display",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val total = categoryBreakdown.values.sum()
    val items = categoryBreakdown.toList().sortedByDescending { it.second }

    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val progressFactor by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = 800),
        label = "diagram_draw"
    )

    LaunchedEffect(categoryBreakdown) {
        animatedProgress = 1f
    }

    Row(
        modifier = modifier
            .background(chartContainerColor(), shape = RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Doughnut Canvas
        Box(
            modifier = Modifier
                .size(130.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)
                val rectSize = Size(radius * 2, radius * 2)
                val topLeft = Offset(center.x - radius, center.y - radius)

                var startAngle = -90f // Start from the top

                items.forEach { (cat, amount) ->
                    val rawSweepAngle = if (total > 0) ((amount / total) * 360f).toFloat() * progressFactor else 0f
                    val sweepAngle = if (rawSweepAngle.isNaN() || rawSweepAngle < 0f) 0f else rawSweepAngle
                    val color = CategoryColors[cat] ?: CategoryColors["Other"]!!

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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Legend list
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.take(4).forEach { (category, amount) ->
                val color = CategoryColors[category] ?: CategoryColors["Other"]!!
                val percent = if (total > 0) (amount / total * 100).toInt() else 0

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
                                .size(10.dp)
                                .background(color, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (items.size > 4) {
                val remainingAmount = items.drop(4).sumOf { it.second }
                val remainingPercent = if (total > 0) (remainingAmount / total * 100).toInt() else 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.Gray, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Others",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "$remainingPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Custom Canvas-drawn Bar Chart for spending trends over days, weeks, months or years
 */
@Composable
fun SpendingTrendBarChart(
    trendPoints: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    if (trendPoints.isEmpty()) {
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
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

    val maxVal = trendPoints.maxOfOrNull { it.amount } ?: 1.0
    val displayMax = if (maxVal == 0.0) 100.0 else maxVal * 1.15 // 15% padding so bars don't clip at top

    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val progressFactor by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = 800),
        label = "bar_draw"
    )

    LaunchedEffect(trendPoints) {
        animatedProgress = 1f
    }

    val textMeasurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .background(chartContainerColor(), shape = RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Spending Trend Projections",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // Leave bottom space for labels, and left/right padding
            val bottomLabelHeight = 24.dp.toPx()
            val availableHeight = canvasHeight - bottomLabelHeight
            val leftPadding = 8.dp.toPx()
            val rightPadding = 8.dp.toPx()
            val chartRangeWidth = canvasWidth - leftPadding - rightPadding

            // Draw Y Grid lines (3 horizontal guideline bars)
            val gridSteps = 3
            for (i in 0..gridSteps) {
                val y = (availableHeight / gridSteps) * i
                drawLine(
                    color = gridLineColor,
                    start = Offset(leftPadding, y),
                    end = Offset(canvasWidth - rightPadding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw Bars
            val barCount = trendPoints.size
            if (barCount > 0) {
                val totalGapWidthPct = 0.35f // 35% gap of the total slot width
                val slotWidth = chartRangeWidth / barCount
                val gapWidth = slotWidth * totalGapWidthPct
                val barWidth = slotWidth - gapWidth

                trendPoints.forEachIndexed { index, point ->
                    val xStart = leftPadding + (index * slotWidth) + (gapWidth / 2)
                    
                    // Height calculation based on animated scale
                    val barHeight = ((point.amount / displayMax) * availableHeight).toFloat() * progressFactor
                    val yStart = availableHeight - barHeight

                    if (point.amount > 0) {
                        // Draw beautiful rounded bar using Brush
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    barColor.copy(alpha = 0.95f),
                                    barColor.copy(alpha = 0.5f)
                                )
                            ),
                            topLeft = Offset(xStart, yStart),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                        )
                    } else {
                        // Tiny dot/line placeholder for zero spend on that day
                        drawRoundRect(
                            color = outlineVariantColor.copy(alpha = 0.4f),
                            topLeft = Offset(xStart, availableHeight - 2.dp.toPx()),
                            size = Size(barWidth, 2.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                        )
                    }

                    // Label drawing
                    val labelResult = textMeasurer.measure(
                        text = point.label,
                        style = textStyle
                    )
                    
                    // Center labels under bars
                    val labelX = xStart + (barWidth / 2) - (labelResult.size.width / 2)
                    val labelY = availableHeight + 6.dp.toPx()

                    drawText(
                        textLayoutResult = labelResult,
                        topLeft = Offset(labelX, labelY)
                    )

                    // Numerical value drawing on top of bar
                    if (point.amount > 0) {
                        val valueText = if (point.amount >= 1000) {
                            String.format(Locale.getDefault(), "%.1fk", point.amount / 1000)
                        } else {
                            point.amount.toInt().toString()
                        }
                        
                        val valueResult = textMeasurer.measure(
                            text = valueText,
                            style = textStyle.copy(
                                fontWeight = FontWeight.Bold,
                                color = barColor.copy(alpha = 0.8f),
                                fontSize = 8.sp
                            )
                        )
                        
                        val valueX = xStart + (barWidth / 2) - (valueResult.size.width / 2)
                        val valueY = yStart - valueResult.size.height - 2.dp.toPx()
                        
                        if (valueY > 0) { // Only draw if there's space at the top
                            drawText(
                                textLayoutResult = valueResult,
                                topLeft = Offset(valueX, valueY)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatCurrency(amount: Double): String {
    val pattern = if (amount % 1 == 0.0) "%,.0f" else "%,.2f"
    return String.format(Locale.getDefault(), pattern, amount)
}

@Composable
private fun chartContainerColor(): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
