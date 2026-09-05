package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.data.AiPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsDialog(
    currentPrefs: AiPreferences,
    onSave: (currency: String, app: String, purpose: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currency by remember { mutableStateOf(currentPrefs.defaultCurrency) }
    var app by remember { mutableStateOf(currentPrefs.defaultApp) }
    var purpose by remember { mutableStateOf(currentPrefs.defaultPurpose) }

    val currencies = listOf("₹", "$", "€", "£")
    // Must be drawn from the canonical presets: the AI confirmation screen resolves the
    // default back to an APP_PRESET / PURPOSE_PRESET, so an off-list value would silently
    // fall back to "Other Platform" / "Others" instead of what the user picked.
    val apps = APP_PRESETS.map { it.displayName }
    val purposes = PURPOSE_PRESETS.filter { it != "Lending" && it != "Borrowing" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Default Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("These values will be used when they are missing from your AI input.")
                
                DropdownField(label = "Default Currency", options = currencies, selected = currency) { currency = it }
                DropdownField(label = "Default Payment App", options = apps, selected = app) { app = it }
                DropdownField(label = "Default Purpose", options = purposes, selected = purpose) { purpose = it }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(currency, app, purpose) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
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
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.015f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
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
