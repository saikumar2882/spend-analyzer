/**
 * Standardized reusable card components for displaying transaction items across
 * Dashboard, History, and Dues screens.
 * Features uniform icon containers, crisp typography hierarchy, high-contrast subtext,
 * and semantic currency styling.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.HistoryType
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendHistory
import com.alpha.spendtracker.ui.icons.AppIcons
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.asMoney
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun parseLendBorrowNotes(rawNotes: String, appName: String): Pair<String, String> {
    val trimmed = rawNotes.trim()
    if (trimmed.contains(" - ")) {
        val parts = trimmed.split(" - ", limit = 2)
        return parts[0].trim() to parts[1].trim()
    }
    if (trimmed.isNotBlank()) {
        val appNameLower = appName.trim().lowercase()
        val isPaymentApp = appNameLower in setOf(
            "google pay", "google_pay", "gpay", "phonepe", "phone_pe", "paytm",
            "swiggy", "zomato", "zepto", "blinkit", "cash", "other platform", "other", "banking & cards"
        ) || appNameLower.contains("pay") || appNameLower.contains("cash")
        
        if (isPaymentApp || appName.isBlank()) {
            return trimmed to ""
        }
    }
    if (appName.isNotBlank()) {
        return appName.trim() to trimmed
    }
    return trimmed to ""
}

fun resolveTitleAndSubtitle(spend: Spend): Pair<String, String> {
    val isLendBorrow = spend.purpose == "Lending" || spend.purpose == "Borrowing"
    if (isLendBorrow) {
        val (personName, notes) = parseLendBorrowNotes(spend.notes, spend.appName)
        val title = personName.ifBlank { "Unknown Person" }
        val subtitle = notes.ifBlank {
            if (spend.appName.isNotBlank() && spend.appName != "Other" && spend.appName != "Cash") spend.appName else ""
        }
        return title to subtitle
    }

    return spend.appName to spend.notes.ifBlank { spend.purpose }
}

/**
 * Category/Purpose accent color resolver.
 */
@Composable
fun getCategoryAccentColor(purpose: String, category: String): Color {
    val purposeMap = getPurposeColors()
    val categoryMap = getCategoryColors()
    return purposeMap[purpose] ?: categoryMap[category] ?: MaterialTheme.colorScheme.primary
}

/**
 * Standardized Recent Activity Transaction Item Row.
 * Uniform 40.dp circular category icon container, crisp title, high contrast subtext,
 * and right-aligned tabular currency figures with semantic color indicators.
 */
