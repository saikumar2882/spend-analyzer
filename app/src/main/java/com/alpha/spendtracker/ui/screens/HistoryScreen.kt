/**
 * Screen for viewing and searching historical transaction data, grouped by month.
 */
package com.alpha.spendtracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.CATEGORY_PRESETS
import com.alpha.spendtracker.ui.components.DateRangePickerModal
import com.alpha.spendtracker.ui.components.HistorySpendCard
import androidx.compose.ui.text.style.TextOverflow
import com.alpha.spendtracker.ui.components.SearchField
import com.alpha.spendtracker.ui.components.formatCurrency
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import com.alpha.spendtracker.util.formatMonth
import com.alpha.spendtracker.util.formatShortDate
import java.util.Calendar

import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.util.PdfExporter
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.draw.drawWithContent
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberCoroutineScope
import com.alpha.spendtracker.ui.components.SwipeableLogCard
import java.text.SimpleDateFormat

private const val ALL_CATEGORIES = "All"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    allSpends: List<Spend>,
    initialSearchQuery: String = "",
    initialCategoryFilter: String = ALL_CATEGORIES,
    initialTimeFilter: TimeFilter = TimeFilter.ALL,
    initialDateRange: Pair<Long, Long>? = null,
    onEditSpend: (Spend) -> Unit,
    onDeleteSpend: (Spend) -> Unit,
    onShowHistory: () -> Unit = {},
    // Opens the source note when a note-linked transaction (non-blank noteUuid) is tapped.
    onOpenNote: (String) -> Unit = {},
    onShowNotification: (String, NotificationType) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable(initialSearchQuery) { mutableStateOf(initialSearchQuery) }
    var isSearchActive by rememberSaveable { mutableStateOf(initialSearchQuery.isNotBlank()) }
    var selectedCategory by rememberSaveable(initialCategoryFilter) { mutableStateOf(initialCategoryFilter) }
    var selectedTimeFilter by rememberSaveable(initialTimeFilter) { mutableStateOf(initialTimeFilter) }
    var customDateRange by remember { mutableStateOf(initialDateRange) }
    var showDatePicker by remember { mutableStateOf(value = false) }
    var spendToDelete by remember { mutableStateOf<Spend?>(null) }
    var showFilters by rememberSaveable { 
        mutableStateOf((initialTimeFilter != TimeFilter.ALL) || (initialCategoryFilter != ALL_CATEGORIES)) 
    }
    var showExportPreview by remember { mutableStateOf(false) }
    var exportSpends by remember { mutableStateOf<List<Spend>>(emptyList()) }

    // Advanced Filter states
    var minRangeProgress by rememberSaveable { mutableStateOf(0f) }
    var maxRangeProgress by rememberSaveable { mutableStateOf(1f) }

    fun progressToAmount(progress: Float): Float {
        return if (progress <= 0.7f) {
            (progress / 0.7f) * 10000f
        } else {
            10000f + ((progress - 0.7f) / 0.3f) * 90000f
        }
    }

    val minAmountFilter = remember(minRangeProgress) { progressToAmount(minRangeProgress) }
    val maxAmountFilter = remember(maxRangeProgress) { progressToAmount(maxRangeProgress) }

    val isAmountFilterActive = minRangeProgress > 0f || maxRangeProgress < 1f

    val filteredHistory = remember(allSpends, searchQuery, selectedCategory, selectedTimeFilter, customDateRange, minAmountFilter, maxAmountFilter, isAmountFilterActive) {
        val q = searchQuery.trim()
        val calendar = Calendar.getInstance()
        val startOfToday = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val filterStartTime: Long
        val filterEndTime: Long

        when (selectedTimeFilter) {
            TimeFilter.DAY -> {
                filterStartTime = startOfToday
                filterEndTime = Long.MAX_VALUE
            }
            TimeFilter.WEEK -> {
                calendar.timeInMillis = startOfToday
                calendar[Calendar.DAY_OF_WEEK] = calendar.firstDayOfWeek
                filterStartTime = calendar.timeInMillis
                filterEndTime = Long.MAX_VALUE
            }
            TimeFilter.MONTH -> {
                calendar.timeInMillis = startOfToday
                calendar[Calendar.DAY_OF_MONTH] = 1
                filterStartTime = calendar.timeInMillis
                filterEndTime = Long.MAX_VALUE
            }
            TimeFilter.YEAR -> {
                calendar.timeInMillis = startOfToday
                calendar[Calendar.DAY_OF_YEAR] = 1
                filterStartTime = calendar.timeInMillis
                filterEndTime = Long.MAX_VALUE
            }
            TimeFilter.CUSTOM -> {
                filterStartTime = customDateRange?.first ?: 0L
                filterEndTime = customDateRange?.second ?: Long.MAX_VALUE
            }
            TimeFilter.ALL -> {
                filterStartTime = 0L
                filterEndTime = Long.MAX_VALUE
            }
        }

        allSpends.filter { spend ->
            val matchesQuery = q.isEmpty() ||
                spend.appName.contains(q, ignoreCase = true) ||
                spend.purpose.contains(q, ignoreCase = true) ||
                spend.notes.contains(q, ignoreCase = true)
            val matchesCategory = (selectedCategory == ALL_CATEGORIES) || (spend.category == selectedCategory)
            val matchesTime = spend.timestamp in (filterStartTime..filterEndTime)
            
            // Only apply amount filtering if the user has moved the slider from its default (0..1)
            val matchesAmountRange = if (isAmountFilterActive) {
                val effectiveMax = if (maxRangeProgress >= 1f) Double.MAX_VALUE else maxAmountFilter.toDouble()
                spend.amount >= minAmountFilter && spend.amount <= effectiveMax
            } else true

            matchesQuery && matchesCategory && matchesTime && matchesAmountRange
        }
    }

    // Total spend for the filter summary bar, hoisted so it isn't recomputed on every recomposition.
    val filteredTotal = remember(filteredHistory) { filteredHistory.sumOf { it.amount } }

    // Group spends by month and pre-compute each month's sum once, outside the LazyColumn content
    // lambda. Doing this inside the lambda re-ran the grouping + per-group sumOf on every recomposition.
    val groupedHistory = remember(filteredHistory) {
        filteredHistory.groupBy { formatMonth(it.timestamp) }
            .map { (monthHeader, spends) -> MonthGroup(monthHeader, spends, spends.sumOf { it.amount }) }
    }

    val graphicsLayer = rememberGraphicsLayer()
    if (showExportPreview) {
        ModalBottomSheet(
            onDismissRequest = { showExportPreview = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "PDF Report Preview",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    val exportTotal = remember(exportSpends) { exportSpends.sumOf { it.amount } }
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        ExportTable(exportSpends, exportTotal)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            PdfExporter.exportToPdf(
                                context = context,
                                spends = exportSpends,
                                reportTitle = "Transaction History Report",
                                filePrefix = "spend_history",
                                share = true,
                                onShowNotification = onShowNotification
                            )
                            showExportPreview = false
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share PDF", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            PdfExporter.exportToPdf(
                                context = context,
                                spends = exportSpends,
                                reportTitle = "Transaction History Report",
                                filePrefix = "spend_history",
                                share = false,
                                onShowNotification = onShowNotification
                            )
                            showExportPreview = false
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Rounded.FileDownload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save PDF", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header row with section title, search lens, filter toggle, export & recycle bin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSearchActive) {
                SearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search history...",
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            SearchLensButton(
                active = isSearchActive,
                onClick = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) {
                        searchQuery = ""
                    }
                }
            )

            FilterToggleButton(active = showFilters, onClick = { showFilters = !showFilters })

            Surface(
                onClick = {
                    exportSpends = filteredHistory
                    showExportPreview = true
                },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(Sizes.minTouchTarget)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.FileDownload,
                        contentDescription = "Export A4 PDF Report",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                onClick = onShowHistory,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(Sizes.minTouchTarget)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Restore,
                        contentDescription = "Recycle bin",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (showFilters) {
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Filter Transactions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val categoryFilters = remember { listOf(ALL_CATEGORIES) + CATEGORY_PRESETS }
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(categoryFilters, key = { it }) { name ->
                            FilterChip(
                                selected = selectedCategory == name,
                                onClick = { selectedCategory = name },
                                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(timeFilters, key = { it.first.name }) { (filter, label) ->
                            FilterChip(
                                selected = selectedTimeFilter == filter,
                                onClick = { selectedTimeFilter = filter },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "₹${minAmountFilter.roundToInt()} — ${if (maxRangeProgress >= 1f) "Max" else "₹${maxAmountFilter.roundToInt()}"}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isAmountFilterActive) {
                                    Surface(
                                        onClick = { 
                                            minRangeProgress = 0f
                                            maxRangeProgress = 1f
                                        },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Reset", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            RangeSlider(
                                value = minRangeProgress..maxRangeProgress,
                                onValueChange = { 
                                    minRangeProgress = it.start
                                    maxRangeProgress = it.endInclusive
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    thumbColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.height(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showFilters = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply Filters", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(24.dp))
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

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredHistory.isNotEmpty()) {
            FilterSummaryBar(
                total = filteredTotal
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filteredHistory.isEmpty()) {
            EmptyHistoryState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                groupedHistory.forEach { group ->
                    val monthHeader = group.monthHeader
                    val spends = group.spends
                    val monthSum = group.total
                    item(key = "header-$monthHeader") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monthHeader,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "₹${formatCurrency(monthSum)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    items(spends, key = { it.uuid }) { spend ->
                        SwipeableLogCard(
                            onEdit = { onEditSpend(spend) },
                            onDelete = { spendToDelete = spend },
                            modifier = Modifier.animateItem()
                        ) {
                            HistorySpendCard(
                                spend = spend,
                                onEdit = { onEditSpend(spend) },
                                onDelete = { spendToDelete = spend },
                                onClick = if (spend.noteUuid.isNotBlank()) {
                                    { onOpenNote(spend.noteUuid) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLensButton(active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(Sizes.minTouchTarget)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = if (active) "Close search" else "Search history",
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FilterToggleButton(active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(Sizes.minTouchTarget)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = "Toggle filters",
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FilterSummaryBar(
    total: Double
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
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
                    text = "Total Spend",
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
                text = "Delete Transaction?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to delete this transaction?",
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

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No expenses match",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Try clearing your filters or search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A month section of the history list with its spends and pre-computed total. Built once in a
 * remember block so the grouping and per-group sum don't re-run on every recomposition.
 */
@Immutable
private data class MonthGroup(
    val monthHeader: String,
    val spends: List<Spend>,
    val total: Double
)

@Composable
private fun ExportTable(spends: List<Spend>, total: Double, modifier: Modifier = Modifier) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val sdf = remember(locale) { java.text.SimpleDateFormat("dd MMM yy", locale) }
    val generatedDate = remember(locale) { 
        SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(System.currentTimeMillis())
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "Transaction History Report",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Generated on $generatedDate",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // Clean Minimal Summary Section (No heavy box fill or borders)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "TOTAL AMOUNT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "₹${formatCurrency(total)}",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "TRANSACTIONS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${spends.size}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(10.dp))

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Date", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text("App", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text("Purpose / Notes", modifier = Modifier.weight(2.5f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text("Amount", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface, thickness = 1.dp)

        // Spends Rows
        spends.forEach { spend ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sdf.format(spend.timestamp), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(spend.appName, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Column(modifier = Modifier.weight(2.5f)) {
                    Text(spend.purpose, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    if (spend.notes.isNotBlank()) {
                        Text(spend.notes, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Text("₹${formatCurrency(spend.amount)}", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "* End of Report *",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
