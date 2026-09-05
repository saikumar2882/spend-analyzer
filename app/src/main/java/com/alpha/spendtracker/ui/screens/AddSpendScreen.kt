/**
 * Clean "Log a Transaction" form layout for logging new spending or editing existing transactions.
 * Designed with refined form controls, soft tonal fills, tabular numeric typography, and clear focus.
 */
package com.alpha.spendtracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.AppIconImage
import com.alpha.spendtracker.ui.components.AppPreset
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import com.alpha.spendtracker.ui.components.PresetGridCard
import com.alpha.spendtracker.ui.components.parseLendBorrowNotes
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.asMoney
import com.alpha.spendtracker.util.findActivity
import com.alpha.spendtracker.util.formatShortDate
import com.alpha.spendtracker.util.isSameDay
import com.alpha.spendtracker.util.yesterdayMillis
import kotlinx.coroutines.launch
import java.util.Calendar

data class NewSpend(
    val preset: AppPreset,
    val amount: Double,
    val purpose: String,
    val notes: String,
    val customAppName: String,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpendScreen(
    editingSpend: Spend? = null,
    prefilledSpend: NewSpend? = null,
    onDismiss: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onSave: (NewSpend) -> Unit
) {
    var amountInput by rememberSaveable { 
        mutableStateOf(
            editingSpend?.amount?.let { if (it > 0) formatPlainAmount(it) else "" } 
                ?: (if (prefilledSpend != null && prefilledSpend.amount > 0) formatPlainAmount(prefilledSpend.amount) else "")
        ) 
    }
    var selectedPreset by remember { 
        mutableStateOf(
            when {
                editingSpend != null -> APP_PRESETS.find { it.displayName == editingSpend.appName } ?: APP_PRESETS.last()
                prefilledSpend != null -> prefilledSpend.preset
                else -> APP_PRESETS.first()
            }
        ) 
    }
    var purposeInput by rememberSaveable { 
        mutableStateOf(
            editingSpend?.purpose ?: prefilledSpend?.purpose ?: PURPOSE_PRESETS.first()
        ) 
    }

    val initialLendBorrowParsed = remember(editingSpend) {
        if (editingSpend != null && (editingSpend.purpose == "Lending" || editingSpend.purpose == "Borrowing")) {
            parseLendBorrowNotes(editingSpend.notes, editingSpend.appName)
        } else null
    }

    var personNameInput by rememberSaveable { 
        mutableStateOf(initialLendBorrowParsed?.first ?: "") 
    }
    var notesInput by rememberSaveable { 
        mutableStateOf(initialLendBorrowParsed?.second ?: editingSpend?.notes ?: prefilledSpend?.notes ?: "") 
    }
    var customAppNameInput by rememberSaveable { 
        mutableStateOf(
            if (selectedPreset.id == "other") {
                editingSpend?.appName ?: prefilledSpend?.customAppName ?: ""
            } else ""
        ) 
    }
    var transactionTimestamp by rememberSaveable { 
        mutableLongStateOf(
            editingSpend?.timestamp ?: prefilledSpend?.timestamp ?: System.currentTimeMillis()
        ) 
    }

    val isLendBorrow = purposeInput.equals("Lending", ignoreCase = true) || purposeInput.equals("Borrowing", ignoreCase = true)

    LaunchedEffect(prefilledSpend) {
        if (prefilledSpend != null && editingSpend == null) {
            amountInput = if (prefilledSpend.amount > 0) formatPlainAmount(prefilledSpend.amount) else ""
            selectedPreset = prefilledSpend.preset
            purposeInput = prefilledSpend.purpose
            notesInput = prefilledSpend.notes
            customAppNameInput = prefilledSpend.customAppName
            transactionTimestamp = prefilledSpend.timestamp
        }
    }

    var amountError by remember { mutableStateOf<String?>(null) }
    var customAppError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedPreset) {
        purposeInput = suggestedPurposeFor(selectedPreset, purposeInput)
        if (selectedPreset.id != "other") customAppError = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (editingSpend != null) "Edit Transaction" else "Log a Transaction",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            item {
                AmountInputCard(
                    amount = amountInput,
                    onAmountChange = {
                        amountInput = it
                        amountError = null
                    },
                    onClear = {
                        amountInput = ""
                        amountError = null
                    },
                    isError = amountError != null,
                    errorMessage = amountError
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Payment app / Wallet")
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val gap = 12.dp
                        val cardWidth = (maxWidth - gap * 3) / 4
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            items(APP_PRESETS, key = { it.id }) { preset ->
                                Box(modifier = Modifier.width(cardWidth)) {
                                    PresetGridCard(
                                        preset = preset,
                                        isSelected = selectedPreset.id == preset.id,
                                        onClick = { selectedPreset = preset }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedPreset.id == "other") {
                item {
                    OutlinedTextField(
                        value = customAppNameInput,
                        onValueChange = {
                            customAppNameInput = it
                            customAppError = null
                        },
                        label = { Text("Enter App / Platform Name") },
                        placeholder = { Text("E.g. Cred, Jupiter, Cash, Bank Transfer...") },
                        leadingIcon = if (customAppNameInput.isNotBlank()) {
                            {
                                AppIconImage(
                                    appName = customAppNameInput,
                                    fallbackColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else null,
                        singleLine = true,
                        isError = customAppError != null,
                        supportingText = customAppError?.let { msg -> { Text(msg) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Date & Time")
                    DateSelectorRow(
                        timestamp = transactionTimestamp,
                        onTimestampChange = { transactionTimestamp = it },
                        onShowNotification = onShowNotification
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle("Category")
                    PurposePresetGrid(
                        selected = purposeInput,
                        onSelect = { purposeInput = it }
                    )
                }
            }

            if (isLendBorrow) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Name of Person")
                        OutlinedTextField(
                            value = personNameInput,
                            onValueChange = { personNameInput = it },
                            placeholder = { Text("E.g. Alex, Ram, Rahul...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Notes (Optional)")
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = { Text("E.g. Groceries, Lunch, Movie tickets...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val parsedAmount = amountInput.toDoubleOrNull()
                        val amtIssue = when {
                            parsedAmount == null -> "Enter an amount to continue"
                            parsedAmount <= 0.0 -> "Amount must be greater than ₹0"
                            else -> null
                        }

                        amountError = amtIssue
                        customAppError = null

                        if (amtIssue != null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar(amtIssue) }
                            return@Button
                        }

                        val finalNotes = if (isLendBorrow && personNameInput.isNotBlank()) {
                            if (notesInput.isNotBlank()) "${personNameInput.trim()} - ${notesInput.trim()}"
                            else personNameInput.trim()
                        } else {
                            notesInput
                        }

                        onSave(
                            NewSpend(
                                preset = selectedPreset,
                                amount = parsedAmount!!,
                                purpose = purposeInput.ifBlank { "Others" },
                                notes = finalNotes,
                                customAppName = if (selectedPreset.id == "other" && customAppNameInput.isBlank()) "Other" else customAppNameInput,
                                timestamp = transactionTimestamp
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 1.dp
                    )
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Save Transaction",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        ),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AmountInputCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    onClear: () -> Unit,
    isError: Boolean,
    errorMessage: String?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        // The only state that earns an outline here is the error state.
        border = if (isError) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Amount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "₹",
                    style = MaterialTheme.typography.headlineMedium.asMoney(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                TextField(
                    value = amount,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) onAmountChange(input)
                    },
                    placeholder = {
                        Text(
                            "0",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            style = MaterialTheme.typography.displaySmall.asMoney()
                        )
                    },
                    textStyle = MaterialTheme.typography.displaySmall.asMoney().copy(
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = isError,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (amount.isNotBlank()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Clear,
                            contentDescription = "Clear amount",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DateSelectorRow(
    timestamp: Long,
    onTimestampChange: (Long) -> Unit,
    onShowNotification: (String, NotificationType) -> Unit
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val isTodaySelected = isSameDay(timestamp, now)
    val isYesterdaySelected = isSameDay(timestamp, yesterdayMillis())
    val isCustomSelected = !isTodaySelected && !isYesterdaySelected

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        DateChip(
            label = "Today",
            isSelected = isTodaySelected,
            weight = 1.0f,
            onClick = { onTimestampChange(System.currentTimeMillis()) }
        )
        DateChip(
            label = "Yesterday",
            isSelected = isYesterdaySelected,
            weight = 1.0f,
            onClick = { onTimestampChange(yesterdayMillis()) }
        )
        DateChip(
            label = if (isCustomSelected) formatShortDate(timestamp) else "Choose date",
            isSelected = isCustomSelected,
            weight = 1.3f,
            icon = Icons.Rounded.Event,
            onClick = {
                val activity = context.findActivity() ?: run {
                    onShowNotification("Could not open the date picker", NotificationType.ERROR)
                    return@DateChip
                }
                val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                DatePickerDialog(
                    activity,
                    { _, year, month, dayOfMonth ->
                        val updated = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        }
                        onTimestampChange(updated.timeInMillis)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        )
    }
}

@Composable
private fun RowScope.DateChip(
    label: String,
    isSelected: Boolean,
    weight: Float,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
        border = null,
        modifier = Modifier.weight(weight)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PurposePresetGrid(
    selected: String,
    onSelect: (String) -> Unit
) {
    val items = PURPOSE_PRESETS

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(3).forEach { chunk ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                chunk.forEach { purpose ->
                    val isSelected = selected.equals(purpose, ignoreCase = true)
                    val isBorrowing = purpose.equals("Borrowing", ignoreCase = true)

                    val activeBg = if (isBorrowing) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.secondary
                    val activeFg = if (isBorrowing) MaterialTheme.colorScheme.onError
                                   else MaterialTheme.colorScheme.onSecondary

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) activeBg else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onSelect(purpose) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = purpose,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            color = if (isSelected) activeFg else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                repeat(3 - chunk.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatPlainAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        amount.toLong().toString()
    } else {
        amount.toString()
    }
}

private fun suggestedPurposeFor(preset: AppPreset, current: String): String =
    when (preset.id) {
        "swiggy", "zepto", "blinkit" -> "Groceries & Food"
        "amazon", "flipkart", "myntra", "ajio" -> "Shopping & Apparels"
        else -> current
    }

@Preview(showBackground = true)
@Composable
fun AddSpendScreenPreview() {
    MyApplicationTheme {
        AddSpendScreen(
            onDismiss = {},
            onShowNotification = { _, _ -> },
            onSave = {}
        )
    }
}

