package com.alpha.spendtracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AiInputBottomSheet(
    onProcess: (String) -> Unit,
    onDismiss: () -> Unit,
    remainingRequests: Int,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    var textInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        // Intercept back button to show discard dialog
        BackHandler(enabled = true) {
            onDismiss()
        }
        
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Tell AI about your spend",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("AI is thinking...", style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                text = "Daily limit: $remainingRequests requests left",
                style = MaterialTheme.typography.labelSmall,
                color = if (remainingRequests < 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )

            OutlinedTextField(
                value = textInput,
                onValueChange = { if (it.length <= 500) textInput = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                placeholder = { Text("e.g., Spend 300 on biryani using phone pay") },
                supportingText = { Text("${textInput.length}/500") },
                minLines = 2,
                trailingIcon = {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = { 
                                if (textInput.isNotBlank()) {
                                    isProcessing = true
                                    onProcess(textInput) 
                                }
                            },
                            enabled = textInput.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                        }
                    }
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )

            if (!isProcessing) {
                Text(
                    "Try one of these:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                val examples = listOf(
                    "Spend 300 on biryani using phone pay",
                    "Paid 250 for uber ride via gpay",
                    "Lent 1000 to a friend",
                    "Bought medicines worth 450 at apollo"
                )
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    examples.forEach { ex ->
                        SuggestionChip(
                            onClick = { textInput = ex },
                            label = { Text(ex, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
