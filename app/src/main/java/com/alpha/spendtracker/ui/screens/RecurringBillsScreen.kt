package com.alpha.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.ui.components.APP_COLOR_BY_NAME
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.AppPreset
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import com.alpha.spendtracker.ui.components.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringBillsScreen(
    bills: List<RecurringBill>,
    onBack: () -> Unit,
    onAddBill: (String, String, String, String, Double, Int, String) -> Unit,
    onUpdateBill: (RecurringBill) -> Unit,
    onDeleteBill: (RecurringBill) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<RecurringBill?>(null) }
    var billToDelete by remember { mutableStateOf<RecurringBill?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring Bills", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Bill")
                    }
                }
            )
        }
    ) { padding ->
        if (bills.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No recurring bills yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tap + to add subscriptions or card bills.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bills) { bill ->
                    RecurringBillItem(
                        bill = bill,
                        onEdit = { editingBill = it },
                        onDelete = { billToDelete = it }
                    )
                }
            }
        }

        if (billToDelete != null) {
            AlertDialog(
                onDismissRequest = { billToDelete = null },
                title = { Text("Delete Recurring Bill") },
                text = { Text("Are you sure you want to delete '${billToDelete?.name}'?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            billToDelete?.let { onDeleteBill(it) }
                            billToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { billToDelete = null }) { Text("Cancel") }
                }
            )
        }

        if (showAddDialog || editingBill != null) {
            BillEditDialog(
                bill = editingBill,
                onDismiss = {
                    showAddDialog = false
                    editingBill = null
                },
                onSave = { name, purpose, category, app, amount, day, notes ->
                    if (editingBill != null) {
                        onUpdateBill(editingBill!!.copy(
                            name = name, purpose = purpose,
                            category = category, appName = app, amount = amount, dayOfMonth = day, notes = notes
                        ))
                    } else {
                        onAddBill(name, purpose, category, app, amount, day, notes)
                    }
                    showAddDialog = false
                    editingBill = null
                }
            )
        }
    }
}

@Composable
fun RecurringBillItem(
    bill: RecurringBill,
    onEdit: (RecurringBill) -> Unit,
    onDelete: (RecurringBill) -> Unit
) {
    val accent = APP_COLOR_BY_NAME[bill.appName] ?: MaterialTheme.colorScheme.primary
    val daysUntil = remember(bill.dayOfMonth) { daysUntilDue(bill.dayOfMonth) }
    val dueLabel = when {
        daysUntil == 0 -> "Due today"
        daysUntil == 1 -> "Due tomorrow"
        daysUntil <= 5 -> "Due in $daysUntil days"
        else -> "Due ${bill.dayOfMonth}${getDaySuffix(bill.dayOfMonth)}"
    }
    val dueColor = if (daysUntil <= 5) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day-of-month badge in the app's brand color
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = bill.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                    Text(
                        text = getDaySuffix(bill.dayOfMonth),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = accent.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bill.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(bill.appName, style = MaterialTheme.typography.labelMedium, color = accent)
                Text(
                    dueLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = dueColor
                )
            }
            if (bill.amount > 0) {
                Text(
                    "₹${formatCurrency(bill.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = { onEdit(bill) }) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onDelete(bill) }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Approximate days until the next occurrence of [dayOfMonth] (for a "due soon" hint). */
private fun daysUntilDue(dayOfMonth: Int): Int {
    val cal = java.util.Calendar.getInstance()
    val todayDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val target = dayOfMonth.coerceIn(1, maxDay)
    return if (target >= todayDay) target - todayDay else (maxDay - todayDay) + target
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditDialog(
    bill: RecurringBill?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Double, Int, String) -> Unit
) {
    var name by remember { mutableStateOf(bill?.name ?: "") }
    var purpose by remember { mutableStateOf(bill?.purpose ?: PURPOSE_PRESETS.first()) }
    var appName by remember { mutableStateOf(bill?.appName ?: APP_PRESETS.first().displayName) }
    var amount by remember { mutableStateOf(bill?.amount?.toString() ?: "") }
    var day by remember { mutableStateOf(bill?.dayOfMonth?.toString() ?: "1") }
    var notes by remember { mutableStateOf(bill?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bill == null) "Add Recurring Bill" else "Edit Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bill Name") }, singleLine = true)
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) amount = it },
                    label = { Text("Amount (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                var appExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = appExpanded, onExpandedChange = { appExpanded = it }) {
                    OutlinedTextField(
                        value = appName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default App") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = appExpanded, onDismissRequest = { appExpanded = false }) {
                        APP_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.displayName) },
                                onClick = {
                                    appName = preset.displayName
                                    appExpanded = false
                                }
                            )
                        }
                    }
                }

                var purposeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = purposeExpanded, onExpandedChange = { purposeExpanded = it }) {
                    OutlinedTextField(
                        value = purpose,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Purpose") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = purposeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = purposeExpanded, onDismissRequest = { purposeExpanded = false }) {
                        PURPOSE_PRESETS.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    purpose = p
                                    purposeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(value = day, onValueChange = { if (it.isEmpty() || (it.toIntOrNull() in 1..31)) day = it }, label = { Text("Day of Month (1-31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (Optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = day.toIntOrNull() ?: 1
                    val a = amount.toDoubleOrNull() ?: 0.0
                    val category = APP_PRESETS.find { it.displayName == appName }?.category ?: "Other"
                    onSave(name, purpose, category, appName, a, d, notes)
                },
                enabled = name.isNotBlank() && day.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun getDaySuffix(day: Int): String {
    if (day <= 0) return ""
    if (day in 11..13) return "th"
    return when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}