@Composable
fun RecentSpendRow(
    spend: Spend,
    onClick: () -> Unit = {}
) {
    val (title, subtitle) = resolveTitleAndSubtitle(spend)
    val categoryAccent = getCategoryAccentColor(spend.purpose, spend.category)
    val appAccent = APP_COLOR_BY_NAME[spend.appName]
        ?: APP_PRESETS.find { it.displayName.equals(spend.appName, ignoreCase = true) }?.color
        ?: categoryAccent

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "recentRowScale")

    val isLending = spend.purpose.equals("Lending", ignoreCase = true)
    val isBorrowing = spend.purpose.equals("Borrowing", ignoreCase = true)

    val amountColor = when {
        isLending -> MaterialTheme.colorScheme.onSurface
        isBorrowing -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(Radius.md),
        color = Color.Transparent,
        border = null
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Left-Side Date Badge Block
            DateBadge(timestamp = spend.timestamp, size = 44.dp)

            Spacer(modifier = Modifier.width(Spacing.md))

            // 2. Middle Content Column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLending || isBorrowing) {
                            PersonInitialAvatar(
                                personName = title,
                                backgroundColor = amountColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            AppIconImage(
                                appName = spend.appName,
                                fallbackColor = appAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 28.dp) // Align under text, past icon
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // 3. Right-Side Financial & Navigation Column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "₹${formatCurrency(spend.amount)}",
                    style = MaterialTheme.typography.titleMedium.asMoney(),
                    color = amountColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DateBadgeCompactText(timestamp: Long) {
    val locale = LocalConfiguration.current.locales[0]
    val sdf = remember(timestamp, locale) { SimpleDateFormat("d MMM", locale) }
    Text(
        text = sdf.format(timestamp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Standardized History Transaction Item Card.
 */
@Composable
fun HistorySpendCard(
    spend: Spend,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (title, subtitle) = resolveTitleAndSubtitle(spend)
    val isNoteLinked = spend.noteUuid.isNotBlank()
    val categoryAccent = getCategoryAccentColor(spend.purpose, spend.category)
    val appAccent = APP_COLOR_BY_NAME[spend.appName]
        ?: APP_PRESETS.find { it.displayName.equals(spend.appName, ignoreCase = true) }?.color
        ?: categoryAccent

    val isLending = spend.purpose.equals("Lending", ignoreCase = true)
    val isBorrowing = spend.purpose.equals("Borrowing", ignoreCase = true)

    val amountColor = when {
        isLending -> MaterialTheme.colorScheme.onSurface
        isBorrowing -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick ?: onEdit,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = Color.Transparent,
        border = null
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Left-Side Date Badge Block
            DateBadge(timestamp = spend.timestamp, size = 44.dp)

            Spacer(modifier = Modifier.width(Spacing.md))

            // 2. Middle Content Column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isLending || isBorrowing) {
                            PersonInitialAvatar(
                                personName = title,
                                backgroundColor = amountColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            AppIconImage(
                                appName = spend.appName,
                                fallbackColor = appAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isNoteLinked) {
                            Icon(imageVector = AppIcons.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // 3. Right-Side Financial Column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "₹${formatCurrency(spend.amount)}",
                    style = MaterialTheme.typography.titleMedium.asMoney(),
                    color = amountColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CardActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = tint.copy(alpha = 0.14f),
            contentColor = tint
        )
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(Sizes.iconAction))
    }
}

@Composable
fun HistoryRecordCard(
    history: SpendHistory,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryAccent = getCategoryAccentColor(history.purpose, history.category)
    val isDeleted = history.historyType == HistoryType.DELETED
    
    val daysLeft = if (isDeleted) {
        val millisInDay = 24L * 60 * 60 * 1000
        val elapsed = System.currentTimeMillis() - history.recordedAt
        val remaining = (30 - (elapsed / millisInDay)).toInt()
        remaining.coerceAtLeast(0)
    } else null

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(categoryAccent)
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = Spacing.md, vertical = Spacing.md)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    DateBadge(timestamp = history.recordedAt, size = Sizes.dateBadgeCompact)
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = history.appName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Pill(
                                text = history.historyType,
                                container = if (isDeleted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                content = if (isDeleted) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (daysLeft != null) {
                            Text(
                                text = if (daysLeft == 0) "Expires today" else "$daysLeft days left",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (daysLeft <= 3) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "₹${formatCurrency(history.amount)} · ${history.purpose}",
                            style = MaterialTheme.typography.bodyMedium.asMoney(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (isDeleted) {
                        CardActionButton(
                            icon = Icons.Rounded.Restore,
                            contentDescription = "Restore record",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onRestore
                        )
                    }
                    CardActionButton(
                        icon = if (isDeleted) Icons.Rounded.DeleteForever else Icons.Rounded.Delete,
                        contentDescription = if (isDeleted) "Permanently delete" else "Remove history",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
fun DateBadge(
    timestamp: Long,
    size: Dp = Sizes.dateBadge
) {
    val calendar = remember(timestamp) {
        Calendar.getInstance().apply { timeInMillis = timestamp }
    }
    val day = calendar.get(Calendar.DAY_OF_MONTH).toString()
    val month = remember(timestamp) {
        SimpleDateFormat("MMM", Locale.getDefault()).format(timestamp)
    }

    Surface(
        modifier = Modifier.sizeIn(minWidth = size, minHeight = size),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = month,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun Pill(
    text: String,
    container: Color,
    content: Color
) {
    Surface(color = container, shape = RoundedCornerShape(Radius.xxs)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PresetGridCard(
    preset: AppPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val surfaceColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "presetScale")

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        color = surfaceColor,
        shape = RoundedCornerShape(14.dp),
        // The one outline kept in the app: picking from a grid needs an unambiguous affordance.
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            // heightIn, never height: the label has to survive a large system font scale.
            .heightIn(min = 86.dp)
            .scale(scale)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIconImage(
                appName = preset.displayName,
                fallbackColor = preset.color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Reusable swipeable wrapper for log cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableLogCard(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        onDismiss = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> onEdit()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> {}
            }
            coroutineScope.launch {
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
        },
        backgroundContent = {
            val isSwipingRight = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd || dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val isSwipingLeft = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart || dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart

            val color = when {
                isSwipingRight -> MaterialTheme.colorScheme.primaryContainer
                isSwipingLeft -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            val alignment = when {
                isSwipingRight -> Alignment.CenterStart
                isSwipingLeft -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when {
                isSwipingRight -> Icons.Rounded.Edit
                isSwipingLeft -> Icons.Rounded.Delete
                else -> null
            }
            val tint = when {
                isSwipingRight -> MaterialTheme.colorScheme.onPrimaryContainer
                isSwipingLeft -> MaterialTheme.colorScheme.onErrorContainer
                else -> Color.Unspecified
            }
            val text = when {
                isSwipingRight -> "Edit"
                isSwipingLeft -> "Delete"
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, shape = RoundedCornerShape(Radius.md))
                    .padding(horizontal = Spacing.md),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isSwipingRight) {
                            Icon(icon, contentDescription = text, tint = tint, modifier = Modifier.size(20.dp))
                            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = tint)
                        } else {
                            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = tint)
                            Icon(icon, contentDescription = text, tint = tint, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        content = {
            content()
        }
    )
}
