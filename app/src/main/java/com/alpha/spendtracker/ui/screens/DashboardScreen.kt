/**
 * The main overview screen displaying spending summaries, charts, and recent activity.
 */
package com.alpha.spendtracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.BudgetEditorDialog
import com.alpha.spendtracker.ui.components.DateRangePickerModal
import com.alpha.spendtracker.ui.components.EmptyStateCard
import com.alpha.spendtracker.ui.components.MonthlyBudgetCard
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.components.WrappedBanner
import com.alpha.spendtracker.ui.components.WrappedBannerInfo
import com.alpha.spendtracker.ui.components.QuickStatsRow
import com.alpha.spendtracker.ui.components.RecentSpendRow
import com.alpha.spendtracker.ui.components.SwipeableLogCard
import com.alpha.spendtracker.ui.components.TimeFilterSelectorRow
import com.alpha.spendtracker.ui.components.TotalSpentHeroCard
import com.alpha.spendtracker.ui.components.UpcomingBillsCard
import com.alpha.spendtracker.ui.components.WhereItWentCard
import com.alpha.spendtracker.ui.components.formatCurrency
import com.alpha.spendtracker.ui.icons.AppIcons
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.alpha.spendtracker.ui.theme.WindowWidthSizeClass
import com.alpha.spendtracker.ui.theme.rememberPressScale
import com.alpha.spendtracker.ui.theme.rememberWindowWidthSizeClass
import com.alpha.spendtracker.ui.viewmodel.SpendingAnalytics
import com.alpha.spendtracker.ui.viewmodel.TimeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    currentFilter: TimeFilter,
    analytics: SpendingAnalytics,
    recentSpends: List<Spend>,
    upcomingBills: List<RecurringBill> = emptyList(),
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onFilterSelect: (TimeFilter) -> Unit,
    onCustomRangeSelect: (Long, Long) -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onShowAllClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onLentClick: () -> Unit,
    onTransactionsClick: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onNotesClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onMarkBillPaid: (RecurringBill) -> Unit = {},
    onRecurringBillsClick: () -> Unit = {},
    onEditSpend: ((Spend) -> Unit)? = null,
    onDeleteSpend: ((Spend) -> Unit)? = null,
    // Non-null in the first days of a month while last month's Wrapped hasn't been looked at.
    wrappedBanner: WrappedBannerInfo? = null,
    currency: String = "",
    monthlyBudget: Double = 0.0,
    monthlySpent: Double = 0.0,
    onUpdateMonthlyBudget: (Double) -> Unit = {},
    onWrappedBannerClick: () -> Unit = {},
    onWrappedBannerDismiss: () -> Unit = {}
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showBudgetEditorDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val showCompactHeader by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 100
        }
    }

    if (showDatePicker) {
        DateRangePickerModal(
            initialStart = null,
            initialEnd = null,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                onCustomRangeSelect(start, end)
                showDatePicker = false
            }
        )
    }

    val budgetUpdatedMsg = stringResource(R.string.budget_updated_success)
    val budgetRemovedMsg = stringResource(R.string.budget_removed_success)

    if (showBudgetEditorDialog) {
        BudgetEditorDialog(
            currentBudget = monthlyBudget,
            currency = currency.ifBlank { "₹" },
            onSave = { newBudget ->
                onUpdateMonthlyBudget(newBudget)
                showBudgetEditorDialog = false
                if (newBudget > 0.0) {
                    onShowNotification(budgetUpdatedMsg, NotificationType.SUCCESS)
                } else {
                    onShowNotification(budgetRemovedMsg, NotificationType.INFO)
                }
            },
            onDismiss = { showBudgetEditorDialog = false }
        )
    }

    val windowWidthClass = rememberWindowWidthSizeClass()
    val isCompact = windowWidthClass == WindowWidthSizeClass.COMPACT

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCompact) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(top = Spacing.md, bottom = 120.dp)
            ) {
                item {
                    DashboardHeader(
                        themePreference = themePreference,
                        onCycleTheme = onCycleTheme,
                        onSettingsClick = onSettingsClick,
                        onAiAssistantClick = onAiAssistantClick,
                        onNotesClick = onNotesClick
                    )
                }

                if (wrappedBanner != null) {
                    item(key = "wrapped-banner") {
                        WrappedBanner(
                            info = wrappedBanner,
                            currency = currency,
                            onOpen = onWrappedBannerClick,
                            onDismiss = onWrappedBannerDismiss
                        )
                    }
                }

                item {
                    TimeFilterSelectorRow(
                        selected = currentFilter,
                        onSelect = onFilterSelect,
                        onCustomClick = { showDatePicker = true }
                    )
                }

                item {
                    val periodDeltaPct = if (currentFilter != TimeFilter.ALL && analytics.previousPeriodTotal > 0.0) {
                        ((analytics.totalAmount - analytics.previousPeriodTotal) / analytics.previousPeriodTotal) * 100.0
                    } else null
                    TotalSpentHeroCard(
                        filterType = currentFilter,
                        totalAmount = analytics.totalAmount,
                        transactionCount = analytics.transactionCount,
                        dateRange = analytics.dateRange,
                        periodDeltaPct = periodDeltaPct,
                        onLentClick = onLentClick,
                        onTransactionsClick = onTransactionsClick
                    )
                }

                if (analytics.transactionCount > 0) {
                    item { QuickStatsRow(analytics = analytics) }

                    item {
                        WhereItWentCard(
                            categoryBreakdown = analytics.categoryBreakdown,
                            purposeBreakdown = analytics.purposeBreakdown,
                            trendPoints = analytics.trendPoints,
                            onCategoryClick = { category ->
                                onShowNotification("Filtering by $category", NotificationType.INFO)
                            },
                            onPurposeClick = { purpose ->
                                onShowNotification("Filtering by $purpose", NotificationType.INFO)
                            },
                            onViewAllClick = onShowAllClick
                        )
                    }
                } else {
                    item { EmptyStateCard() }
                }

                if (upcomingBills.isNotEmpty()) {
                    item(key = "upcoming-bills-card") {
                        UpcomingBillsCard(
                            bills = upcomingBills,
                            onViewAllClick = onRecurringBillsClick
                        )
                    }
                }

                if (monthlyBudget > 0.0) {
                    item(key = "monthly-budget-card") {
                        MonthlyBudgetCard(
                            monthlyBudget = monthlyBudget,
                            monthlySpent = monthlySpent,
                            currency = currency.ifBlank { "₹" },
                            onEditBudgetClick = { showBudgetEditorDialog = true }
                        )
                    }
                }

                if (recentSpends.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(title = stringResource(R.string.recent_activity))
                            TextButton(onClick = onShowAllClick) {
                                Text(stringResource(R.string.see_all), style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Icon(
                                    AppIcons.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(Sizes.iconInline)
                                )
                            }
                        }
                    }

                    items(recentSpends, key = { it.uuid }) { spend ->
                        if (onEditSpend != null && onDeleteSpend != null) {
                            SwipeableLogCard(
                                onEdit = { onEditSpend(spend) },
                                onDelete = { onDeleteSpend(spend) },
                                modifier = Modifier.animateItem()
                            ) {
                                RecentSpendRow(
                                    spend = spend,
                                    onClick = { onAppClick(spend.appName) }
                                )
                            }
                        } else {
                            RecentSpendRow(
                                spend = spend,
                                onClick = { onAppClick(spend.appName) }
                            )
                        }
                    }
                }
            }
        } else {
            // Dual-Pane Responsive Layout for Foldables, Tablets, Desktops
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Left Pane: Overview & Charts
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    contentPadding = PaddingValues(top = Spacing.md, bottom = 120.dp)
                ) {
                    item {
                        DashboardHeader(
                            themePreference = themePreference,
                            onCycleTheme = onCycleTheme,
                            onSettingsClick = onSettingsClick,
                            onAiAssistantClick = onAiAssistantClick,
                            onNotesClick = onNotesClick
                        )
                    }

                    item {
                        TimeFilterSelectorRow(
                            selected = currentFilter,
                            onSelect = onFilterSelect,
                            onCustomClick = { showDatePicker = true }
                        )
                    }

                    item {
                        val periodDeltaPct = if (currentFilter != TimeFilter.ALL && analytics.previousPeriodTotal > 0.0) {
                            ((analytics.totalAmount - analytics.previousPeriodTotal) / analytics.previousPeriodTotal) * 100.0
                        } else null
                        TotalSpentHeroCard(
                            filterType = currentFilter,
                            totalAmount = analytics.totalAmount,
                            transactionCount = analytics.transactionCount,
                            dateRange = analytics.dateRange,
                            periodDeltaPct = periodDeltaPct,
                            onLentClick = onLentClick,
                            onTransactionsClick = onTransactionsClick
                        )
                    }

                    if (analytics.transactionCount > 0) {
                        item { QuickStatsRow(analytics = analytics) }

                        item {
                            WhereItWentCard(
                                categoryBreakdown = analytics.categoryBreakdown,
                                purposeBreakdown = analytics.purposeBreakdown,
                                trendPoints = analytics.trendPoints,
                                onCategoryClick = { category ->
                                    onShowNotification("Filtering by $category", NotificationType.INFO)
                                },
                                onPurposeClick = { purpose ->
                                    onShowNotification("Filtering by $purpose", NotificationType.INFO)
                                },
                                onViewAllClick = onShowAllClick
                            )
                        }
                    } else {
                        item { EmptyStateCard() }
                    }

                    if (monthlyBudget > 0.0) {
                        item(key = "monthly-budget-card-dual") {
                            MonthlyBudgetCard(
                                monthlyBudget = monthlyBudget,
                                monthlySpent = monthlySpent,
                                currency = currency.ifBlank { "₹" },
                                onEditBudgetClick = { showBudgetEditorDialog = true }
                            )
                        }
                    }
                }

                // Right Pane: Activity & Secondary Modules
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    contentPadding = PaddingValues(top = Spacing.md, bottom = 120.dp)
                ) {
                    if (wrappedBanner != null) {
                        item(key = "wrapped-banner-dual") {
                            WrappedBanner(
                                info = wrappedBanner,
                                currency = currency,
                                onOpen = onWrappedBannerClick,
                                onDismiss = onWrappedBannerDismiss
                            )
                        }
                    }

                    if (upcomingBills.isNotEmpty()) {
                        item(key = "upcoming-bills-card-dual") {
                            UpcomingBillsCard(
                                bills = upcomingBills,
                                onViewAllClick = onRecurringBillsClick
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(title = stringResource(R.string.recent_activity))
                            TextButton(onClick = onShowAllClick) {
                                Text(stringResource(R.string.see_all), style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Icon(
                                    AppIcons.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(Sizes.iconInline)
                                )
                            }
                        }
                    }

                    if (recentSpends.isNotEmpty()) {
                        items(recentSpends, key = { "dual-${it.uuid}" }) { spend ->
                            if (onEditSpend != null && onDeleteSpend != null) {
                                SwipeableLogCard(
                                    onEdit = { onEditSpend(spend) },
                                    onDelete = { onDeleteSpend(spend) },
                                    modifier = Modifier.animateItem()
                                ) {
                                    RecentSpendRow(
                                        spend = spend,
                                        onClick = { onAppClick(spend.appName) }
                                    )
                                }
                            } else {
                                RecentSpendRow(
                                    spend = spend,
                                    onClick = { onAppClick(spend.appName) }
                                )
                            }
                        }
                    } else {
                        item {
                            EmptyStateCard()
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showCompactHeader,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.clickable(onClick = onSettingsClick),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        HeaderActionButton(
                            icon = Icons.Outlined.Menu,
                            onClick = onSettingsClick,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            background = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        HeaderActionButton(
                            icon = AppIcons.Ai,
                            onClick = onAiAssistantClick,
                            contentDescription = "AI Assistant",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            background = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        HeaderActionButton(
                            icon = AppIcons.Notes,
                            onClick = onNotesClick,
                            contentDescription = "Notes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            background = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun DashboardHeader(
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onSettingsClick: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onNotesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onSettingsClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            HeaderActionButton(
                icon = Icons.Outlined.Menu,
                onClick = onSettingsClick,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurface,
                background = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            HeaderActionButton(
                icon = AppIcons.Ai,
                onClick = onAiAssistantClick,
                contentDescription = "AI Assistant",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                background = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            HeaderActionButton(
                icon = AppIcons.Notes,
                onClick = onNotesClick,
                contentDescription = "Notes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                background = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            HeaderActionButton(
                icon = when (themePreference) {
                    ThemePreference.SYSTEM -> AppIcons.ThemeAuto
                    ThemePreference.LIGHT -> AppIcons.ThemeLight
                    ThemePreference.DARK -> AppIcons.ThemeDark
                    ThemePreference.OLED -> AppIcons.ThemeDark
                },
                onClick = onCycleTheme,
                contentDescription = when (themePreference) {
                    ThemePreference.SYSTEM -> "Theme: follow system (Material You). Tap to switch to light."
                    ThemePreference.LIGHT -> "Theme: light. Tap to switch to dark."
                    ThemePreference.DARK -> "Theme: dark. Tap to switch to OLED pitch-black."
                    ThemePreference.OLED -> "Theme: OLED pitch-black. Tap to follow system."
                },
                tint = MaterialTheme.colorScheme.onSurface,
                background = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    }
}

/**
 * A hand-rolled `Surface(onClick)` gets none of Material's touch-target reservation, so the size
 * here *is* the tap area — it has to carry the full minimum itself.
 */
@Composable
private fun HeaderActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(Radius.md),
        color = background,
        modifier = Modifier
            .size(Sizes.minTouchTarget)
            .scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(Sizes.iconAction)
            )
        }
    }
}
