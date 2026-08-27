/**
 * Specialized card components used specifically on the Dashboard screen.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.ui.theme.BrandAccentMint
import com.alpha.spendtracker.ui.theme.BrandGradientEnd
import com.alpha.spendtracker.ui.theme.BrandGradientMid
import com.alpha.spendtracker.ui.theme.BrandGradientStart
import com.alpha.spendtracker.ui.theme.MotionDuration
import com.alpha.spendtracker.ui.theme.OnGradientMoneyDown
import com.alpha.spendtracker.ui.theme.OnGradientMoneyUp
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.asMoney
import com.alpha.spendtracker.ui.theme.isAppInDarkTheme
import com.alpha.spendtracker.ui.theme.motionDuration
import com.alpha.spendtracker.ui.theme.rememberReduceMotion
import com.alpha.spendtracker.ui.viewmodel.SpendingAnalytics
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import com.alpha.spendtracker.ui.viewmodel.TrendPoint
import kotlin.math.abs

/**
 * Height floor for the dense segmented controls (time filter, chart toggle). Material's own
 * segmented buttons sit at 40dp; 44 keeps them comfortably tappable without eating the vertical
 * room a full 48dp row would take above the hero.
 */
private val SegmentedCellHeight = 44.dp

/**
 * Hairline gap between segmented cells. Deliberately off the 4dp spacing grid: this is a seam
 * between adjacent surfaces, not layout rhythm, and 4dp reads as a visible gutter here.
 */
