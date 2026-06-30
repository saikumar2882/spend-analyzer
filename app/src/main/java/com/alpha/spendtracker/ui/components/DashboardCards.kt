/**
 * Specialized card components used specifically on the Dashboard screen.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.ui.theme.BrandAccentMint
import com.alpha.spendtracker.ui.theme.BrandGradientEnd
import com.alpha.spendtracker.ui.theme.BrandGradientMid
import com.alpha.spendtracker.ui.theme.BrandGradientStart
import com.alpha.spendtracker.ui.viewmodel.SpendingAnalytics
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import com.alpha.spendtracker.ui.viewmodel.TrendPoint
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.abs

@Composable
fun TimeFilterSelectorRow(
    selected: TimeFilter,
    onSelect: (TimeFilter) -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (type, label) ->
                val isSelected = selected == type
                Surface(
                    onClick = {
                        if (type == TimeFilter.CUSTOM) onCustomClick()
                        else onSelect(type)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    shadowElevation = if (isSelected) 4.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

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
        TimeFilter.DAY -> "Today's Spend"
        TimeFilter.WEEK -> "This Week"
        TimeFilter.MONTH -> "This Month"
        TimeFilter.YEAR -> "This Year"
        TimeFilter.ALL -> "All Time"
        TimeFilter.CUSTOM -> "Custom Range"
    }

    val subtitleText = if (filterType == TimeFilter.CUSTOM && dateRange != null) {
        val locale = LocalConfiguration.current.locales[0]
        val sdf = remember(locale) { java.text.SimpleDateFormat("dd MMM", locale) }
        "${sdf.format(dateRange.first)} - ${sdf.format(dateRange.second)}"
    } else {
        "$transactionCount transactions"
    }

    // Respect the system "remove animations" accessibility setting: when on, the total snaps to
    // its final value with no count-up.
    val context = LocalContext.current
    val reduceMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) == 0f

    // Count the hero total up to its current value. animateFloatAsState animates from the previous
    // composed value to the new target, so the number rolls up on first appearance and whenever the
    // total changes (e.g. switching the time filter). Tabular digits keep the width stable.
    val animatedTotal by animateFloatAsState(
        targetValue = totalAmount.toFloat(),
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 600),
        label = "hero_total_countup"
    )
    val displayTotal = if (reduceMotion) totalAmount else animatedTotal.toDouble()

    val isDark = isSystemInDarkTheme()
    val gradientColors = remember(isDark) {
        if (isDark) {
            listOf(BrandGradientStart, BrandGradientMid, BrandGradientEnd)
        } else {
            // Slightly softer for light mode
            listOf(
                BrandGradientStart.copy(alpha = 0.92f),
                BrandGradientMid.copy(alpha = 0.92f),
                BrandGradientEnd.copy(alpha = 0.92f)
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 12.dp else 4.dp),
        border = if (!isDark) BorderStroke(1.dp, BrandGradientStart.copy(alpha = 0.1f)) else null
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(colors = gradientColors))
        ) {
            // Decorative blurred orbs
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .padding(start = 200.dp, top = 0.dp)
                    .background(Color.White.copy(alpha = 0.10f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .padding(start = 0.dp, top = 140.dp)
                    .background(BrandAccentMint.copy(alpha = 0.18f), CircleShape)
            )

            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(BrandAccentMint, CircleShape)
                    )
                    Text(
                        text = titleText.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "₹",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(end = 4.dp, bottom = 6.dp)
                            )
                            Text(
                                text = formatCurrency(displayTotal),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp,
                                    lineHeight = 40.sp,
                                    letterSpacing = (-1).sp
                                ),
                                color = Color.White
                            )
                        }
                    }

                    if (periodDeltaPct != null) {
                        HeroDeltaChip(periodDeltaPct)
                    } else {
                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(28.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.18f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassChip(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        text = subtitleText,
                        tint = Color.White,
                        background = Color.White.copy(alpha = 0.16f),
                        border = Color.White.copy(alpha = 0.22f),
                        onClick = onTransactionsClick
                    )

                    GlassChip(
                        icon = Icons.Rounded.Handshake,
                        text = "Lend / Borrow",
                        tint = Color.White,
                        background = Color.White.copy(alpha = 0.16f),
                        border = Color.White.copy(alpha = 0.22f),
                        onClick = onLentClick
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    background: Color,
    border: Color,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Surface(
        color = background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, border),
        modifier = clickModifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = tint
            )
        }
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
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
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Savings,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ready to start tracking?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap 'Track Spend' below to log transactions from Swiggy, Zepto, Paytm, and more — see summaries instantly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Period-over-period delta pill shown on the hero. Mint arrow = spending down (good),
 * soft coral = spending up. Rendered on the gradient, so text stays white.
 */
@Composable
private fun HeroDeltaChip(deltaPct: Double) {
    val flat = abs(deltaPct) <= 1.0
    val up = deltaPct > 0
    val icon = when {
        flat -> Icons.AutoMirrored.Rounded.TrendingFlat
        up -> Icons.AutoMirrored.Rounded.TrendingUp
        else -> Icons.AutoMirrored.Rounded.TrendingDown
    }
    val arrowTint = when {
        flat -> Color.White
        up -> Color(0xFFFFB4BC)      // soft coral — spending increased
        else -> BrandAccentMint       // mint — spending decreased
    }
    val label = if (flat) "Flat vs last" else "${String.format("%.0f", abs(deltaPct))}% vs last"
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = arrowTint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

/**
 * Compact 3-stat row shown directly under the hero: daily average, top category, and
 * projected total (falling back to transaction count when no projection is available).
 */
@Composable
fun QuickStatsRow(analytics: SpendingAnalytics, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Daily Avg",
            value = "₹${formatCurrency(analytics.dailyAverage)}",
            accent = MaterialTheme.colorScheme.secondary
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Top Category",
            value = analytics.topCategory?.first ?: "—",
            accent = MaterialTheme.colorScheme.primary
        )
        if (analytics.projectedTotal != null) {
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Projected",
                value = "₹${formatCurrency(analytics.projectedTotal)}",
                accent = MaterialTheme.colorScheme.tertiary
            )
        } else {
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Transactions",
                value = analytics.transactionCount.toString(),
                accent = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accent, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * "Where it went" — a single card hosting the category donut and the spending trend, with a
 * segmented toggle to switch between them (replaces two separately-stacked chart cards).
 */
@Composable
fun WhereItWentCard(
    categoryBreakdown: Map<String, Double>,
    trendPoints: List<TrendPoint>,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Where it went",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                ChartToggle(selected = tab, onSelect = { tab = it })
            }
            Spacer(modifier = Modifier.height(14.dp))
            AnimatedContent(targetState = tab, label = "where_it_went") { current ->
                if (current == 0) {
                    SpendingDonutChart(
                        categoryBreakdown = categoryBreakdown,
                        modifier = Modifier.fillMaxWidth(),
                        inCard = true,
                        onCategoryClick = onCategoryClick
                    )
                } else {
                    SpendingTrendBarChart(
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
    val labels = listOf("Categories", "Trend")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            labels.forEachIndexed { index, label ->
                val isSel = index == selected
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
