/**
 * Screen for viewing and searching historical transaction data, grouped by month.
 */
package com.alpha.spendtracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import com.alpha.spendtracker.ui.theme.MotionDuration
import com.alpha.spendtracker.ui.theme.motionDuration
import com.alpha.spendtracker.ui.theme.rememberReduceMotion
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.CATEGORY_PRESETS
import com.alpha.spendtracker.ui.components.DateRangePickerModal
import com.alpha.spendtracker.ui.components.HistorySpendCard
import com.alpha.spendtracker.ui.components.getLocalizedPresetName
import androidx.compose.ui.text.style.TextOverflow
import com.alpha.spendtracker.ui.components.SearchField
import com.alpha.spendtracker.ui.components.formatCurrency
import com.alpha.spendtracker.ui.components.formatCurrencyRounded
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.rememberPressScale
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
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.platform.LocalConfiguration
import com.alpha.spendtracker.ui.components.SwipeableLogCard
import java.text.SimpleDateFormat

private const val ALL_CATEGORIES = "All"

enum class QuickFilter(val labelRes: Int) {
    ALL(R.string.quick_filter_all),
    FOOD(R.string.quick_filter_food),
    SHOPPING(R.string.quick_filter_shopping),
    TRAVEL(R.string.quick_filter_travel),
    BILLS(R.string.quick_filter_bills),
    HIGH_SPENDS(R.string.quick_filter_high_spends),
    CREDIT_CARDS(R.string.quick_filter_credit_cards)
}

