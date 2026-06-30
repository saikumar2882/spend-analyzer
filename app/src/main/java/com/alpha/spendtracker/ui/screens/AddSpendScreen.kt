/**
 * Screen for logging new spending transactions with details like amount, purpose, and date.
 */
package com.alpha.spendtracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.AppPreset
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import com.alpha.spendtracker.ui.components.PresetGridCard
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
    editingSpend: com.alpha.spendtracker.data.Spend? = null,
    prefilledSpend: NewSpend? = null,
    onDismiss: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onSave: (NewSpend) -> Unit
) {
    var amountInput by rememberSaveable { 
        mutableStateOf(
            editingSpend?.amount?.toString() ?: (if (prefilledSpend != null && prefilledSpend.amount > 0) prefilledSpend.amount.toString() else "")
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
    var notesInput by rememberSaveable { 
        mutableStateOf(editingSpend?.notes ?: prefilledSpend?.notes ?: "") 
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

    LaunchedEffect(prefilledSpend) {
        if (prefilledSpend != null && editingSpend == null) {
            amountInput = if (prefilledSpend.amount > 0) prefilledSpend.amount.toString() else ""
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
                title = { Text("Log a Transaction", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                AmountInputCard(
                    amount = amountInput,
                    onAmountChange = {
                        amountInput = it
                        amountError = null
                    },
                    isError = amountError != null,
                    errorMessage = amountError
                )
            }

            item { SectionTitle("Select Payment App & Wallet") }

            item {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(APP_PRESETS, key = { it.id }) { preset ->
                            PresetGridCard(
                                preset = preset,
                                isSelected = selectedPreset.id == preset.id,
                                onClick = { selectedPreset = preset }
                            )
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
                        label = { Text("Enter App Name") },
                        placeholder = { Text("E.g., Cred, Jupiter, Dunzo…") },
                        singleLine = true,
                        isError = customAppError != null,
                        supportingText = customAppError?.let { msg -> { Text(msg) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            item { SectionTitle("Date of Transaction") }

            item {
                DateSelectorRow(
                    timestamp = transactionTimestamp,
                    onTimestampChange = { transactionTimestamp = it },
                    onShowNotification = onShowNotification
                )
            }

            item { SectionTitle("Transaction Purpose (Categorization)") }

            item {
                OutlinedTextField(
                    value = purposeInput,
                    onValueChange = { purposeInput = it },
                    label = { Text("Purpose Details / Custom Category") },
                    placeholder = { Text("E.g. Dinner with friends, Groceries, Clothes…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                PurposePresetGrid(
                    selected = purposeInput,
                    onSelect = { purposeInput = it }
                )
            }

            item {
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Optional description / short note") },
                    placeholder = { Text("E.g. Sent to Alice for lunch…") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Button(
                    onClick = {
                        val parsedAmount = amountInput.toDoubleOrNull()
                        val amtIssue = when {
                            parsedAmount == null -> "Enter an amount to continue"
                            parsedAmount <= 0.0 -> "Amount must be greater than ₹0"
                            else -> null
                        }
                        val appIssue = if (selectedPreset.id == "other" && customAppNameInput.isBlank())
                            "Please enter the app name" else null

                        amountError = amtIssue
                        customAppError = appIssue

                        val firstIssue = amtIssue ?: appIssue
                        if (firstIssue != null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar(firstIssue) }
                            return@Button
                        }

                        onSave(
                            NewSpend(
                                preset = selectedPreset,
                                amount = parsedAmount!!,
                                purpose = purposeInput.ifBlank { "Others" },
                                notes = notesInput,
                                customAppName = customAppNameInput,
                                timestamp = transactionTimestamp
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
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
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AmountInputCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    isError: Boolean,
    errorMessage: String?
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Surface(
        color = if (isError)
            MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        else
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AMOUNT SPENT",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "₹",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp, end = 4.dp)
                )
                TextField(
                    value = amount,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) onAmountChange(input)
                    },
                    placeholder = {
                        Text(
                            "0.00",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black)
                        )
                    },
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    modifier = Modifier.width(220.dp)
                )
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
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
        Box(
            modifier = Modifier
                .weight(1.3f)
                .background(
                    color = if (isCustomSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                    val activity = context.findActivity() ?: run {
                        onShowNotification("Could not open the date picker", NotificationType.ERROR)
                        return@clickable
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
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.Event,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isCustomSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCustomSelected) formatShortDate(timestamp) else "Choose…",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isCustomSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RowScope.DateChip(
    label: String,
    isSelected: Boolean,
    weight: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PurposePresetGrid(
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PURPOSE_PRESETS.chunked(3).forEach { chunk ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                chunk.forEach { purpose ->
                    val isSelected = selected.equals(purpose, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(purpose) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = purpose,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun suggestedPurposeFor(preset: AppPreset, current: String): String =
    when (preset.id) {
        "swiggy", "zepto", "blinkit" -> "Groceries & Food"
        "amazon", "flipkart", "myntra", "ajio" -> "Shopping & Apparels"
        else -> current
    }
