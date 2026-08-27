/**
 * Reusable card components for displaying transaction details in lists.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.icons.AppIcons
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.asMoney

@Composable
fun RecentSpendRow(
    spend: Spend,
    onClick: () -> Unit = {}
) {
    val accent = APP_COLOR_BY_NAME[spend.appName] ?: MaterialTheme.colorScheme.primary
    val isLendBorrow = spend.purpose == "Lending" || spend.purpose == "Borrowing"
    val subtitle = if (isLendBorrow) spend.notes else spend.notes.ifBlank { spend.purpose }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "recentRowScale")

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent strip
            Box(
                modifier = Modifier
                    .width(Spacing.xs)
                    .fillMaxHeight()
                    .background(accent)
            )
            Row(
                modifier = Modifier
                    .padding(Spacing.md)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    DateBadge(timestamp = spend.timestamp, color = accent)
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column {
                        SpendCardHeader(appName = spend.appName, category = spend.category, accent = accent)
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                Text(
                    text = "₹${formatCurrency(spend.amount)}",
                    style = MaterialTheme.typography.titleLarge.asMoney(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun HistorySpendCard(
    spend: Spend,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    // When non-null (a note-linked spend), tapping the card opens the source note.
    onClick: (() -> Unit)? = null
) {
    val accent = APP_COLOR_BY_NAME[spend.appName] ?: MaterialTheme.colorScheme.primary
    val isLendBorrow = spend.purpose == "Lending" || spend.purpose == "Borrowing"
    val isNoteLinked = spend.noteUuid.isNotBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // fillMaxHeight only resolves because the Row above is measured at IntrinsicSize.Min:
            // in an unbounded-height Row (a plain list item) it collapses to zero and the strip
            // never draws.
            Box(
                modifier = Modifier
                    .width(Spacing.xs)
                    .fillMaxHeight()
                    .background(accent)
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
                    DateBadge(timestamp = spend.timestamp, color = accent, size = Sizes.dateBadgeCompact)
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column {
                        SpendCardHeader(appName = spend.appName, category = spend.category, accent = accent, showNoteIcon = isNoteLinked)
                        if (!isLendBorrow) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = spend.purpose,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (spend.notes.isNotBlank()) {
                            Text(
                                text = spend.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = "₹${formatCurrency(spend.amount)}",
                        style = MaterialTheme.typography.titleLarge.asMoney(),
                        color = when (spend.purpose) {
                            "Lending" -> MaterialTheme.colorScheme.secondary
                            "Borrowing" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CardActionButton(
                            icon = Icons.Rounded.Edit,
                            contentDescription = "Edit transaction",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onEdit
                        )
                        CardActionButton(
                            icon = Icons.Rounded.Delete,
                            contentDescription = "Delete transaction",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = onDelete
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tinted icon button for the in-card row actions.
 *
 * Deliberately does not set an explicit size: `IconButton` paints a 40dp container but reserves a
 * 48dp touch target, and the `Modifier.size(34.dp)` these call sites used to pass overrode that
 * reservation — shrinking every edit/delete/restore target on the transaction and history lists
 * well below the accessible minimum.
 */
@Composable
private fun CardActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
    history: com.alpha.spendtracker.data.SpendHistory,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = APP_COLOR_BY_NAME[history.appName] ?: MaterialTheme.colorScheme.primary
    val isDeleted = history.historyType == com.alpha.spendtracker.data.HistoryType.DELETED
    
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // fillMaxHeight only resolves because the Row above is measured at IntrinsicSize.Min:
            // in an unbounded-height Row (a plain list item) it collapses to zero and the strip
            // never draws.
            Box(
                modifier = Modifier
                    .width(Spacing.xs)
                    .fillMaxHeight()
                    .background(accent)
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
                    DateBadge(timestamp = history.recordedAt, color = accent, size = Sizes.dateBadgeCompact)
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = history.appName,
                                style = MaterialTheme.typography.titleSmall,
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (history.notes.isNotBlank()) {
                            Text(
                                text = history.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
    color: Color,
    size: Dp = Sizes.dateBadge
) {
    val calendar = remember(timestamp) {
        java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    }
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH).toString()
    val month = remember(timestamp) {
        java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(timestamp)
    }

    // `size` is a *floor*, not a fixed square: at a large system font scale the day and month lines
    // together are taller than 44/48dp, and a hard `Modifier.size` cropped the month clean off the
    // bottom of the badge ("26" over a half-drawn "aug"). sizeIn lets the badge grow to whatever the
    // two lines need while staying square at the default scale.
    Surface(
        modifier = Modifier.sizeIn(minWidth = size, minHeight = size),
        shape = RoundedCornerShape(Radius.sm),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fixed scale steps rather than fractions of `size`: the old `size.value * 0.35f`
            // produced a different, off-scale font size for every call site that passed a
            // different badge size.
            Text(
                text = day,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                maxLines = 1
            )
            Text(
                text = month.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}

/**
 * Small tinted label — a category tag, a DELETED/UPDATED marker. One implementation so these stop
 * drifting apart (they previously sat at 8sp/ExtraBold and 9sp/Bold with different corner radii).
 */
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
            // Ellipsis, not the default Clip: a long category at a large font scale otherwise ran
            // off the pill mid-glyph. "Quick Commerce" becomes "Quick Com…", which still reads.
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpendCardHeader(appName: String, category: String, accent: Color, showNoteIcon: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // `fill = false` so the name takes only what it needs at the default font scale, but is the
        // first thing to give way when a larger scale makes the row wider than the card.
        Text(
            text = appName,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        CategoryBadge(category = category, accent = accent)
        // Marks a spend logged from a Note — tapping the card opens that note.
        if (showNoteIcon) {
            Icon(
                imageVector = AppIcons.Notes,
                contentDescription = "Logged from a note. Tap to open.",
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: String, accent: Color) {
    Pill(text = category, container = accent.copy(alpha = 0.15f), content = accent)
}

@Composable
fun PresetGridCard(
    preset: AppPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val surfaceColor = if (isSelected) {
        preset.color.copy(alpha = 0.18f)
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
        shape = RoundedCornerShape(Radius.md),
        border = if (isSelected) BorderStroke(2.dp, preset.color)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            // A floor, not a fixed height: at a large system font scale the two label lines exceed
            // 76dp and a hard height cropped the category line off the bottom of every tile.
            .heightIn(min = 76.dp)
            .scale(scale)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.iconInline)
                    .background(preset.color, CircleShape)
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preset.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
