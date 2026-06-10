package com.alpha.spendtracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendHistory
import com.alpha.spendtracker.ui.components.*
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import com.alpha.spendtracker.util.formatMonth
import com.alpha.spendtracker.util.formatShortDate
import java.util.Calendar

@Composable
fun LendBorrowScreen(
    allSpends: List<Spend>,
    deletedHistory: List<SpendHistory>,
    updatedHistory: List<SpendHistory>,
    onEditSpend: (Spend) -> Unit,
    onDeleteSpend: (Spend) -> Unit,
    onShowHistory: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val mainTabs = listOf("Lending", "Borrowing")
    var spendToDelete by remember { mutableStateOf<Spend?>(null) }
    
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTimeFilter by rememberSaveable { mutableStateOf(TimeFilter.ALL) }
    var customDateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var showDatePicker by remember { mutableStateOf(value = false) }
    var showFilters by rememberSaveable { mutableStateOf(value = false) }

    val filteredSpends = remember(allSpends, selectedTab, searchQuery, selectedTimeFilter, customDateRange) {
        val purpose = if (selectedTab == 0) "Lending" else "Borrowing"
        val q = searchQuery.trim()

        val calendar = Calendar.getInstance()
        val startOfToday = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val (filterStart, filterEnd) = getTimeBounds(selectedTimeFilter, startOfToday, calendar, customDateRange)

        allSpends.filter { spend ->
            val matchesPurpose = spend.purpose == purpose
            val matchesQuery = q.isEmpty() ||
                spend.notes.contains(q, ignoreCase = true) ||
                spend.appName.contains(q, ignoreCase = true)
            val matchesTime = spend.timestamp in (filterStart..filterEnd)

            matchesPurpose && matchesQuery && matchesTime
        }
    }

    if (spendToDelete != null) {
        val currentSpendToDelete = spendToDelete!!
        DeleteConfirmationDialog(
            spend = currentSpendToDelete,
            onConfirm = {
                onDeleteSpend(currentSpendToDelete)
                spendToDelete = null
            },
            onDismiss = {
                spendToDelete = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        SegmentedTabs(
            tabs = mainTabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search, filter & AI assistant — grouped in one compact, aligned row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search description…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterToggleButton(active = showFilters, onClick = { showFilters = !showFilters })
                
                // Consolidated History Button
                val historyCount = deletedHistory.size + updatedHistory.size
                HistoryIconButton(
                    count = historyCount,
                    onClick = onShowHistory
                )
            }
        }

        AnimatedVisibility(
            visible = showFilters,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                val timeFilters = remember {
                    listOf(
                        TimeFilter.ALL to "All Time",
                        TimeFilter.DAY to "Today",
                        TimeFilter.WEEK to "This Week",
                        TimeFilter.MONTH to "This Month",
                        TimeFilter.YEAR to "This Year"
                    )
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(timeFilters, key = { it.first.name }) { (filter, label) ->
                        FilterChip(
                            selected = selectedTimeFilter == filter,
                            onClick = { selectedTimeFilter = filter },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                    item(key = "custom") {
                        val range = customDateRange
                        val customLabel = if ((selectedTimeFilter == TimeFilter.CUSTOM) && (range != null)) {
                            "${formatShortDate(range.first)} – ${formatShortDate(range.second)}"
                        } else {
                            "Custom"
                        }
                        FilterChip(
                            selected = selectedTimeFilter == TimeFilter.CUSTOM,
                            onClick = { showDatePicker = true },
                            label = { Text(customLabel, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }
        }

        if (showDatePicker) {
            DateRangePickerModal(
                initialStart = customDateRange?.first,
                initialEnd = customDateRange?.second,
                onDismiss = { showDatePicker = false },
                onConfirm = { start, end ->
                    customDateRange = start to end
                    selectedTimeFilter = TimeFilter.CUSTOM
                    showDatePicker = false
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Aggregate Summary Bar
        if (filteredSpends.isNotEmpty()) {
            SummaryBar(
                label = if (selectedTab == 0) "Total Lent" else "Total Borrowed",
                total = filteredSpends.sumOf { it.amount }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filteredSpends.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No ${mainTabs[selectedTab].lowercase()} records yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                val grouped = filteredSpends.groupBy { formatMonth(it.timestamp) }
                grouped.forEach { (monthHeader, spends) ->
                    val monthSum = spends.sumOf { it.amount }
                    item(key = "header-$monthHeader-$selectedTab") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monthHeader,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "₹${formatCurrency(monthSum)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    items(spends, key = { it.uuid }) { spend ->
                        HistorySpendCard(
                            spend = spend,
                            onEdit = { onEditSpend(spend) },
                            onDelete = { spendToDelete = spend },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryIconButton(count: Int, onClick: () -> Unit) {
    Box {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = "Show history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (count > 0) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = CircleShape,
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (count > 9) "9+" else count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

private fun getTimeBounds(filter: TimeFilter, startOfToday: Long, calendar: Calendar, customRange: Pair<Long, Long>?): Pair<Long, Long> {
    return when (filter) {
        TimeFilter.DAY -> startOfToday to Long.MAX_VALUE
        TimeFilter.WEEK -> {
            calendar.timeInMillis = startOfToday
            calendar[Calendar.DAY_OF_WEEK] = calendar.firstDayOfWeek
            calendar.timeInMillis to Long.MAX_VALUE
        }
        TimeFilter.MONTH -> {
            calendar.timeInMillis = startOfToday
            calendar[Calendar.DAY_OF_MONTH] = 1
            calendar.timeInMillis to Long.MAX_VALUE
        }
        TimeFilter.YEAR -> {
            calendar.timeInMillis = startOfToday
            calendar[Calendar.DAY_OF_YEAR] = 1
            calendar.timeInMillis to Long.MAX_VALUE
        }
        TimeFilter.CUSTOM -> (customRange?.first ?: 0L) to (customRange?.second ?: Long.MAX_VALUE)
        else -> 0L to Long.MAX_VALUE
    }
}

@Composable
private fun SegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
private fun FilterToggleButton(active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = "Toggle filters",
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryBar(
    label: String,
    total: Double
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "₹${formatCurrency(total)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    spend: Spend,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Record?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to delete this record? It will be moved to the Recycle Bin.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = spend.appName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "₹${formatCurrency(spend.amount)} - ${spend.purpose}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
