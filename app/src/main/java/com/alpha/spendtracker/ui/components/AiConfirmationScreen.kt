package com.alpha.spendtracker.ui.components

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.ui.icons.AppIcons
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.util.findActivity
import java.text.SimpleDateFormat
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfirmationScreen(
    extractedData: AiTransactionResponse,
    onConfirm: (NewSpend) -> Unit,
    onCancel: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    defaultApp: String = "Google Pay",
    defaultPurpose: String = "Others",
    currencySymbol: String = "₹"
) {
    val initialPreset = remember(extractedData, defaultApp) {
        APP_PRESETS.firstOrNull { it.id == extractedData.appPresetId }
            ?: APP_PRESETS.firstOrNull { it.displayName.equals(extractedData.appName, ignoreCase = true) }
            ?: APP_PRESETS.firstOrNull { it.displayName.equals(defaultApp, ignoreCase = true) }
            ?: APP_PRESETS.last()
    }

    var amount by remember { mutableStateOf(extractedData.amount?.let { formatAmount(it) } ?: "") }
    var selectedPreset by remember { mutableStateOf(initialPreset) }
    var customAppName by remember {
        mutableStateOf(
            if (initialPreset.id == "other") (extractedData.appName ?: "")
            else ""
        )
    }
    var purpose by remember {
        mutableStateOf(
            PURPOSE_PRESETS.firstOrNull { it.equals(extractedData.purpose, ignoreCase = true) }
                ?: PURPOSE_PRESETS.firstOrNull { it.equals(defaultPurpose, ignoreCase = true) }
                ?: "Others"
        )
    }
    var notes by remember { mutableStateOf(extractedData.notes) }
    var selectedTimestamp by remember {
        mutableLongStateOf(extractedData.timestamp ?: System.currentTimeMillis())
    }

    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("EEE, d MMM yyyy", locale) }
    val isAiExtractedDate = extractedData.timestamp != null

    BackHandler(enabled = true) {
        onCancel()
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Ai,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Review AI Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Double-check before saving",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ExtractedSummaryCard(
            amount = amount,
            appName = if (selectedPreset.id == "other") customAppName.ifBlank { "Other" } else selectedPreset.displayName,
            purpose = purpose,
            notes = notes,
            dateLabel = dateFormatter.format(selectedTimestamp),
            currencySymbol = currencySymbol
        )

        if (extractedData.needsAmount && amount.isBlank()) {
            Text(
                "Please enter the spend amount below.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        OutlinedTextField(
            value = amount,
            onValueChange = { input ->
                if (input.matches(Regex("""^\d*\.?\d*$"""))) amount = input
            },
            label = { Text("Amount") },
            leadingIcon = { Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        AppPresetDropdown(
            selected = selectedPreset,
            onSelect = { selectedPreset = it },
            colors = fieldColors
        )

        if (selectedPreset.id == "other") {
            OutlinedTextField(
                value = customAppName,
                onValueChange = { customAppName = it },
                label = { Text("Custom App / Platform") },
                leadingIcon = if (customAppName.isNotBlank()) {
                    {
                        AppIconImage(
                            appName = customAppName,
                            fallbackColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    { Icon(Icons.Rounded.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp)) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp)
            )
        }

        PurposeDropdown(
            selected = purpose,
            onSelect = { purpose = it },
            colors = fieldColors
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Description / Notes") },
            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
            placeholder = { Text("e.g. Biryani, Uber ride") },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            shape = RoundedCornerShape(12.dp)
        )

        DateField(
            timestamp = selectedTimestamp,
            isAiExtracted = isAiExtractedDate,
            dateFormatter = dateFormatter,
            onPick = { picked -> selectedTimestamp = picked },
            onPickError = { onShowNotification(it, NotificationType.ERROR) }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Cancel") }

            Button(
                onClick = {
                    val finalAmount = amount.toDoubleOrNull() ?: 0.0
                    onConfirm(
                        NewSpend(
                            amount = finalAmount,
                            purpose = purpose.ifBlank { "Others" },
                            notes = notes.trim(),
                            preset = selectedPreset,
                            customAppName = if (selectedPreset.id == "other") customAppName.trim() else "",
                            timestamp = selectedTimestamp
                        )
                    )
                },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = amount.toDoubleOrNull()?.let { it > 0 } == true &&
                          (selectedPreset.id != "other" || customAppName.isNotBlank())
            ) { Text("Confirm & Save", fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ExtractedSummaryCard(
    amount: String,
    appName: String,
    purpose: String,
    notes: String,
    dateLabel: String,
    currencySymbol: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$currencySymbol${amount.ifBlank { "0" }}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    AppIconImage(
                        appName = appName,
                        fallbackColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = purpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (notes.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DateField(
    timestamp: Long,
    isAiExtracted: Boolean,
    dateFormatter: SimpleDateFormat,
    onPick: (Long) -> Unit,
    onPickError: (String) -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val activity = context.findActivity() ?: run {
                    onPickError("Could not open the date picker")
                    return@clickable
                }
                val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                DatePickerDialog(
                    activity,
                    { _, year, month, dayOfMonth ->
                        val updated = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, 12, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onPick(updated.timeInMillis)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateFormatter.format(timestamp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
            }
            if (isAiExtracted) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        "AI detected",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    "Change",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPresetDropdown(
    selected: AppPreset,
    onSelect: (AppPreset) -> Unit,
    colors: TextFieldColors
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("App / Platform") },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(selected.color)
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = colors
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            APP_PRESETS.forEach { preset ->
                DropdownMenuItem(
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(preset.color)
                        )
                    },
                    text = {
                        Column {
                            Text(preset.displayName, fontWeight = FontWeight.Medium)
                            Text(
                                preset.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurposeDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    colors: TextFieldColors
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category / Purpose") },
            leadingIcon = { Icon(Icons.Rounded.Category, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = colors
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PURPOSE_PRESETS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else value.toString()
