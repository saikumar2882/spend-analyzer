package com.alpha.spendtracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.BillFrequency
import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.data.SubscriptionSuggestion
import com.alpha.spendtracker.ui.components.APP_COLOR_BY_NAME
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.getLocalizedPresetName
import com.alpha.spendtracker.ui.components.AppIconImage
import com.alpha.spendtracker.ui.components.SwipeableLogCard
import com.alpha.spendtracker.ui.components.formatCurrency
import androidx.compose.ui.draw.scale
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.rememberPressScale
import com.alpha.spendtracker.util.findActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

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
        cardLast4: String,
        untilDate: Long?,
        frequency: String
    ) -> Unit,
    onUpdateBill: (RecurringBill) -> Unit,
    onDeleteBill: (RecurringBill) -> Unit,
    suggestions: List<SubscriptionSuggestion> = emptyList(),
    onDismissSuggestion: (SubscriptionSuggestion) -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Bills & Subscriptions, 1 = Credit Cards
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<RecurringBill?>(null) }
    var billToDelete by remember { mutableStateOf<RecurringBill?>(null) }
    // A suggestion being accepted opens the normal add dialog pre-filled, so the user confirms it.
    var acceptingSuggestion by remember { mutableStateOf<SubscriptionSuggestion?>(null) }

    val mainTabs = remember { listOf("Bills & Subscriptions", "Credit Cards") }

    val displayedBills = remember(bills, selectedTab) {
        if (selectedTab == 1) {
            bills.filter { it.isCreditCardBill }
        } else {
            bills.filter { !it.isCreditCardBill }
        }
    }

    val displayedSuggestions = remember(suggestions, selectedTab) {
        suggestions.filter { it.purpose.contains("Credit Card", ignoreCase = true) == (selectedTab == 1) }
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

                            val tabInteraction = remember { MutableInteractionSource() }
                            val tabScale = rememberPressScale(tabInteraction)
                            Surface(
                                onClick = { selectedTab = index },
                                interactionSource = tabInteraction,
                                shape = CircleShape,
                                color = if (isSelected) activeBg else Color.Transparent,
                                border = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(tabScale)
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
        if (displayedBills.isEmpty() && displayedSuggestions.isEmpty()) {
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
                        if (selectedTab == 1) stringResource(R.string.no_bills_tracked) else stringResource(R.string.no_bills_tracked),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.add_bill_hint),
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
                if (displayedSuggestions.isNotEmpty()) {
                    item(key = "suggestions-header") {
                        Text(
                            text = stringResource(R.string.subscription_suggestions_title),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.sm)
                        )
                    }
                    items(displayedSuggestions, key = { "suggestion-${it.key}" }) { suggestion ->
                        SubscriptionSuggestionItem(
                            suggestion = suggestion,
                            onAdd = { acceptingSuggestion = suggestion },
                            onDismiss = { onDismissSuggestion(suggestion) }
                        )
                    }
                }
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
                title = { Text(stringResource(R.string.delete_transaction_title)) },
                text = { Text(stringResource(R.string.delete_entry_message, billToDelete?.name.orEmpty())) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = billToDelete
                            if (target != null) onDeleteBill(target)
                            billToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = { TextButton(onClick = { billToDelete = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (showAddDialog || editingBill != null || acceptingSuggestion != null) {
            BillEditDialog(
                bill = editingBill,
                prefill = acceptingSuggestion?.let { s ->
                    RecurringBill(
                        name = s.name,
                        purpose = s.purpose,
                        category = s.category,
                        appName = s.appName,
                        amount = s.amount,
                        dayOfMonth = s.dayOfMonth
                    )
                },
                defaultIsCreditCard = selectedTab == 1,
                onDismiss = {
                    showAddDialog = false
                    editingBill = null
                    acceptingSuggestion = null
                },
                onSave = { name, purpose, category, app, amount, day, notes, isCreditCard, cardLast4, untilDate, frequency ->
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
                                cardLast4 = cardLast4,
                                untilDate = untilDate,
                                frequency = frequency
                            )
                        )
                    } else {
                        onAddBill(name, purpose, category, app, amount, day, notes, isCreditCard, cardLast4, untilDate, frequency)
                    }
                    showAddDialog = false
                    editingBill = null
                    acceptingSuggestion = null
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

    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
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
                // Top Row: Bill Name + Due Date / Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = bill.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )

                    if (bill.isExpired) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(Radius.xxs)
                        ) {
                            Text(
                                text = "Ended",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Text(
                            text = dueLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (daysUntil <= 5) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = dueColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Bottom Row: App Name / Card Info + Frequency + Until Date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val hasAppOrCard = (bill.isCreditCardBill && bill.cardLast4.isNotBlank()) || bill.appName.isNotBlank()
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
                    } else if (bill.appName.isNotBlank()) {
                        Text(
                            text = getLocalizedPresetName(bill.appName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (hasAppOrCard) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    val freq = BillFrequency.fromString(bill.frequency)
                    Text(
                        text = getLocalizedPresetName(freq.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (bill.untilDate != null && !bill.isExpired) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        val locale = LocalConfiguration.current.locales[0]
                        val untilCal = remember(bill.untilDate) {
                            Calendar.getInstance().apply { timeInMillis = bill.untilDate }
                        }
                        val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
                        val pattern = if (untilCal.get(Calendar.YEAR) == currentYear) "d MMM" else "d MMM ''yy"
                        val untilStr = remember(bill.untilDate, locale) {
                            SimpleDateFormat(pattern, locale).format(Date(bill.untilDate))
                        }
                        Text(
                            text = "until $untilStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

/** One compact "looks monthly" row: app icon, name, amount + typical day, then Add / dismiss. */
@Composable
private fun SubscriptionSuggestionItem(
    suggestion: SubscriptionSuggestion,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = APP_COLOR_BY_NAME[suggestion.appName]
        ?: APP_PRESETS.find { it.displayName.equals(suggestion.appName, ignoreCase = true) }?.color
        ?: MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                appName = suggestion.appName,
                fallbackColor = accent,
                modifier = Modifier.size(38.dp)
            )

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getLocalizedPresetName(suggestion.name),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.subscription_suggestion_detail,
                        formatCurrency(suggestion.amount),
                        suggestion.dayOfMonth
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(onClick = onAdd) {
                Text(
                    stringResource(R.string.subscription_suggestion_add),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.subscription_suggestion_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
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
    // Initial values for a new bill (e.g. an accepted subscription suggestion). Ignored when editing.
    prefill: RecurringBill? = null,
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
        cardLast4: String,
        untilDate: Long?,
        frequency: String
    ) -> Unit
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("d MMM yyyy", locale) }

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

    val initial = bill ?: prefill
    var purpose by remember(initial, defaultIsCreditCard) {
        mutableStateOf(
            initial?.purpose.takeIf { !it.isNullOrBlank() }
                ?: if (defaultIsCreditCard || initial?.isCreditCardBill == true) "Credit Card Bill" else "Rent & Utilities"
        )
    }
    var isCreditCardMode by remember(purpose, defaultIsCreditCard) {
        mutableStateOf(purpose.contains("Credit Card", ignoreCase = true) || defaultIsCreditCard || initial?.isCreditCardBill == true)
    }

    var frequency by remember(initial) {
        mutableStateOf(BillFrequency.fromString(initial?.frequency))
    }

    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var cardLast4 by remember(initial) { mutableStateOf(initial?.cardLast4 ?: "") }
    var appName by remember(initial) { mutableStateOf(initial?.appName ?: APP_PRESETS.first().displayName) }
    var amount by remember(initial) { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var day by remember(initial) { mutableStateOf(initial?.dayOfMonth?.toString() ?: "1") }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var untilDate by remember(initial) { mutableStateOf<Long?>(initial?.untilDate) }

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
        title = { Text(if (bill == null) (if (isCreditCardMode) stringResource(R.string.credit_cards) else stringResource(R.string.add_recurring_bill)) else stringResource(R.string.edit_recurring_bill)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. Purpose Dropdown shown first
                var purposeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = purposeExpanded, onExpandedChange = { purposeExpanded = it }) {
                    OutlinedTextField(
                        value = getLocalizedPresetName(purpose),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.purpose)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = purposeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )
                    ExposedDropdownMenu(expanded = purposeExpanded, onDismissRequest = { purposeExpanded = false }) {
                        purposeOptions.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(getLocalizedPresetName(p)) },
                                onClick = {
                                    purpose = p
                                    isCreditCardMode = p.contains("Credit Card", ignoreCase = true)
                                    purposeExpanded = false
                                }
                            )
                        }
                    }
                }

                // 2. Frequency Dropdown
                var frequencyExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = frequencyExpanded, onExpandedChange = { frequencyExpanded = it }) {
                    OutlinedTextField(
                        value = getLocalizedPresetName(frequency.displayName),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.frequency)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )
                    ExposedDropdownMenu(expanded = frequencyExpanded, onDismissRequest = { frequencyExpanded = false }) {
                        BillFrequency.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(getLocalizedPresetName(f.displayName)) },
                                onClick = {
                                    frequency = f
                                    frequencyExpanded = false
                                }
                            )
                        }
                    }
                }

                if (isCreditCardMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.card_name)) },
                        placeholder = { Text("e.g. HDFC Regalia, ICICI Amazon") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )

                    OutlinedTextField(
                        value = cardLast4,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) cardLast4 = it },
                        label = { Text(stringResource(R.string.last_4_digits)) },
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
                        label = { Text(stringResource(R.string.entry_amount_optional)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )

                    var appExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = appExpanded, onExpandedChange = { appExpanded = it }) {
                        OutlinedTextField(
                            value = getLocalizedPresetName(appName),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.default_payment_app)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanTextFieldColors
                        )
                        ExposedDropdownMenu(expanded = appExpanded, onDismissRequest = { appExpanded = false }) {
                            APP_PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(getLocalizedPresetName(preset.displayName)) },
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
                        label = { Text(stringResource(R.string.due_day_of_month)) },
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
                        label = { Text(stringResource(R.string.bill_name)) },
                        placeholder = { Text("e.g. Rent, Maid, Driver, YouTube") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) amount = it },
                        label = { Text(stringResource(R.string.entry_amount_optional)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )

                    var appExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = appExpanded, onExpandedChange = { appExpanded = it }) {
                        OutlinedTextField(
                            value = getLocalizedPresetName(appName),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.payment_app)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanTextFieldColors
                        )
                        ExposedDropdownMenu(expanded = appExpanded, onDismissRequest = { appExpanded = false }) {
                            APP_PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(getLocalizedPresetName(preset.displayName)) },
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
                        label = { Text(stringResource(R.string.due_day_of_month)) },
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
                    label = { Text(stringResource(R.string.note_detail_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = cleanTextFieldColors
                )

                // Optional Recurring Until Date Field
                OutlinedCard(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = untilDate ?: System.currentTimeMillis()
                            }
                            DatePickerDialog(
                                activity,
                                { _, year, month, dayOfMonth ->
                                    val selected = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth, 23, 59, 59)
                                    }
                                    untilDate = selected.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                    ),
                    border = BorderStroke(0.dp, Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recurring Until (Optional)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (untilDate != null) "Until ${dateFormatter.format(Date(untilDate!!))}" else "No end date (Indefinite)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (untilDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        if (untilDate != null) {
                            IconButton(
                                onClick = { untilDate = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear end date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Rounded.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
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
                    onSave(name, finalPurpose, category, appName, a, d, notes, isCard, cardLast4, untilDate, frequency.name)
                },
                enabled = name.isNotBlank() && day.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
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
