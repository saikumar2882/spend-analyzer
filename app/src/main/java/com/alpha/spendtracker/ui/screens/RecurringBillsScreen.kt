package com.alpha.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.ui.components.APP_COLOR_BY_NAME
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.AppIconImage
import com.alpha.spendtracker.ui.components.SwipeableLogCard
import com.alpha.spendtracker.ui.components.formatCurrency
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Spacing
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringBillsScreen(
    bills: List<RecurringBill>,
    onBack: () -> Unit,
    onAddBill: (
        name: String,
        purpose: String,
        category: String,
        appName: String,
        amount: Double,
        dayOfMonth: Int,
        notes: String,
        isCreditCard: Boolean,
        cardLast4: String
    ) -> Unit,
    onUpdateBill: (RecurringBill) -> Unit,
    onDeleteBill: (RecurringBill) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Bills & Subscriptions, 1 = Credit Cards
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<RecurringBill?>(null) }
    var billToDelete by remember { mutableStateOf<RecurringBill?>(null) }

    val mainTabs = remember { listOf("Bills & Subscriptions", "Credit Cards") }

    val displayedBills = remember(bills, selectedTab) {
        if (selectedTab == 1) {
            bills.filter { it.isCreditCardBill }
        } else {
            bills.filter { !it.isCreditCardBill }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Recurring Bills",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Add Bill",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                // High-contrast Segmented Pill Tabs matching LendBorrowScreen
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.xs),
                    border = null
                ) {
                    Row(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        mainTabs.forEachIndexed { index, title ->
                            val isSelected = index == selectedTab
                            val activeBg = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                            val activeFg = if (index == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer

                            Surface(
                                onClick = { selectedTab = index },
                                shape = CircleShape,
                                color = if (isSelected) activeBg else Color.Transparent,
                                border = null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) activeFg else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (displayedBills.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                ) {
                    Icon(
                        if (selectedTab == 1) Icons.Rounded.CreditCard else Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        if (selectedTab == 1) "No credit cards added yet" else "No bills or subscriptions yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        if (selectedTab == 1) "Tap + to manage credit card bills & due dates." else "Tap + to add rent, utilities, driver, maid, or subscriptions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.ScreenPadding,
                    top = Spacing.md,
                    end = Spacing.ScreenPadding,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(displayedBills, key = { it.uuid }) { bill ->
                    SwipeableLogCard(
                        onEdit = { editingBill = bill },
                        onDelete = { billToDelete = bill }
                    ) {
                        RecurringBillItem(
                            bill = bill,
                            onClick = { editingBill = bill }
                        )
                    }
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
                defaultIsCreditCard = selectedTab == 1,
                onDismiss = {
                    showAddDialog = false
                    editingBill = null
                },
                onSave = { name, purpose, category, app, amount, day, notes, isCreditCard, cardLast4 ->
                    val currentEditing = editingBill
                    if (currentEditing != null) {
                        onUpdateBill(
                            currentEditing.copy(
                                name = name,
                                purpose = purpose,
                                category = category,
                                appName = app,
                                amount = amount,
                                dayOfMonth = day,
                                notes = notes,
                                isCreditCard = isCreditCard,
                                cardLast4 = cardLast4
                            )
                        )
                    } else {
                        onAddBill(name, purpose, category, app, amount, day, notes, isCreditCard, cardLast4)
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = APP_COLOR_BY_NAME[bill.appName]
        ?: APP_PRESETS.find { it.displayName.equals(bill.appName, ignoreCase = true) }?.color
        ?: MaterialTheme.colorScheme.primary

    val daysUntil = remember(bill.dayOfMonth) { daysUntilDue(bill.dayOfMonth) }
    val dueLabel = when {
        daysUntil == 0 -> "Due today"
        daysUntil == 1 -> "Due tomorrow"
        daysUntil <= 5 -> "Due in $daysUntil days"
        else -> "Due on ${bill.dayOfMonth}${getDaySuffix(bill.dayOfMonth)}"
    }
    val dueColor = if (daysUntil <= 5) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bill.isCreditCardBill) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.CreditCard,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                AppIconImage(
                    appName = bill.appName,
                    fallbackColor = accent,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bill.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (bill.isCreditCardBill && bill.cardLast4.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(Radius.xxs)
                        ) {
                            Text(
                                text = "•••• ${bill.cardLast4}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    } else if (!bill.isCreditCardBill && bill.appName.isNotBlank()) {
                        Text(
                            text = bill.appName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    Text(
                        text = dueLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (daysUntil <= 5) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = dueColor
                    )
                }
            }

            if (bill.amount > 0) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = "₹${formatCurrency(bill.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Approximate days until the next occurrence of [dayOfMonth] (for a "due soon" hint). */
private fun daysUntilDue(dayOfMonth: Int): Int {
    val cal = Calendar.getInstance()
    val todayDay = cal.get(Calendar.DAY_OF_MONTH)
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val target = dayOfMonth.coerceIn(1, maxDay)
    return if (target >= todayDay) target - todayDay else (maxDay - todayDay) + target
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditDialog(
    bill: RecurringBill?,
    defaultIsCreditCard: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        purpose: String,
        category: String,
        appName: String,
        amount: Double,
        day: Int,
        notes: String,
        isCreditCard: Boolean,
        cardLast4: String
    ) -> Unit
) {
    val purposeOptions = remember {
        listOf(
            "Credit Card Bill",
            "Rent & Utilities",
            "Subscription & Leisure",
            "Groceries & Food",
            "Shopping & Apparels",
            "Travel & Commute",
            "Healthcare & Medical",
            "Others"
        )
    }

    var purpose by remember(bill, defaultIsCreditCard) {
        mutableStateOf(
            bill?.purpose.takeIf { !it.isNullOrBlank() }
                ?: if (defaultIsCreditCard || bill?.isCreditCardBill == true) "Credit Card Bill" else "Rent & Utilities"
        )
    }
    var isCreditCardMode by remember(purpose, defaultIsCreditCard) {
        mutableStateOf(purpose.contains("Credit Card", ignoreCase = true) || defaultIsCreditCard || bill?.isCreditCardBill == true)
    }

    var name by remember(bill) { mutableStateOf(bill?.name ?: "") }
    var cardLast4 by remember(bill) { mutableStateOf(bill?.cardLast4 ?: "") }
    var appName by remember(bill) { mutableStateOf(bill?.appName ?: APP_PRESETS.first().displayName) }
    var amount by remember(bill) { mutableStateOf(bill?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var day by remember(bill) { mutableStateOf(bill?.dayOfMonth?.toString() ?: "1") }
    var notes by remember(bill) { mutableStateOf(bill?.notes ?: "") }

    val cleanTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.015f),
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bill == null) (if (isCreditCardMode) "Add Credit Card" else "Add Bill / Subscription") else "Edit Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. Purpose Dropdown shown first
                var purposeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = purposeExpanded, onExpandedChange = { purposeExpanded = it }) {
                    OutlinedTextField(
                        value = purpose,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Purpose / Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = purposeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )
                    ExposedDropdownMenu(expanded = purposeExpanded, onDismissRequest = { purposeExpanded = false }) {
                        purposeOptions.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    purpose = p
                                    isCreditCardMode = p.contains("Credit Card", ignoreCase = true)
                                    purposeExpanded = false
                                }
                            )
                        }
                    }
                }

                if (isCreditCardMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Card Name / Bank") },
                        placeholder = { Text("e.g. HDFC Regalia, ICICI Amazon") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )

                    OutlinedTextField(
                        value = cardLast4,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) cardLast4 = it },
                        label = { Text("Last 4 Digits") },
                        placeholder = { Text("e.g. 4321") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) amount = it },
                        label = { Text("Statement / Bill Amount (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )

                    var appExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = appExpanded, onExpandedChange = { appExpanded = it }) {
                        OutlinedTextField(
                            value = appName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Default Payment App") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanTextFieldColors
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

                    OutlinedTextField(
                        value = day,
                        onValueChange = { if (it.isEmpty() || (it.toIntOrNull() in 1..31)) day = it },
                        label = { Text("Statement Due Day of Month (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Bill / Subscription Name") },
                        placeholder = { Text("e.g. Rent, Maid, Driver, YouTube") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) amount = it },
                        label = { Text("Amount (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )

                    var appExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = appExpanded, onExpandedChange = { appExpanded = it }) {
                        OutlinedTextField(
                            value = appName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Default App") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanTextFieldColors
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

                    OutlinedTextField(
                        value = day,
                        onValueChange = { if (it.isEmpty() || (it.toIntOrNull() in 1..31)) day = it },
                        label = { Text("Day of Month (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = cleanTextFieldColors
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = day.toIntOrNull() ?: 1
                    val a = amount.toDoubleOrNull() ?: 0.0
                    val isCard = isCreditCardMode || purpose.contains("Credit Card", ignoreCase = true) || cardLast4.isNotBlank() || defaultIsCreditCard
                    val category = if (isCard) "Credit Card" else (APP_PRESETS.find { it.displayName == appName }?.category ?: "Other")
                    val finalPurpose = if (isCard) "Credit Card Bill" else purpose
                    onSave(name, finalPurpose, category, appName, a, d, notes, isCard, cardLast4)
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