private val SegmentedGap = 2.dp

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
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(SegmentedGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (type, label) ->
                val isSelected = selected == type
                Surface(
                    onClick = {
                        if (type == TimeFilter.CUSTOM) onCustomClick()
                        else onSelect(type)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SegmentedCellHeight),
                    shape = RoundedCornerShape(Radius.sm),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shadowElevation = if (isSelected) 4.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
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

    val reduceMotion = rememberReduceMotion()

    // Count the hero total up to its current value. animateFloatAsState animates from the previous
    // composed value to the new target, so the number rolls up on first appearance and whenever the
    // total changes (e.g. switching the time filter). Tabular digits keep the width stable.
    val animatedTotal by animateFloatAsState(
        targetValue = totalAmount.toFloat(),
        animationSpec = tween(motionDuration(MotionDuration.LONG, reduceMotion)),
        label = "hero_total_countup"
    )
    val displayTotal = if (reduceMotion) totalAmount else animatedTotal.toDouble()

    val isDark = isAppInDarkTheme
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
        shape = RoundedCornerShape(Radius.xl),
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
            // Decorative blurred orbs. These must be offset, not padded: `size().padding()` shrinks
            // the drawable area instead of moving it, so a 200dp start padding inside a 180dp box
            // collapsed the orb to zero width and neither one ever rendered.
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(x = 200.dp)
                    .background(Color.White.copy(alpha = 0.10f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(y = 140.dp)
                    .background(BrandAccentMint.copy(alpha = 0.18f), CircleShape)
            )

            Column(modifier = Modifier.padding(Spacing.xl)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(Spacing.sm)
                            .background(BrandAccentMint, CircleShape)
                    )
                    Text(
                        text = titleText.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "₹",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(end = Spacing.xs, bottom = Spacing.sm)
                        )
                        Text(
                            // Rounded, not exact: the count-up animates through fractional values,
                            // and formatCurrency switches to two decimal places for anything
                            // non-whole — so the headline flickered between "₹218.86" and
                            // "₹30,361" widths on every frame, which tabular figures cannot fix.
                            text = formatCurrencyRounded(displayTotal),
                            // Weighted so a large system font scale shrinks the figure's box
                            // instead of pushing the delta chip off the card.
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.displaySmall.asMoney(),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                                    .padding(Spacing.md)
                                    .size(Sizes.iconAction),
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.ml))
                HorizontalDivider(color = Color.White.copy(alpha = 0.18f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.lg))

                // Two chips of unbounded text on one row overflowed the card as soon as the system
                // font scale grew. Each now owns half the row and truncates inside it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassChip(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        text = subtitleText,
                        onClick = onTransactionsClick,
                        modifier = Modifier.weight(1f)
                    )

                    GlassChip(
                        icon = Icons.Rounded.Handshake,
                        text = "Dues",
                        onClick = onLentClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Translucent pill for use on the hero gradient. Always white-on-glass — the surrounding gradient
 * is mid-dark at every stop, so `colorScheme` roles would not have reliable contrast here.
 */
@Composable
private fun GlassChip(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Radius.sm)
    val border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f))
    val background = Color.White.copy(alpha = 0.16f)

    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = SegmentedCellHeight)
                .padding(horizontal = Spacing.md)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.iconInline), tint = Color.White)
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, color = background, shape = shape, border = border, content = { content() })
    } else {
        Surface(modifier = modifier, color = background, shape = shape, border = border, content = { content() })
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
        )
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.xxl)
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
                    modifier = Modifier.size(Spacing.xxl),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            Text(
                text = "Ready to start tracking?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Tap 'Track Spend' below to log transactions from Swiggy, Zepto, Paytm, and more — see summaries instantly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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
        up -> OnGradientMoneyUp
        else -> OnGradientMoneyDown
    }
    // Locale comes from LocalConfiguration, not Locale.getDefault(): the latter is not observable
    // state, so the label would keep its old formatting after a locale change.
    val locale = LocalConfiguration.current.locales[0]
    val label = if (flat) {
        "Flat vs last"
    } else {
        String.format(locale, "%.0f%% vs last", abs(deltaPct))
    }
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Icon(icon, contentDescription = null, tint = arrowTint, modifier = Modifier.size(Sizes.iconInline))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Labels are single words: at a third of the screen width, "TOP CATEGORY" truncated to
        // "TOP CATEG…", which reads worse than just naming the thing.
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Daily Avg",
            value = "₹${formatCurrencyRounded(analytics.dailyAverage)}",
            isMoney = true,
            accent = MaterialTheme.colorScheme.secondary
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Category",
            value = analytics.topCategory?.first ?: "—",
            isMoney = false,
            accent = MaterialTheme.colorScheme.primary
        )
        if (analytics.projectedTotal != null) {
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Projected",
                value = "₹${formatCurrencyRounded(analytics.projectedTotal)}",
                isMoney = true,
                accent = MaterialTheme.colorScheme.tertiary
            )
        } else {
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Transactions",
                value = analytics.transactionCount.toString(),
                isMoney = true,
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
    isMoney: Boolean,
    accent: Color
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Radius.md))
            .padding(Spacing.md)
    ) {
        // Value first, and at the larger step: the number is what the user came to read, the label
        // only says which number it is.
        Text(
            text = value,
            style = if (isMoney) {
                MaterialTheme.typography.titleMedium.asMoney()
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accent, CircleShape)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * "Where it went" — a single card hosting the category donut and the spending trend, with a
 * segmented toggle to switch between them (replaces two separately-stacked chart cards).
 */
@Composable
fun WhereItWentCard(
    categoryBreakdown: Map<String, Double>,
    purposeBreakdown: Map<String, Double>,
    trendPoints: List<TrendPoint>,
    onCategoryClick: (String) -> Unit,
    onPurposeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // The toggle sits on its own full-width row rather than sharing one with the title:
            // three labels beside a heading truncate on a 375dp screen, and full width gives each
            // segment a comfortable target.
            Text(
                text = "Where it went",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            ChartToggle(selected = tab, onSelect = { tab = it })
            Spacer(modifier = Modifier.height(Spacing.md))
            AnimatedContent(targetState = tab, label = "where_it_went") { current ->
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
    val labels = listOf("Categories", "Purpose", "Trend")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(Radius.sm)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(SegmentedGap)
        ) {
            labels.forEachIndexed { index, label ->
                val isSel = index == selected
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SegmentedCellHeight),
                    shape = RoundedCornerShape(Radius.xs),
                    color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ellipsis rather than the default Clip: a segment is a fixed fraction of
                        // the row, so at a large font scale "Categories" has to become "Cat…"
                        // instead of running off the edge of its cell mid-letter.
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
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
