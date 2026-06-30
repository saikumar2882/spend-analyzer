/**
 * A dedicated settings screen consolidating appearance, security, AI defaults and account options.
 */
package com.alpha.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.AiPreferences
import com.alpha.spendtracker.ui.components.AiSettingsDialog
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themePreference: ThemePreference,
    aiPreferences: AiPreferences,
    onBack: () -> Unit,
    onCycleTheme: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onUpdateAiPreferences: (String, String, String) -> Unit,
    onToggleBiometrics: (Boolean) -> Unit,
    onAiAssistantClick: () -> Unit,
    onRecurringBillsClick: () -> Unit,
    onShareApp: () -> Unit,
    onLogout: () -> Unit
) {
    var showSecurityOptions by remember { mutableStateOf(false) }
    var showPasswordUpdateDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    var displayName by remember { mutableStateOf(auth.currentUser?.displayName.orEmpty()) }
    val email = auth.currentUser?.email.orEmpty()

    if (showProfileDialog) {
        ProfileDialog(
            currentName = displayName,
            email = email,
            onDismiss = { showProfileDialog = false },
            onSave = { newName ->
                val request = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                auth.currentUser?.updateProfile(request)?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        displayName = newName
                        showProfileDialog = false
                        onShowNotification("Profile updated", NotificationType.SUCCESS)
                    } else {
                        onShowNotification(
                            "Error: ${task.exception?.message ?: "Could not save"}",
                            NotificationType.ERROR
                        )
                    }
                }
            }
        )
    }

    if (showSecurityOptions) {
        AlertDialog(
            onDismissRequest = { showSecurityOptions = false },
            title = { Text("Account Security") },
            text = { Text("Would you like to update your password or receive a reset link via email?") },
            confirmButton = {
                TextButton(onClick = {
                    showSecurityOptions = false
                    showPasswordUpdateDialog = true
                }) { Text("Update Password") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSecurityOptions = false
                    if (email.isNotBlank()) {
                        auth.sendPasswordResetEmail(email)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    onShowNotification("Reset email sent to $email", NotificationType.SUCCESS)
                                } else {
                                    onShowNotification("Error: ${task.exception?.message}", NotificationType.ERROR)
                                }
                            }
                    }
                }) { Text("Forgot Password") }
            }
        )
    }

    if (showPasswordUpdateDialog) {
        var newPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var isUpdating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isUpdating) showPasswordUpdateDialog = false },
            title = { Text("Update Password") },
            text = {
                Column {
                    Text("Enter your new password below:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.length < 6) {
                            onShowNotification("Password should be at least 6 characters", NotificationType.ERROR)
                            return@Button
                        }
                        isUpdating = true
                        auth.currentUser?.updatePassword(newPassword)
                            ?.addOnCompleteListener { task ->
                                isUpdating = false
                                if (task.isSuccessful) {
                                    onShowNotification("Password updated successfully!", NotificationType.SUCCESS)
                                    showPasswordUpdateDialog = false
                                } else {
                                    onShowNotification("Error: ${task.exception?.message}", NotificationType.ERROR)
                                }
                            }
                    },
                    enabled = newPassword.isNotEmpty() && !isUpdating
                ) {
                    if (isUpdating) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordUpdateDialog = false }, enabled = !isUpdating) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAiSettingsDialog) {
        AiSettingsDialog(
            currentPrefs = aiPreferences,
            onSave = { currency, app, purpose ->
                onUpdateAiPreferences(currency, app, purpose)
                showAiSettingsDialog = false
                onShowNotification("Default settings updated", NotificationType.SUCCESS)
            },
            onDismiss = { showAiSettingsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsGroup(title = "Appearance") {
                    SettingsRow(
                        icon = when (themePreference) {
                            ThemePreference.SYSTEM -> Icons.Rounded.BrightnessAuto
                            ThemePreference.LIGHT -> Icons.Rounded.LightMode
                            ThemePreference.DARK -> Icons.Rounded.DarkMode
                        },
                        title = "Theme",
                        subtitle = when (themePreference) {
                            ThemePreference.SYSTEM -> "Follow system"
                            ThemePreference.LIGHT -> "Light"
                            ThemePreference.DARK -> "Dark"
                        },
                        onClick = onCycleTheme,
                        trailing = {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(Radius.xs)
                            ) {
                                Text(
                                    text = when (themePreference) {
                                        ThemePreference.SYSTEM -> "Auto"
                                        ThemePreference.LIGHT -> "Light"
                                        ThemePreference.DARK -> "Dark"
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "Security") {
                    SettingsRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometric Lock",
                        subtitle = "Require authentication to open the app",
                        iconTint = if (aiPreferences.isBiometricEnabled)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onToggleBiometrics(!aiPreferences.isBiometricEnabled) },
                        trailing = {
                            Switch(
                                checked = aiPreferences.isBiometricEnabled,
                                // Display-only: the row's onClick is the single toggle source, so the
                                // Switch must not also handle the change (would double-fire and cancel out).
                                onCheckedChange = null
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Password,
                        title = "Account Security",
                        subtitle = "Change or reset your password",
                        onClick = { showSecurityOptions = true }
                    )
                }
            }

            item {
                SettingsGroup(title = "AI & Defaults") {
                    SettingsRow(
                        icon = Icons.Rounded.Tune,
                        title = "App Defaults",
                        subtitle = "${aiPreferences.defaultCurrency} · ${aiPreferences.defaultApp} · ${aiPreferences.defaultPurpose}",
                        onClick = { showAiSettingsDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "AI History Assistant",
                        subtitle = "Ask questions about your spending",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onAiAssistantClick
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = "Recurring Bills",
                        subtitle = "Manage subscriptions and bill reminders",
                        onClick = onRecurringBillsClick
                    )
                }
            }

            item {
                SettingsGroup(title = "Account") {
                    SettingsRow(
                        icon = Icons.Rounded.Person,
                        title = "Profile",
                        subtitle = email.ifBlank { "Edit your display name" },
                        onClick = { showProfileDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Share,
                        title = "Share App",
                        subtitle = "Tell your friends about Spendly",
                        onClick = onShareApp
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = "Sign Out",
                        subtitle = "Log out of your account",
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = onLogout
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.sm),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}
