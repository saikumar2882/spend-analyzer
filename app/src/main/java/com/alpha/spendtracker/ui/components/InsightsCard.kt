/**
 * Insights card showing period-over-period change, daily average, projected total,
 * and the top-spending category for the current dashboard filter.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.ui.viewmodel.SpendingAnalytics
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import kotlin.math.abs

@Composable
fun InsightsCard(analytics: SpendingAnalytics) {
    // Skip when there's nothing meaningful to show.
    if (analytics.transactionCount == 0) return

    val (changePct, changeDirection) = computeChange(analytics.totalAmount, analytics.previousPeriodTotal)
    val periodLabel = periodLabel(analytics.filterType)
    val previousLabel = previousPeriodLabel(analytics.filterType)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Insights,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Period-over-period comparison row
            if (analytics.filterType != TimeFilter.ALL) {
                ComparisonRow(
                    periodLabel = periodLabel,
                    previousLabel = previousLabel,
                    changePct = changePct,
                    direction = changeDirection,
                    previousAmount = analytics.previousPeriodTotal
                )
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Metrics grid (daily avg + projection / top category)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Daily Average",
                    value = "₹${formatCurrency(analytics.dailyAverage)}",
                    accent = MaterialTheme.colorScheme.secondary
                )
                if (analytics.projectedTotal != null) {
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Projected $periodLabel",
                        value = "₹${formatCurrency(analytics.projectedTotal)}",
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                } else if (analytics.topCategory != null) {
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Top Category",
                        value = analytics.topCategory.first,
                        valueAmount = "₹${formatCurrency(analytics.topCategory.second)}",
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (analytics.projectedTotal != null && analytics.topCategory != null) {
                Spacer(modifier = Modifier.height(12.dp))
                MetricTile(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Top Category",
                    value = analytics.topCategory.first,
                    valueAmount = "₹${formatCurrency(analytics.topCategory.second)}",
                    accent = MaterialTheme.colorScheme.primary,
                    horizontal = true
                )
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    periodLabel: String,
    previousLabel: String,
    changePct: Double?,
    direction: ChangeDirection,
    previousAmount: Double
) {
    val tint = when (direction) {
        ChangeDirection.UP -> MaterialTheme.colorScheme.error
        ChangeDirection.DOWN -> MaterialTheme.colorScheme.secondary
        ChangeDirection.FLAT, ChangeDirection.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon: ImageVector = when (direction) {
        ChangeDirection.UP -> Icons.AutoMirrored.Rounded.TrendingUp
        ChangeDirection.DOWN -> Icons.AutoMirrored.Rounded.TrendingDown
        ChangeDirection.FLAT, ChangeDirection.UNKNOWN -> Icons.AutoMirrored.Rounded.TrendingFlat
    }
    val changeText = when {
        changePct == null -> "No data for $previousLabel"
        direction == ChangeDirection.FLAT -> "Flat vs $previousLabel"
        else -> "${String.format("%.0f", abs(changePct))}% vs $previousLabel"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$periodLabel vs $previousLabel".uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₹${formatCurrency(previousAmount)} last period",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    color = tint.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = changeText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = tint
            )
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueAmount: String? = null,
    accent: Color,
    horizontal: Boolean = false
) {
    if (horizontal) {
        Row(
            modifier = modifier
                .background(
                    color = accent.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accent, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (valueAmount != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = valueAmount,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                }
            }
        }
        return
    }
    Column(
        modifier = modifier
            .background(
                color = accent.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        if (valueAmount != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = valueAmount,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
    }
}

private enum class ChangeDirection { UP, DOWN, FLAT, UNKNOWN }

private fun computeChange(current: Double, previous: Double): Pair<Double?, ChangeDirection> {
    if (previous <= 0.0) {
        return if (current > 0.0) null to ChangeDirection.UNKNOWN
        else 0.0 to ChangeDirection.FLAT
    }
    val pct = ((current - previous) / previous) * 100.0
    val dir = when {
        abs(pct) < 1.0 -> ChangeDirection.FLAT
        pct > 0 -> ChangeDirection.UP
        else -> ChangeDirection.DOWN
    }
    return pct to dir
}

private fun periodLabel(filter: TimeFilter): String = when (filter) {
    TimeFilter.DAY -> "Today"
    TimeFilter.WEEK -> "This Week"
    TimeFilter.MONTH -> "This Month"
    TimeFilter.YEAR -> "This Year"
    TimeFilter.ALL -> "All Time"
    TimeFilter.CUSTOM -> "Current Range"
}

private fun previousPeriodLabel(filter: TimeFilter): String = when (filter) {
    TimeFilter.DAY -> "Yesterday"
    TimeFilter.WEEK -> "Last Week"
    TimeFilter.MONTH -> "Last Month"
    TimeFilter.YEAR -> "Last Year"
    TimeFilter.ALL -> "Previous"
    TimeFilter.CUSTOM -> "Previous Range"
}
