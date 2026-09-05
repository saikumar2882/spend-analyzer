/**
 * Display-name / account dialog, shared by the Dashboard greeting and the Settings account row.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.alpha.spendtracker.ui.theme.Spacing

@Composable
fun ProfileDialog(
    currentName: String,
    email: String,
    photoUrl: String? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }
    var isSaving by remember { mutableStateOf(false) }

    val fallbackFromEmail = remember(email) {
        if (email.isNotBlank()) {
            val handle = email.substringBefore('@').trim()
            handle.split('.', '_', '-')
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
        } else {
            ""
        }
    }
    val effectiveName = currentName.ifBlank { fallbackFromEmail }
    val initial = effectiveName.trim().firstOrNull()?.uppercase()
        ?: email.firstOrNull()?.uppercase() ?: "?"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = "Your Profile",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                // Large Round Avatar
                val initialBadge: @Composable () -> Unit = {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White
                        )
                    }
                }

                if (!photoUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    SubcomposeAsyncImage(
                        model = remember(photoUrl) {
                            ImageRequest.Builder(context)
                                .data(photoUrl)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = currentName,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        loading = { initialBadge() },
                        error = { initialBadge() }
                    )
                } else {
                    initialBadge()
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Email badge container (no outline, faded minimal tone)
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = email.ifBlank { "—" },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Full name input (faded seamless container, NO outlines/borders)
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= 60) nameInput = it },
                        label = { Text("Full name") },
                        placeholder = { Text(fallbackFromEmail.ifBlank { "e.g., Tsai Kumar" }) },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
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

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = "Used for greetings across the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = if (!photoUrl.isNullOrBlank()) {
                            "Profile photo loaded from your Google account."
                        } else {
                            "Sign in with Google to use your Google profile photo."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
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
                enabled = !isSaving,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(
                    horizontal = 24.dp,
                    vertical = 10.dp
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = CircleShape
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
