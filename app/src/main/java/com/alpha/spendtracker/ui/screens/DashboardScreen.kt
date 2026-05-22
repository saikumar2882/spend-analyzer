/**
 * The main overview screen displaying spending summaries, charts, and recent activity.
 */
package com.alpha.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.EmptyStateCard
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.alpha.spendtracker.ui.components.RecentSpendRow
import com.alpha.spendtracker.ui.components.SpendingDonutChart
import com.alpha.spendtracker.ui.components.SpendingTrendBarChart
import com.alpha.spendtracker.ui.components.TimeFilterSelectorRow
import com.alpha.spendtracker.ui.components.TopAppsRankingCard
import com.alpha.spendtracker.ui.components.TotalSpentHeroCard
import com.alpha.spendtracker.ui.viewmodel.SpendingAnalytics
import com.alpha.spendtracker.ui.viewmodel.TimeFilter

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
    onShowAllClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onLentClick: () -> Unit,
    onLogout: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            onCustomRangeSelect(start, end)
                        }
                        showDatePicker = false
                    },
                    enabled = dateRangePickerState.selectedStartDateMillis != null && 
                             dateRangePickerState.selectedEndDateMillis != null
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp)) },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        item {
            DashboardHeader(
                themePreference = themePreference,
                onCycleTheme = onCycleTheme,
                onLogout = onLogout
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
            TotalSpentHeroCard(
                filterType = currentFilter,
                totalAmount = analytics.totalAmount,
                friendLending = analytics.friendLendingTotal,
                transactionCount = analytics.transactionCount,
                dateRange = analytics.dateRange,
                onLentClick = onLentClick
            )
        }

        if (analytics.transactionCount > 0) {
            item {
                Column {
                    Text(
                        text = "Category Breakdown",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SpendingDonutChart(
                        categoryBreakdown = analytics.categoryBreakdown,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SpendingTrendBarChart(
                    trendPoints = analytics.trendPoints,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                TopAppsRankingCard(
                    appBreakdown = analytics.appBreakdown,
                    onAppClick = onAppClick
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
                    Text(
                        text = "Recent Spending's",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onShowAllClick) {
                        Text("See All History")
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = "Show history",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            items(recentSpends, key = { it.id }) { spend ->
                RecentSpendRow(
                    spend = spend,
                    onClick = { onAppClick(spend.appName) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                    text = "OVERVIEW",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "SpendWise",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeToggleButton(
                themePreference = themePreference,
                onCycle = onCycleTheme
            )
            Spacer(modifier = Modifier.size(8.dp))
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ThemeToggleButton(
    themePreference: ThemePreference,
    onCycle: () -> Unit
) {
    val icon = when (themePreference) {
        ThemePreference.SYSTEM -> Icons.Rounded.BrightnessAuto
        ThemePreference.LIGHT -> Icons.Rounded.LightMode
        ThemePreference.DARK -> Icons.Rounded.DarkMode
    }
    val description = when (themePreference) {
        ThemePreference.SYSTEM -> "Theme: follow system. Tap to switch to light."
        ThemePreference.LIGHT -> "Theme: light. Tap to switch to dark."
        ThemePreference.DARK -> "Theme: dark. Tap to follow system."
    }

    Box(
        modifier = Modifier
            .size(46.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        Color(0xFF7C4DFF),
                        Color(0xFF3F51B5)
                    )
                ),
                CircleShape
            )
            .border(2.dp, Color.White, CircleShape)
            .clickable(onClick = onCycle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}
