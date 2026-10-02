package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.R
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Spacing
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetEditorDialog(
    currentBudget: Double,
    currency: String,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isEditing = currentBudget > 0.0

    val initialText = if (isEditing) {
        if (currentBudget % 1.0 == 0.0) currentBudget.toLong().toString()
        else currentBudget.toString()
    } else ""

    var textInput by remember { mutableStateOf(initialText) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val presetAmounts = listOf(5000.0, 10000.0, 25000.0, 50000.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isEditing) R.string.edit_monthly_budget else R.string.set_monthly_budget
                ),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.monthly_budget_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { input ->
                        // Allow digits and optional single decimal point
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            textInput = input
                            errorMessage = null
                        }
                    },
                    label = { Text(stringResource(R.string.budget_amount)) },
                    prefix = {
                        Text(
                            text = "$currency ",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetAmounts.forEach { preset ->
                        val presetStr = if (preset % 1.0 == 0.0) preset.toLong().toString() else preset.toString()
                        val isSelected = textInput == presetStr

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                textInput = presetStr
                                errorMessage = null
                            },
                            label = {
                                Text(
                                    text = "$currency ${formatCompactNumber(preset)}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = RoundedCornerShape(Radius.sm),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = textInput.toDoubleOrNull()
                    if (value == null || value <= 0.0) {
                        errorMessage = "Please enter a valid budget amount (> 0)"
                    } else {
                        runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        onSave(value)
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.apply),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing && textInput.isBlank()) {
                    TextButton(
                        onClick = {
                            runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            onSave(0.0)
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.remove_budget),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

private fun formatCompactNumber(amount: Double): String {
    return when {
        amount >= 100000 -> "${(amount / 100000).toCleanString()}L"
        amount >= 1000 -> "${(amount / 1000).toCleanString()}k"
        else -> amount.toCleanString()
    }
}

private fun Double.toCleanString(): String {
    return if (this % 1.0 == 0.0) this.toLong().toString()
    else String.format(Locale.US, "%.1f", this)
}
