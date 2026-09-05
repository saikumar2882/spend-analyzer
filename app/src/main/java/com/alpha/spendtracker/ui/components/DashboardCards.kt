/**
 * Specialized card components used specifically on the Dashboard screen.
 * Refined for financial clarity, crisp contrast, card elevation, and standardized hierarchy.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.ui.theme.MotionDuration
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.asMoney
import com.alpha.spendtracker.ui.theme.motionDuration
import com.alpha.spendtracker.ui.theme.rememberReduceMotion
import com.alpha.spendtracker.ui.viewmodel.SpendingAnalytics
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import com.alpha.spendtracker.ui.viewmodel.TrendPoint
import java.text.SimpleDateFormat
import kotlin.math.abs

private val SegmentedCellHeight = 40.dp
private val SegmentedGap = 4.dp

@Composable
fun TimeFilterSelectorRow(
    selected: TimeFilter,
    onSelect: (TimeFilter) -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val options = remember {
        listOf(
            TimeFilter.DAY to "Today",
            TimeFilter.WEEK to "Week",
            TimeFilter.MONTH to "Month",
            TimeFilter.YEAR to "Year",
            TimeFilter.ALL to "All",
            TimeFilter.CUSTOM to "Custom"
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(SegmentedGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (type, label) ->
                val isSelected = selected == type
                Surface(
                    onClick = {
                        runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        if (type == TimeFilter.CUSTOM) onCustomClick()
                        else onSelect(type)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SegmentedCellHeight),
                    shape = RoundedCornerShape(12.dp),
                    // Solid brand fill is the whole selected signal — no outline on top of it.
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    border = null
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Refined Dashboard Overview Hero Card (Balance & Spending Metrics).
 * Designed with high contrast, tabular currency typography, clear card elevation,
 * subtle stroke border, and actionable period comparison metrics.
 */
