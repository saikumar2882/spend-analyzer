/**
 * The main overview screen displaying spending summaries, charts, and recent activity.
 */
package com.alpha.spendtracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.AppAvatar
import com.alpha.spendtracker.ui.components.DateRangePickerModal
import com.alpha.spendtracker.ui.components.EmptyStateCard
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.components.ProfileDialog
import com.alpha.spendtracker.ui.components.QuickStatsRow
import com.alpha.spendtracker.ui.components.RecentSpendRow
import com.alpha.spendtracker.ui.components.SwipeableLogCard
import com.alpha.spendtracker.ui.components.TimeFilterSelectorRow
import com.alpha.spendtracker.ui.components.TotalSpentHeroCard
import com.alpha.spendtracker.ui.components.WhereItWentCard
import com.alpha.spendtracker.ui.icons.AppIcons
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.alpha.spendtracker.ui.viewmodel.SpendingAnalytics
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    currentFilter: TimeFilter,
    analytics: SpendingAnalytics,
    recentSpends: List<Spend>,
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
    onEditSpend: ((Spend) -> Unit)? = null,
    onDeleteSpend: ((Spend) -> Unit)? = null
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    var displayName by remember { mutableStateOf(auth.currentUser?.displayName.orEmpty()) }
    val email = auth.currentUser?.email.orEmpty()
    val photoUrl = auth.currentUser?.photoUrl?.toString()

    val effectiveDisplayName = remember(displayName, email) {
        if (displayName.isNotBlank()) {
            displayName.trim()
        } else if (email.isNotBlank()) {
            val handle = email.substringBefore('@').trim()
            handle.split('.', '_', '-')
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
        } else {
            ""
        }
    }

    val lazyListState = rememberLazyListState()
    val showCompactHeader by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 100
        }
    }

    val firstName = effectiveDisplayName.split(" ").firstOrNull().orEmpty()
    val greeting = if (firstName.isNotBlank()) "Hi, $firstName 👋" else "Welcome back 👋"

    if (showProfileDialog) {
        ProfileDialog(
            currentName = displayName,
            email = auth.currentUser?.email.orEmpty(),
            photoUrl = photoUrl,
            onDismiss = { showProfileDialog = false },
            onSave = { newName ->
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    showProfileDialog = false
                    onShowNotification("Please sign in to update your profile", NotificationType.ERROR)
                    return@ProfileDialog
                }
                val request = UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                currentUser.updateProfile(request)
                    .addOnCompleteListener { task ->
                        showProfileDialog = false
                        if (task.isSuccessful) {
                            displayName = newName
                            onShowNotification("Profile updated", NotificationType.SUCCESS)
                        } else {
                            onShowNotification(
                                "Error: ${task.exception?.message ?: "Could not save profile"}",
                                NotificationType.ERROR
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        showProfileDialog = false
                        onShowNotification(
                            "Error: ${e.message ?: "Could not save profile"}",
                            NotificationType.ERROR
                        )
                    }
            }
        )
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    displayName = effectiveDisplayName,
                    photoUrl = photoUrl,
                    themePreference = themePreference,
                    onCycleTheme = onCycleTheme,
                    onProfileClick = { showProfileDialog = true },
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

            if (recentSpends.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "Recent Activity")
                        TextButton(onClick = onShowAllClick) {
                            Text("See all", style = MaterialTheme.typography.labelLarge)
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
                        modifier = Modifier.clickable { showProfileDialog = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppAvatar(
                            name = effectiveDisplayName,
                            color = MaterialTheme.colorScheme.primary,
                            size = 32.dp,
                            photoUrl = photoUrl
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = greeting,
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = Spacing.xs, height = Spacing.ml)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DashboardHeader(
    displayName: String,
    photoUrl: String? = null,
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onProfileClick: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onNotesClick: () -> Unit
) {
    val firstName = displayName.trim().split(" ").firstOrNull().orEmpty()
    val greeting = if (firstName.isNotBlank()) "Hi, $firstName 👋" else "Welcome back 👋"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onProfileClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppAvatar(
                name = displayName,
                color = MaterialTheme.colorScheme.primary,
                size = 44.dp,
                photoUrl = photoUrl
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
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
                },
                onClick = onCycleTheme,
                contentDescription = when (themePreference) {
                    ThemePreference.SYSTEM -> "Theme: follow system. Tap to switch to light."
                    ThemePreference.LIGHT -> "Theme: light. Tap to switch to dark."
                    ThemePreference.DARK -> "Theme: dark. Tap to follow system."
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.md),
        color = background,
        modifier = Modifier.size(Sizes.minTouchTarget)
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
