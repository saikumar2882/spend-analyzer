/**
 * Display-name / account dialog, shared by the Dashboard greeting and the Settings account row.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.ui.theme.BrandGradientEnd
import com.alpha.spendtracker.ui.theme.BrandGradientMid
import com.alpha.spendtracker.ui.theme.BrandGradientStart
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Spacing

@Composable
fun ProfileDialog(
    currentName: String,
    email: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }
    var isSaving by remember { mutableStateOf(false) }
    val initial = currentName.trim().firstOrNull()?.uppercase()
        ?: email.firstOrNull()?.uppercase() ?: "?"

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Your Profile", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(BrandGradientStart, BrandGradientMid, BrandGradientEnd)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(Radius.xs)
                ) {
                    Text(
                        text = email.ifBlank { "—" },
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.ml))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 60) nameInput = it },
                    label = { Text("Full name") },
                    placeholder = { Text("e.g., Tsai Kumar") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    supportingText = { Text("Used for greetings across the app") }
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Profile photos aren't available on the free plan.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = nameInput.trim()
                    if (trimmed == currentName.trim() || trimmed.isEmpty()) {
                        onDismiss()
                        return@Button
                    }
                    isSaving = true
                    onSave(trimmed)
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(Spacing.ml), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Close") }
        }
    )
}
