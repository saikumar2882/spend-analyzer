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
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import androidx.compose.ui.graphics.asImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendBorrowScreen(
    allSpends: List<Spend>,
    deletedHistory: List<SpendHistory>,
    updatedHistory: List<SpendHistory>,
    onEditSpend: (Spend) -> Unit,
    onDeleteSpend: (Spend) -> Unit,
    onShowHistory: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val mainTabs = listOf("Lending", "Borrowing")
    var spendToDelete by remember { mutableStateOf<Spend?>(null) }
    
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTimeFilter by rememberSaveable { mutableStateOf(TimeFilter.ALL) }
    var customDateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var showDatePicker by remember { mutableStateOf(value = false) }
    var showFilters by rememberSaveable { mutableStateOf(value = false) }

    var showExportMenu by remember { mutableStateOf(false) }
    var showExportPreview by remember { mutableStateOf(false) }
    var exportSpends by remember { mutableStateOf<List<Spend>>(emptyList()) }
    val graphicsLayer = rememberGraphicsLayer()

    fun exportToCsv(context: Context, spends: List<Spend>, share: Boolean = true) {
        val csvHeader = "Date,App Name,Amount,Purpose,Notes\n"
        val csvData = StringBuilder(csvHeader)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        spends.forEach { spend ->
            csvData.append("${sdf.format(spend.timestamp)},")
            csvData.append("${spend.appName.replace(",", " ")},")
            csvData.append("${spend.amount},")
            csvData.append("${spend.purpose.replace(",", " ")},")
            csvData.append("${spend.notes.replace(",", " ")}\n")
        }

        val fileName = "lend_borrow_history_${System.currentTimeMillis()}.csv"
        
        if (share) {
            try {
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { it.write(csvData.toString().toByteArray()) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Lend & Borrow History")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share CSV Report"))
            } catch (e: Exception) {
                onShowNotification("Failed to share CSV: ${e.message}", NotificationType.ERROR)
            }
        } else {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }
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
            val fileName = "lend_borrow_report_${System.currentTimeMillis()}.png"
            
            if (share) {
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Lend & Borrow Report")
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

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        SegmentedTabs(
            tabs = mainTabs,
            selectedIndex = selectedTab,
            selectedColor = if (selectedTab == 0) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.error,
            onSelectedColor = if (selectedTab == 0) MaterialTheme.colorScheme.onSecondary
                              else MaterialTheme.colorScheme.onError,
            onSelect = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search, filter & AI assistant — grouped in one compact, aligned row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search", fontSize = 14.sp) },
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

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                exportToCsv(context, filteredSpends, share = true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Download CSV", style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showExportMenu = false
                                exportToCsv(context, filteredSpends, share = false)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text("Image Report", style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showExportMenu = false
                                exportSpends = filteredSpends
                                showExportPreview = true
                            }
                        )
                    }
                }

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
                total = filteredSpends.sumOf { it.amount },
                accent = if (selectedTab == 0) MaterialTheme.colorScheme.secondary
                         else MaterialTheme.colorScheme.error
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No ${mainTabs[selectedTab].lowercase()} records yet",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedTab == 0) "Money you lend will show up here."
                               else "Money you borrow will show up here.",
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
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = "Show history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
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
    selectedColor: Color,
    onSelectedColor: Color,
    onSelect: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    color = if (isSelected) selectedColor else Color.Transparent,
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
                            color = if (isSelected) onSelectedColor
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
private fun SummaryBar(
    label: String,
    total: Double,
    accent: Color
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
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
                        .background(accent, CircleShape)
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
                color = accent
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
            "Lend & Borrow Report",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            "Generated on $generatedDate",
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
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
                    Text("TOTAL AMOUNT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Gray))
                    Text("₹${formatCurrency(total)}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, color = Color.Black))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("RECORDS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Gray))
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
            Text("App/Person", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black))
            Text("Details", modifier = Modifier.weight(2.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black))
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