fun Spend.matchesQuickFilter(filter: QuickFilter): Boolean {
    return when (filter) {
        QuickFilter.ALL -> true
        QuickFilter.FOOD -> {
            val p = purpose.lowercase()
            val c = category.lowercase()
            val a = appName.lowercase()
            val n = notes.lowercase()
            p.contains("food") || p.contains("grocer") || p.contains("dining") || p.contains("restaurant") ||
            c == "quick commerce" ||
            a.contains("swiggy") || a.contains("zomato") || a.contains("zepto") || a.contains("blinkit") ||
            n.contains("food") || n.contains("dinner") || n.contains("lunch") || n.contains("breakfast") || n.contains("grocer")
        }
        QuickFilter.SHOPPING -> {
            val p = purpose.lowercase()
            val c = category.lowercase()
            val a = appName.lowercase()
            val n = notes.lowercase()
            p.contains("shop") || p.contains("apparel") || p.contains("cloth") ||
            c == "e-commerce" ||
            a.contains("amazon") || a.contains("flipkart") || a.contains("myntra") || a.contains("ajio") ||
            n.contains("shopping") || n.contains("clothes") || n.contains("dress")
        }
        QuickFilter.TRAVEL -> {
            val p = purpose.lowercase()
            val a = appName.lowercase()
            val n = notes.lowercase()
            p.contains("travel") || p.contains("commute") || p.contains("cab") || p.contains("fuel") ||
            a.contains("uber") || a.contains("ola") || a.contains("rapido") || a.contains("irctc") ||
            n.contains("travel") || n.contains("cab") || n.contains("uber") || n.contains("ola") || n.contains("rapido") || n.contains("flight") || n.contains("train") || n.contains("petrol") || n.contains("fuel")
        }
        QuickFilter.BILLS -> {
            val p = purpose.lowercase()
            val n = notes.lowercase()
            p.contains("bill") || p.contains("rent") || p.contains("utilit") || p.contains("subscript") || p.contains("recharge") ||
            n.contains("bill") || n.contains("rent") || n.contains("electricity") || n.contains("recharge") || n.contains("wifi") ||
            noteUuid.isNotBlank()
        }
        QuickFilter.HIGH_SPENDS -> amount > 1000.0
        QuickFilter.CREDIT_CARDS -> {
            val p = purpose.lowercase()
            val c = category.lowercase()
            val a = appName.lowercase()
            val n = notes.lowercase()
            c == "banking & cards" || c.contains("credit") ||
            p.contains("credit") || p.contains("card bill") || p.contains("cc bill") ||
            a.contains("cred") || a.contains("card") ||
            n.contains("credit card") || n.contains("cc bill") || n.contains("card bill")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    allSpends: List<Spend>,
    initialSearchQuery: String = "",
    initialCategoryFilter: String = ALL_CATEGORIES,
    initialTimeFilter: TimeFilter = TimeFilter.ALL,
    initialDateRange: Pair<Long, Long>? = null,
    monthlyBudget: Double? = null,
    // Hoisted by the caller so open/closed months survive leaving the screen (see MonthChoices).
    monthChoices: MonthChoices = rememberMonthChoices(),
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

    val initialQuickFilter = remember(initialCategoryFilter) {
        when (initialCategoryFilter) {
            "Food", "Groceries & Food", "Quick Commerce" -> QuickFilter.FOOD
            "Shopping", "Shopping & Apparels", "E-Commerce" -> QuickFilter.SHOPPING
            "Travel", "Travel & Commute" -> QuickFilter.TRAVEL
            "Bills", "Rent & Utilities" -> QuickFilter.BILLS
            "High Spends (> ₹1000)" -> QuickFilter.HIGH_SPENDS
            "Credit Cards", "Banking & Cards", "Credit Card Bill" -> QuickFilter.CREDIT_CARDS
            else -> QuickFilter.ALL
        }
    }
    var selectedQuickFilter by rememberSaveable { mutableStateOf(initialQuickFilter) }

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

    val filteredHistory = remember(allSpends, searchQuery, selectedCategory, selectedTimeFilter, customDateRange, minAmountFilter, maxAmountFilter, isAmountFilterActive, selectedQuickFilter) {
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

            val matchesQuickFilter = spend.matchesQuickFilter(selectedQuickFilter)

            matchesQuery && matchesCategory && matchesTime && matchesAmountRange && matchesQuickFilter
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

    // Months are collapsed behind their header. A month the user opened or closed stays that way;
    // otherwise only the newest is open, and every month is open while searching (see MonthSections).
    val sections = rememberMonthSections(
        choices = monthChoices,
        newestKey = groupedHistory.firstOrNull()?.monthHeader,
        searching = searchQuery.isNotBlank()
    )

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
                        ExportTable(exportSpends, exportTotal, monthlyBudget)
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
                                monthlyBudget = monthlyBudget,
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
                        Text(stringResource(R.string.share_pdf), fontWeight = FontWeight.SemiBold)
                    }
                    val reportTitleText = stringResource(R.string.pdf_report_title)
                    Button(
                        onClick = {
                            PdfExporter.exportToPdf(
                                context = context,
                                spends = exportSpends,
                                reportTitle = reportTitleText,
                                filePrefix = "spend_history",
                                monthlyBudget = monthlyBudget,
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
                        Text(stringResource(R.string.save_pdf), fontWeight = FontWeight.SemiBold)
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
                    placeholder = stringResource(R.string.search_history_placeholder),
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = stringResource(R.string.history_title),
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

            val exportInteraction = remember { MutableInteractionSource() }
            val exportScale = rememberPressScale(exportInteraction)
            Surface(
                onClick = {
                    exportSpends = filteredHistory
                    showExportPreview = true
                },
                interactionSource = exportInteraction,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(Sizes.minTouchTarget)
                    .scale(exportScale)
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

            val trashInteraction = remember { MutableInteractionSource() }
            val trashScale = rememberPressScale(trashInteraction)
            Surface(
                onClick = onShowHistory,
                interactionSource = trashInteraction,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(Sizes.minTouchTarget)
                    .scale(trashScale)
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

        Spacer(modifier = Modifier.height(4.dp))

        // Quick Category & Smart Filter Chips Bar
        QuickFilterChipsBar(
            selectedQuickFilter = selectedQuickFilter,
            onQuickFilterSelected = { filter ->
                selectedQuickFilter = filter
                if (filter != QuickFilter.ALL) {
                    selectedCategory = ALL_CATEGORIES
                }
            }
        )

        if (showFilters) {
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filters",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isAmountFilterActive || selectedCategory != ALL_CATEGORIES || selectedTimeFilter != TimeFilter.ALL) {
                            val resetInteraction = remember { MutableInteractionSource() }
                            val resetScale = rememberPressScale(resetInteraction)
                            Surface(
                                onClick = {
                                    selectedCategory = ALL_CATEGORIES
                                    selectedTimeFilter = TimeFilter.ALL
                                    minRangeProgress = 0f
                                    maxRangeProgress = 1f
                                    customDateRange = null
                                },
                                interactionSource = resetInteraction,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.scale(resetScale)
                            ) {
                                Text(
                                    text = "Reset All",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val categoryFilters = remember { listOf(ALL_CATEGORIES) + CATEGORY_PRESETS }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(categoryFilters, key = { it }) { name ->
                            val isSelected = selectedCategory == name
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = name },
                                label = { Text(name) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Time",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                    ) {
                        items(timeFilters, key = { it.first.name }) { (filter, label) ->
                            val isSelected = selectedTimeFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTimeFilter = filter },
                                label = { Text(label) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                        }
                        item(key = "custom") {
                            val range = customDateRange
                            val isSelected = selectedTimeFilter == TimeFilter.CUSTOM
                            val customLabel = if ((isSelected) && (range != null)) {
                                "${formatShortDate(range.first)} – ${formatShortDate(range.second)}"
                            } else {
                                "Custom"
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { showDatePicker = true },
                                label = { Text(customLabel) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Amount Range",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${minAmountFilter.roundToInt()} — ${if (maxRangeProgress >= 1f) "Max" else "₹${maxAmountFilter.roundToInt()}"}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                        )
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = { showFilters = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.apply_filters), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                    val expanded = sections.isExpanded(monthHeader)
                    item(key = "header-$monthHeader") {
                        MonthHeader(
                            month = monthHeader,
                            total = group.total,
                            expanded = expanded,
                            onToggle = { sections.toggle(monthHeader) }
                        )
                    }
                    if (expanded) {
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
}

/**
 * A month's heading, doubling as the button that opens and closes its logs. The whole row is the
 * tap target (and at least [Sizes.minTouchTarget] tall, growing with the font scale rather than
 * cropping the text); the click label tells a screen reader which way the tap will go.
 */
@Composable
internal fun MonthHeader(
    month: String,
    total: Double,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(motionDuration(MotionDuration.SHORT, reduceMotion)),
        label = "monthChevron"
    )
    val clickLabel = stringResource(
        if (expanded) R.string.history_collapse_month else R.string.history_expand_month
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onToggle)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(chevronRotation)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = month,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "₹${formatCurrency(total)}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun SearchLensButton(active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(Sizes.minTouchTarget)
            .scale(scale)
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
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(Sizes.minTouchTarget)
            .scale(scale)
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
                    text = stringResource(R.string.total_spend),
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
                            text = getLocalizedPresetName(spend.appName),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "₹${formatCurrency(spend.amount)} - ${getLocalizedPresetName(spend.purpose)}",
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
private fun ExportTable(
    spends: List<Spend>,
    total: Double,
    monthlyBudget: Double? = null,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val sdf = remember(locale) { SimpleDateFormat("dd MMM yy", locale) }
    val generatedDate = remember(locale) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(System.currentTimeMillis())
    }
    val avgPerTx = remember(spends, total) { if (spends.isNotEmpty()) total / spends.size else 0.0 }
    val topCategories = remember(spends) {
        spends
            .groupBy { spend ->
                getLocalizedPresetName(spend.purpose.ifBlank { spend.category.ifBlank { "Others" } })
            }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .take(3)
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
            stringResource(R.string.generated_on, generatedDate),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Clean Financial Summary Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        stringResource(R.string.pdf_total_spent),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "₹${formatCurrency(total)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.pdf_avg_per_tx, "₹${formatCurrencyRounded(avgPerTx)}"),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        stringResource(R.string.pdf_transactions),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${spends.size}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.transactions_count, spends.size),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        stringResource(R.string.pdf_budget_progress),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    val (statusText, subText) = if (monthlyBudget != null && monthlyBudget > 0.0) {
                        val pct = ((total / monthlyBudget) * 100).toInt().coerceAtMost(999)
                        val status = if (total <= monthlyBudget) stringResource(R.string.pdf_budget_on_track) else stringResource(R.string.pdf_budget_exceeded)
                        val sub = "₹${formatCurrencyRounded(total)} / ₹${formatCurrencyRounded(monthlyBudget)} ($pct%)"
                        Pair(status, sub)
                    } else {
                        val status = stringResource(R.string.pdf_budget_on_track)
                        val sub = stringResource(R.string.pdf_avg_per_tx, "₹${formatCurrencyRounded(avgPerTx)}")
                        Pair(status, sub)
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (monthlyBudget != null && monthlyBudget > 0.0 && total > monthlyBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (topCategories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        stringResource(R.string.pdf_top_categories),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    topCategories.forEachIndexed { index, entry ->
                        val pct = if (total > 0) (entry.value / total * 100).toInt() else 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}. ${entry.key}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "₹${formatCurrency(entry.value)} ($pct%)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
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
            Text(stringResource(R.string.entry_date_label), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.payment_app), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.purpose_or_notes), modifier = Modifier.weight(2.5f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.amount), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
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
                Text(getLocalizedPresetName(spend.appName), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Column(modifier = Modifier.weight(2.5f)) {
                    Text(getLocalizedPresetName(spend.purpose), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
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

@Composable
private fun QuickFilterChipsBar(
    selectedQuickFilter: QuickFilter,
    onQuickFilterSelected: (QuickFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(QuickFilter.entries, key = { it.name }) { filter ->
            val isSelected = selectedQuickFilter == filter
            val icon = when (filter) {
                QuickFilter.ALL -> Icons.Rounded.AllInclusive
                QuickFilter.FOOD -> Icons.Rounded.Restaurant
                QuickFilter.SHOPPING -> Icons.Rounded.ShoppingBag
                QuickFilter.TRAVEL -> Icons.Rounded.DirectionsCar
                QuickFilter.BILLS -> Icons.AutoMirrored.Rounded.ReceiptLong
                QuickFilter.HIGH_SPENDS -> Icons.AutoMirrored.Rounded.TrendingUp
                QuickFilter.CREDIT_CARDS -> Icons.Rounded.CreditCard
            }

            val interactionSource = remember { MutableInteractionSource() }
            val scale = rememberPressScale(interactionSource)

            FilterChip(
                selected = isSelected,
                onClick = { onQuickFilterSelected(filter) },
                interactionSource = interactionSource,
                label = {
                    Text(
                        text = stringResource(filter.labelRes),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.scale(scale)
            )
        }
    }
}
