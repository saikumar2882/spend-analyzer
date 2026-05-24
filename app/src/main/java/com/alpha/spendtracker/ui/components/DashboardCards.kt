/**
 * Specialized card components used specifically on the Dashboard screen.
 */
package com.alpha.spendtracker.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import java.util.Locale

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                border = if (isSelected) null
                         else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    onLentClick: (() -> Unit)? = null
) {
    val titleText = when (filterType) {
        TimeFilter.DAY -> "Today's Total Spend"
        TimeFilter.WEEK -> "This Week's Outflow"
        TimeFilter.MONTH -> "This Month's Spending"
        TimeFilter.YEAR -> "Annual Total Spent"
        TimeFilter.ALL -> "Total Aggregated Outflow"
        TimeFilter.CUSTOM -> "Custom Range Outflow"
    }

    val subtitleText = if (filterType == TimeFilter.CUSTOM && dateRange != null) {
        val sdf = java.text.SimpleDateFormat("dd MMM", Locale.getDefault())
        "${sdf.format(dateRange.first)} - ${sdf.format(dateRange.second)}"
    } else {
        "$transactionCount logs"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF5E35B1),
                            Color(0xFF7C4DFF),
                            Color(0xFF3F51B5)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF00FFCC), CircleShape)
                            )
                            Text(
                                text = titleText.uppercase(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "₹${formatCurrency(totalAmount)}",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White
                        )
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
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

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassChip(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        text = subtitleText,
                        tint = Color.White.copy(alpha = 0.95f),
                        background = Color.White.copy(alpha = 0.10f),
                        border = Color.White.copy(alpha = 0.15f)
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
fun TopAppsRankingCard(
    appBreakdown: List<Pair<String, Double>>,
    onAppClick: (String) -> Unit
) {
    if (appBreakdown.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Most Active Wallets / Apps",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val maxAmt = appBreakdown.maxOfOrNull { it.second } ?: 1.0

            appBreakdown.take(4).forEach { (app, amount) ->
                val accent = APP_PRESETS.firstOrNull { it.displayName == app }?.color ?: Color.Gray
                val fraction = (amount / maxAmt.coerceAtLeast(1.0)).toFloat()
                    .coerceIn(0f, 1f)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAppClick(app) }
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(accent, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = app,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "₹${formatCurrency(amount)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = fraction)
                                .height(6.dp)
                                .background(accent, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.Savings,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Ready to start tracking?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap 'Track Spend' below to log your transactions from Swiggy, Zepto, Paytm, and more — see summaries instantly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
