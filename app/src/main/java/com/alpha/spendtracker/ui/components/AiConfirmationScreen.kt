package com.alpha.spendtracker.ui.components

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.util.findActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfirmationScreen(
    extractedData: AiTransactionResponse,
    onConfirm: (NewSpend) -> Unit,
    onCancel: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit
) {
    val initialPreset = remember(extractedData) {
        APP_PRESETS.firstOrNull { it.id == extractedData.appPresetId }
            ?: APP_PRESETS.firstOrNull { it.displayName.equals(extractedData.appName, ignoreCase = true) }
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
                ?: "Others"
        )
    }
    var notes by remember { mutableStateOf(extractedData.notes) }
    var selectedTimestamp by remember {
        mutableStateOf(extractedData.timestamp ?: System.currentTimeMillis())
    }

    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }
    val isAiExtractedDate = extractedData.timestamp != null

    BackHandler(enabled = true) {
        onCancel()
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Review & Confirm",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "AI extracted the details below. Double-check before saving.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ExtractedSummaryCard(
            amount = amount,
            appName = if (selectedPreset.id == "other") customAppName.ifBlank { "Other" } else selectedPreset.displayName,
            purpose = purpose,
            notes = notes,
            dateLabel = dateFormatter.format(selectedTimestamp)
        )

        if (extractedData.needsAmount && amount.isBlank()) {
            Text(
                "AI couldn't find the amount. Please enter it below.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { input ->
                if (input.matches(Regex("""^\d*\.?\d*$"""))) amount = input
            },
            label = { Text("Amount") },
            leadingIcon = { Icon(Icons.Rounded.Payments, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        AppPresetDropdown(
            selected = selectedPreset,
            onSelect = { selectedPreset = it }
        )

        if (selectedPreset.id == "other") {
            OutlinedTextField(
                value = customAppName,
                onValueChange = { customAppName = it },
                label = { Text("Custom App / Platform name") },
                leadingIcon = { Icon(Icons.Rounded.CreditCard, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        PurposeDropdown(
            selected = purpose,
            onSelect = { purpose = it }
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Description") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
            placeholder = { Text("e.g., Biryani") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
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
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
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
    dateLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₹${amount.ifBlank { "0" }}",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = purpose,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (notes.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
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
    OutlinedCard(
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
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateFormatter.format(timestamp),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
            }
            if (isAiExtracted) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("AI detected", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        disabledLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                Text(
                    "Tap to change",
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
    onSelect: (AppPreset) -> Unit
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
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(selected.color)
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
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
                                .size(16.dp)
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
    onSelect: (String) -> Unit
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
            label = { Text("Purpose") },
            leadingIcon = { Icon(Icons.Rounded.Category, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
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
