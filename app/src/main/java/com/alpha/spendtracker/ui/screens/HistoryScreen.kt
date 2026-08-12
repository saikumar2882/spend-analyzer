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
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.alpha.spendtracker.ui.components.formatCurrency
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
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
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
import androidx.compose.material.icons.rounded.History
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
    var selectedCategory by rememberSaveable(initialCategoryFilter) { mutableStateOf(initialCategoryFilter) }
    var selectedTimeFilter by rememberSaveable(initialTimeFilter) { mutableStateOf(initialTimeFilter) }
    var customDateRange by remember { mutableStateOf(initialDateRange) }
    var showDatePicker by remember { mutableStateOf(value = false) }
    var spendToDelete by remember { mutableStateOf<Spend?>(null) }
    var showFilters by rememberSaveable { 
        mutableStateOf((initialTimeFilter != TimeFilter.ALL) || (initialCategoryFilter != ALL_CATEGORIES)) 
    }
    var showExportMenu by remember { mutableStateOf(false) }
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
    val scope = rememberCoroutineScope()

    fun exportToCsv(context: Context, spends: List<Spend>, share: Boolean = true) {
        val csvHeader = "Date,App Name,Amount,Purpose,Category,Notes\n"
        val csvData = StringBuilder(csvHeader)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        spends.forEach { spend ->
            csvData.append("${sdf.format(spend.timestamp)},")
            csvData.append("${spend.appName.replace(",", " ")},")
            csvData.append("${spend.amount},")
            csvData.append("${spend.purpose.replace(",", " ")},")
            csvData.append("${spend.category.replace(",", " ")},")
            csvData.append("${spend.notes.replace(",", " ")}\n")
        }

        val fileName = "spend_history_${System.currentTimeMillis()}.csv"
        
        if (share) {
            try {
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { it.write(csvData.toString().toByteArray()) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Spend Tracker History")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share CSV Report"))
            } catch (e: Exception) {
                onShowNotification("Failed to share CSV: ${e.message}", NotificationType.ERROR)
            }
        } else {
            // Direct download to Downloads folder
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                // MediaStore.Downloads requires API 29. On older devices, we'll use sharing or MediaStore.Files
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }
                
                val uri = resolver.insert(collection, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os ->
                        os.write(csvData.toString().toByteArray())
                    }
                    onShowNotification("Saved to Downloads", NotificationType.SUCCESS)
                } ?: run {
                    onShowNotification("Failed to create file", NotificationType.ERROR)
                }
            } catch (e: Exception) {
                onShowNotification("Failed to download CSV: ${e.message}", NotificationType.ERROR)
            }
        }
    }

    suspend fun exportToPng(share: Boolean = true) {
        try {
            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
            val fileName = "spend_history_${System.currentTimeMillis()}.png"
            
            if (share) {
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Spend Tracker Report")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Image Report"))
            } else {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SpendTracker")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, os) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    onShowNotification("Saved to Gallery", NotificationType.SUCCESS)
                }
            }
        } catch (e: Exception) {
            onShowNotification("Failed to export image: ${e.message}", NotificationType.ERROR)
        }
    }

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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Image Export Preview",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val exportTotal = remember(exportSpends) { exportSpends.sumOf { it.amount } }
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Box(modifier = Modifier.drawWithContent {
                            // This captures the content as it is drawn. 
                            // To get a "long image", we need to ensure the graphicsLayer 
                            // records the entire height of the ExportTable.
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }) {
                            ExportTable(exportSpends, exportTotal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                exportToPng(share = true)
                                showExportPreview = false
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.Image, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                exportToPng(share = false)
                                showExportPreview = false
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Rounded.FileDownload, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
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

        // Search, filter & AI assistant — grouped in one compact, aligned row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search ...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            FilterToggleButton(active = showFilters, onClick = { showFilters = !showFilters })

            Box {
                Surface(
                    onClick = { showExportMenu = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.FileDownload,
                            contentDescription = "Export CSV or Image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    DropdownMenuItem(
                        text = { Text("Share CSV", style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showExportMenu = false
                            exportToCsv(context, filteredHistory, share = true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Download CSV", style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showExportMenu = false
                            exportToCsv(context, filteredHistory, share = false)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text("Image Report", style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showExportMenu = false
                            exportSpends = filteredHistory
                            showExportPreview = true
                        }
                    )
                }
            }

            Surface(
                onClick = onShowHistory,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
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

        AnimatedVisibility(
            visible = showFilters,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))

                val categoryFilters = remember { listOf(ALL_CATEGORIES) + CATEGORY_PRESETS }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(categoryFilters, key = { it }) { name ->
                        FilterChip(
                            selected = selectedCategory == name,
                            onClick = { selectedCategory = name },
                            label = { Text(name) },
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

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
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
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (isAmountFilterActive) {
                                Surface(
                                    onClick = { 
                                        minRangeProgress = 0f
                                        maxRangeProgress = 1f
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Reset", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(12.dp))
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
                                activeTrackColor = MaterialTheme.colorScheme.secondary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thumbColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.height(10.dp)
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
                contentPadding = PaddingValues(bottom = 80.dp)
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
                        HistorySpendCard(
                            spend = spend,
                            onEdit = { onEditSpend(spend) },
                            onDelete = { spendToDelete = spend },
                            modifier = Modifier.animateItem(),
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

@Composable
private fun FilterToggleButton(active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(44.dp)
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
        java.text.SimpleDateFormat("dd MMM yyyy", locale).format(System.currentTimeMillis()) 
    }
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text(
            "Transaction History Report",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            "Generated on $generatedDate",
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Summary Card at top so it's always captured in a single-screen image
        Surface(
            color = Color(0xFFF9FAFB),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("TOTAL SPEND", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Gray))
                    Text("₹${formatCurrency(total)}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, color = Color.Black))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("TRANSACTIONS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Gray))
                    Text("${spends.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.Black))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3F4F6))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Date", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black))
            Text("App", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black))
            Text("Purpose/Note", modifier = Modifier.weight(2.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black))
            Text("Amount", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = Color.Black, thickness = 1.dp)

        // Spends
        spends.forEach { spend ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sdf.format(spend.timestamp), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color.Black))
                Text(spend.appName, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color.Black), maxLines = 1)
                Column(modifier = Modifier.weight(2.5f)) {
                    Text(spend.purpose, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black), maxLines = 1)
                    if (spend.notes.isNotBlank()) {
                        Text(spend.notes, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color.Gray), maxLines = 1)
                    }
                }
                Text("₹${formatCurrency(spend.amount)}", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black), textAlign = TextAlign.End)
            }
            HorizontalDivider(color = Color(0xFFE5E7EB))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "* End of Report *",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}