@Composable
fun TotalSpentHeroCard(
    filterType: TimeFilter,
    totalAmount: Double,
    transactionCount: Int,
    dateRange: Pair<Long, Long>? = null,
    periodDeltaPct: Double? = null,
    onLentClick: (() -> Unit)? = null,
    onTransactionsClick: (() -> Unit)? = null
) {
    val titleText = when (filterType) {
        TimeFilter.DAY -> "Total spent today"
        TimeFilter.WEEK -> "Total spent this week"
        TimeFilter.MONTH -> "Total spent this month"
        TimeFilter.YEAR -> "Total spent this year"
        TimeFilter.ALL -> "Total spent all time"
        TimeFilter.CUSTOM -> "Total spent in range"
    }

    val subtitleText = if (filterType == TimeFilter.CUSTOM && dateRange != null) {
        val locale = LocalConfiguration.current.locales[0]
        val sdf = remember(locale) { SimpleDateFormat("dd MMM", locale) }
        "${sdf.format(dateRange.first)} - ${sdf.format(dateRange.second)}"
    } else {
        "$transactionCount transactions"
    }

    val reduceMotion = rememberReduceMotion()

    val animatedTotal by animateFloatAsState(
        targetValue = totalAmount.toFloat(),
        animationSpec = tween(motionDuration(MotionDuration.LONG, reduceMotion)),
        label = "hero_total_countup"
    )
    val displayTotal = if (reduceMotion) totalAmount else animatedTotal.toDouble()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹ ${formatCurrencyRounded(displayTotal)}",
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.displaySmall.asMoney(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (periodDeltaPct != null) {
                    HeroDeltaChip(periodDeltaPct)
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = Spacing.xs)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }

                Surface(
                    onClick = { onTransactionsClick?.invoke() },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Text(
                            text = "View details >",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroDeltaChip(deltaPct: Double) {
    val locale = LocalConfiguration.current.locales[0]
    val absPct = abs(deltaPct)
    val isDecrease = deltaPct <= 0
    val arrow = if (isDecrease) "↓↓" else "↑↑"
    val pctColor = if (isDecrease) MaterialTheme.colorScheme.secondary
                   else MaterialTheme.colorScheme.error
    val pctText = String.format(locale, "%s %.1f%%", arrow, absPct)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        border = null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = pctText,
                style = MaterialTheme.typography.labelMedium.asMoney().copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = pctColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "vs last period",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = null
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = Spacing.xxl, horizontal = Spacing.lg)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = "No transactions in this period",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Tap + below to log a spend, or try changing your time horizon filter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Compact 3-stat row:
 * Card 1: Value "₹468", Subtitle "Daily average"
 * Card 2: Value "UPI Apps", Subtitle "Top channel"
 * Card 3: Value "₹170,795", Subtitle "Projected spend"
 */
@Composable
fun QuickStatsRow(analytics: SpendingAnalytics, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            value = "₹${formatCurrencyRounded(analytics.dailyAverage)}",
            label = "Daily average",
            showSparkline = true
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = analytics.topCategory?.first ?: "UPI Apps",
            label = "Top channel",
            showSparkline = false
        )
        if (analytics.projectedTotal != null) {
            StatTile(
                modifier = Modifier.weight(1f),
                value = "₹${formatCurrencyRounded(analytics.projectedTotal)}",
                label = "Projected spend",
                showSparkline = true
            )
        } else {
            StatTile(
                modifier = Modifier.weight(1f),
                value = analytics.transactionCount.toString(),
                label = "Transactions",
                showSparkline = false
            )
        }
    }
}

@Composable
fun SmoothSparkline(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.5.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        if (dataPoints.size < 2) return@Canvas

        val width = size.width
        val height = size.height

        val min = dataPoints.minOrNull() ?: 0f
        val max = dataPoints.maxOrNull() ?: 1f
        val range = if (max - min == 0f) 1f else max - min

        val points = dataPoints.mapIndexed { index, value ->
            val x = index * (width / (dataPoints.size - 1))
            val y = height - ((value - min) / range * height)
            androidx.compose.ui.geometry.Offset(x, y)
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val controlX1 = p1.x + (p2.x - p1.x) / 2f
                val controlY1 = p1.y
                val controlX2 = p1.x + (p2.x - p1.x) / 2f
                val controlY2 = p2.y
                cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    showSparkline: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 14.dp, end = 14.dp, bottom = 12.dp)
        ) {
            val valueStyle = if (value.length > 10) {
                MaterialTheme.typography.titleMedium.asMoney()
            } else {
                MaterialTheme.typography.titleLarge.asMoney()
            }
            Text(
                text = value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showSparkline) {
                Spacer(modifier = Modifier.height(10.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(0f, h * 0.95f)
                        cubicTo(
                            w * 0.3f, h * 1.1f,
                            w * 0.5f, h * -0.2f,
                            w, h * 0.1f
                        )
                    }
                    drawPath(
                        path = path,
                        color = accent.copy(alpha = 0.85f),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun WhereItWentCard(
    categoryBreakdown: Map<String, Double>,
    purposeBreakdown: Map<String, Double>,
    trendPoints: List<TrendPoint>,
    onCategoryClick: (String) -> Unit,
    onPurposeClick: (String) -> Unit,
    onViewAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.lg),
        border = null
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spending Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (onViewAllClick != null) {
                    Text(
                        text = "View all >",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onViewAllClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            ChartToggle(selected = tab, onSelect = { tab = it })
            Spacer(modifier = Modifier.height(Spacing.md))
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f))
                        .togetherWith(fadeOut(animationSpec = tween(180)))
                },
                label = "where_it_went"
            ) { current ->
                when (current) {
                    0 -> SpendingDonutChart(
                        categoryBreakdown = categoryBreakdown,
                        modifier = Modifier.fillMaxWidth(),
                        inCard = true,
                        onCategoryClick = onCategoryClick
                    )
                    1 -> SpendingDonutChart(
                        categoryBreakdown = purposeBreakdown,
                        modifier = Modifier.fillMaxWidth(),
                        inCard = true,
                        usePurposeColors = true,
                        onCategoryClick = onPurposeClick
                    )
                    else -> SpendingTrendBarChart(
                        trendPoints = trendPoints,
                        modifier = Modifier.fillMaxWidth(),
                        inCard = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartToggle(selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val labels = listOf("Payment Apps", "Purpose", "Trend")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        border = null
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(SegmentedGap)
        ) {
            labels.forEachIndexed { index, label ->
                val isSel = index == selected
                Surface(
                    onClick = {
                        runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        onSelect(index)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SegmentedCellHeight),
                    shape = RoundedCornerShape(12.dp),
                    // Solid brand fill is the whole selected signal — no outline on top of it.
                    color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                    border = null
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
