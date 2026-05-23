package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val apps = listOf("Google Pay", "PhonePe", "Paytm", "Cash", "Credit Card", "Debit Card")
    val purposes = listOf("Food", "Groceries", "Shopping", "Bills", "Others")

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
            modifier = Modifier.menuAnchor().fillMaxWidth()
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
