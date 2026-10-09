package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PrivacyPolicyDialog(
    isE2eEncryptionEnabled: Boolean,
    onToggleE2eEncryption: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    isLoginFlow: Boolean = false,
    onAcceptAndContinue: ((Boolean) -> Unit)? = null
) {
    var isE2eOptedIn by remember { mutableStateOf(isE2eEncryptionEnabled) }
    var hasAgreedToTerms by remember { mutableStateOf(!isLoginFlow) }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = {
            if (!isLoginFlow) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isLoginFlow,
            dismissOnClickOutside = !isLoginFlow
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // --- Title Header ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Spendly data protection & choices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                // --- Scrollable Policy Body (Pure Text & Clean Toggle, No Boxes) ---
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cloud Encryption Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newState = !isE2eOptedIn
                                isE2eOptedIn = newState
                                if (!isLoginFlow) {
                                    onToggleE2eEncryption(newState)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "End-to-End Cloud Encryption",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isE2eOptedIn) "AES-256 client encryption enabled" else "Opt-in to encrypt cloud backups",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isE2eOptedIn,
                            onCheckedChange = { newState ->
                                isE2eOptedIn = newState
                                if (!isLoginFlow) {
                                    onToggleE2eEncryption(newState)
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Policy Sections (Minimal Typography)
                    TextSection(
                        title = "1. Offline-First Storage",
                        body = "All your expense records, notes, and recurring bills are stored locally in a sandboxed database on your device. Syncing to cloud storage requires account sign-in."
                    )

                    TextSection(
                        title = "2. AI & PII Protection",
                        body = "Natural language expense entries are scrubbed locally for bank numbers, phone numbers, and personal names before processing via cloud AI APIs."
                    )

                    TextSection(
                        title = "3. Zero Data Sale",
                        body = "We never sell, rent, trade, or monetize your financial data. Your data belongs strictly to you."
                    )

                    TextSection(
                        title = "4. Voice Dictation",
                        body = "Voice input is converted to text transiently via system speech services. Raw audio recordings are never recorded or saved."
                    )

                    TextSection(
                        title = "5. Account & Deletion",
                        body = "You can delete individual records or your entire account at any time, permanently purging all associated data."
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                // --- Minimal Footer ---
                if (isLoginFlow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hasAgreedToTerms = !hasAgreedToTerms }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hasAgreedToTerms,
                            onCheckedChange = { hasAgreedToTerms = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I have read and agree to all the privacy policies",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (hasAgreedToTerms) {
                                onAcceptAndContinue?.invoke(isE2eOptedIn)
                            }
                        },
                        enabled = hasAgreedToTerms,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Accept & Continue",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextSection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
