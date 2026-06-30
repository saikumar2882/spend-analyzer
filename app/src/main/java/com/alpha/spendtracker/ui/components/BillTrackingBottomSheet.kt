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
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.util.findActivity
import java.text.SimpleDateFormat
import java.util.Calendar

import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillTrackingBottomSheet(
    show: Boolean,
    sheetState: SheetState,
    prefilledSpend: NewSpend,
    onConfirm: (NewSpend) -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    var amount by rememberSaveable { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf(prefilledSpend.preset) }
    var customAppName by rememberSaveable { mutableStateOf(prefilledSpend.customAppName) }
    var purpose by rememberSaveable { mutableStateOf(prefilledSpend.purpose) }
    var notes by rememberSaveable { mutableStateOf(prefilledSpend.notes) }
    var selectedTimestamp by rememberSaveable { mutableStateOf(prefilledSpend.timestamp) }

    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("EEE, d MMM yyyy", locale) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        BackHandler(enabled = true) {
            onCancel()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Summary Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${amount.ifBlank { "0" }}",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (selectedPreset.id == "other") customAppName else selectedPreset.displayName,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Category,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$purpose • ${notes.ifBlank { "Bill payment" }}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Event,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = dateFormatter.format(selectedTimestamp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Input Fields
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.matches(Regex("""^\d*\.?\d*$"""))) amount = it },
                label = { Text("Amount") },
                leadingIcon = { Icon(Icons.Rounded.Payments, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            AppPresetDropdown(
                selected = selectedPreset,
                onSelect = { selectedPreset = it }
            )

            if (selectedPreset.id == "other") {
                OutlinedTextField(
                    value = customAppName,
                    onValueChange = { customAppName = it },
                    label = { Text("App / Platform") },
                    leadingIcon = { Icon(Icons.Rounded.CreditCard, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
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
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            // Date Field
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val activity = context.findActivity()
                        if (activity != null) {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                            DatePickerDialog(
                                activity,
                                { _, year, month, day ->
                                    val updated = Calendar.getInstance().apply {
                                        set(year, month, day, 12, 0, 0)
                                    }
                                    selectedTimestamp = updated.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    },
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dateFormatter.format(selectedTimestamp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("Scheduled bill", fontSize = 10.sp) },
                        enabled = false,
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            disabledLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Cancel") }

                Button(
                    onClick = {
                        val finalAmount = amount.toDoubleOrNull() ?: 0.0
                        onConfirm(
                            NewSpend(
                                preset = selectedPreset,
                                amount = finalAmount,
                                purpose = purpose,
                                notes = notes,
                                customAppName = customAppName,
                                timestamp = selectedTimestamp
                            )
                        )
                    },
                    modifier = Modifier.weight(1.3f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = amount.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
                ) {
                    Text("Confirm & Save", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(24.dp))
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
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("App / Platform") },
            leadingIcon = { Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(selected.color)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            APP_PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.displayName) },
                    onClick = { onSelect(preset); expanded = false },
                    leadingIcon = { Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(preset.color)) }
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
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Purpose") },
            leadingIcon = { Icon(Icons.Rounded.Category, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PURPOSE_PRESETS.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p) },
                    onClick = { onSelect(p); expanded = false }
                )
            }
        }
    }
}
