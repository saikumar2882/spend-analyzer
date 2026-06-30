/**
 * The main overview screen displaying spending summaries, charts, and recent activity.
 */
package com.alpha.spendtracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.AiPreferences
import com.alpha.spendtracker.ui.components.AiSettingsDialog
import com.alpha.spendtracker.ui.components.DateRangePickerModal
import com.alpha.spendtracker.ui.components.EmptyStateCard
import com.alpha.spendtracker.ui.components.QuickStatsRow
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.alpha.spendtracker.ui.components.RecentSpendRow
import com.alpha.spendtracker.ui.components.WhereItWentCard
import com.alpha.spendtracker.ui.components.TimeFilterSelectorRow
import com.alpha.spendtracker.ui.components.TotalSpentHeroCard
import com.alpha.spendtracker.ui.viewmodel.SpendingAnalytics
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    currentFilter: TimeFilter,
    analytics: SpendingAnalytics,
    recentSpends: List<Spend>,
    themePreference: ThemePreference,
    aiPreferences: AiPreferences,
    onCycleTheme: () -> Unit,
    onFilterSelect: (TimeFilter) -> Unit,
    onCustomRangeSelect: (Long, Long) -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onShowAllClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onLentClick: () -> Unit,
    onTransactionsClick: () -> Unit,
    onLogout: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onUpdateAiPreferences: (String, String, String) -> Unit,
    onToggleBiometrics: (Boolean) -> Unit,
    onShareApp: () -> Unit,
    onRecurringBillsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showSecurityOptions by remember { mutableStateOf(false) }
    var showPasswordUpdateDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    var displayName by remember { mutableStateOf(auth.currentUser?.displayName.orEmpty()) }

    if (showProfileDialog) {
        ProfileDialog(
            currentName = displayName,
            email = auth.currentUser?.email.orEmpty(),
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
                    val email = auth.currentUser?.email
                    if (email != null) {
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

    if (showDatePicker) {
        DateRangePickerModal(
            initialStart = null,
            initialEnd = null,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                onCustomRangeSelect(start, end)
                showDatePicker = false
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            DashboardHeader(
                displayName = displayName,
                themePreference = themePreference,
                onCycleTheme = onCycleTheme,
                onProfileClick = { showProfileDialog = true },
                onAiAssistantClick = onAiAssistantClick,
                onSettingsClick = onSettingsClick
            )
        }

        item {
            TimeFilterSelectorRow(
                selected = currentFilter,
                onSelect = onFilterSelect,
                onCustomClick = { showDatePicker = true }
            )
        }

        item {
            val periodDeltaPct = if (currentFilter != TimeFilter.ALL && analytics.previousPeriodTotal > 0.0) {
                ((analytics.totalAmount - analytics.previousPeriodTotal) / analytics.previousPeriodTotal) * 100.0
            } else null
            TotalSpentHeroCard(
                filterType = currentFilter,
                totalAmount = analytics.totalAmount,
                transactionCount = analytics.transactionCount,
                dateRange = analytics.dateRange,
                periodDeltaPct = periodDeltaPct,
                onLentClick = onLentClick,
                onTransactionsClick = onTransactionsClick
            )
        }

        if (analytics.transactionCount > 0) {
            item { QuickStatsRow(analytics = analytics) }

            item {
                WhereItWentCard(
                    categoryBreakdown = analytics.categoryBreakdown,
                    trendPoints = analytics.trendPoints,
                    onCategoryClick = { category ->
                        onShowNotification("Filtering by $category", NotificationType.INFO)
                    }
                )
            }
        } else {
            item { EmptyStateCard() }
        }

        if (recentSpends.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(title = "Recent Activity")
                    TextButton(onClick = onShowAllClick) {
                        Text(
                            "See all",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = "Show history",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            items(recentSpends, key = { it.uuid }) { spend ->
                RecentSpendRow(
                    spend = spend,
                    onClick = { onAppClick(spend.appName) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DashboardHeader(
    displayName: String,
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onProfileClick: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val firstName = displayName.trim().split(" ").firstOrNull().orEmpty()
    val greeting = if (firstName.isBlank()) "Hi there 👋" else "Hi, $firstName 👋"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onProfileClick)
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeaderActionButton(
                icon = Icons.Rounded.AutoAwesome,
                onClick = onAiAssistantClick,
                contentDescription = "AI Assistant",
                tint = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            )
            HeaderActionButton(
                icon = when (themePreference) {
                    ThemePreference.SYSTEM -> Icons.Rounded.BrightnessAuto
                    ThemePreference.LIGHT -> Icons.Rounded.LightMode
                    ThemePreference.DARK -> Icons.Rounded.DarkMode
                },
                onClick = onCycleTheme,
                contentDescription = when (themePreference) {
                    ThemePreference.SYSTEM -> "Theme: follow system. Tap to switch to light."
                    ThemePreference.LIGHT -> "Theme: light. Tap to switch to dark."
                    ThemePreference.DARK -> "Theme: dark. Tap to follow system."
                },
                tint = MaterialTheme.colorScheme.onSurface,
                background = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            HeaderActionButton(
                icon = Icons.Rounded.Settings,
                onClick = onSettingsClick,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface,
                background = MaterialTheme.colorScheme.surfaceContainerHigh
            )

        }
    }
}

@Composable
private fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    tint: Color,
    background: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = background,
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun ProfileDialog(
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
        title = { Text("Your Profile", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    com.alpha.spendtracker.ui.theme.BrandGradientStart,
                                    com.alpha.spendtracker.ui.theme.BrandGradientMid,
                                    com.alpha.spendtracker.ui.theme.BrandGradientEnd
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = email.ifBlank { "—" },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 60) nameInput = it },
                    label = { Text("Full name") },
                    placeholder = { Text("e.g., Tsai Kumar") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    supportingText = { Text("Used for greetings across the app") }
                )
                Spacer(modifier = Modifier.height(6.dp))
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
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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

