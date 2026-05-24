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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
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
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.EmptyStateCard
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.alpha.spendtracker.ui.components.RecentSpendRow
import com.alpha.spendtracker.ui.components.SpendingDonutChart
import com.alpha.spendtracker.ui.components.SpendingTrendBarChart
import com.alpha.spendtracker.ui.components.TimeFilterSelectorRow
import com.alpha.spendtracker.ui.components.TopAppsRankingCard
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
    onCycleTheme: () -> Unit,
    onFilterSelect: (TimeFilter) -> Unit,
    onCustomRangeSelect: (Long, Long) -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onShowAllClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onLentClick: () -> Unit,
    onLogout: () -> Unit,
    onAiAssistantClick: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showSecurityOptions by remember { mutableStateOf(false) }
    var showPasswordUpdateDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

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
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            onCustomRangeSelect(start, end)
                        }
                        showDatePicker = false
                    },
                    enabled = dateRangePickerState.selectedStartDateMillis != null && 
                             dateRangePickerState.selectedEndDateMillis != null
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp)) },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        item {
            DashboardHeader(
                themePreference = themePreference,
                onCycleTheme = onCycleTheme,
                onLogout = onLogout,
                onSecurityClick = { showSecurityOptions = true },
                onAiAssistantClick = onAiAssistantClick
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
            TotalSpentHeroCard(
                filterType = currentFilter,
                totalAmount = analytics.totalAmount,
                transactionCount = analytics.transactionCount,
                dateRange = analytics.dateRange,
                onLentClick = onLentClick
            )
        }

        if (analytics.transactionCount > 0) {
            item {
                Column {
                    Text(
                        text = "Category Breakdown",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SpendingDonutChart(
                        categoryBreakdown = analytics.categoryBreakdown,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SpendingTrendBarChart(
                    trendPoints = analytics.trendPoints,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                TopAppsRankingCard(
                    appBreakdown = analytics.appBreakdown,
                    onAppClick = onAppClick
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
                    Text(
                        text = "Recent Spending's",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onShowAllClick) {
                        Text("See All History")
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
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onLogout: () -> Unit,
    onSecurityClick: () -> Unit,
    onAiAssistantClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onAiAssistantClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Assistant",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onSecurityClick) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = "Security Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            ThemeToggleButton(
                themePreference = themePreference,
                onCycle = onCycleTheme
            )
            Spacer(modifier = Modifier.size(4.dp))
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ThemeToggleButton(
    themePreference: ThemePreference,
    onCycle: () -> Unit
) {
    val icon = when (themePreference) {
        ThemePreference.SYSTEM -> Icons.Rounded.BrightnessAuto
        ThemePreference.LIGHT -> Icons.Rounded.LightMode
        ThemePreference.DARK -> Icons.Rounded.DarkMode
    }
    val description = when (themePreference) {
        ThemePreference.SYSTEM -> "Theme: follow system. Tap to switch to light."
        ThemePreference.LIGHT -> "Theme: light. Tap to switch to dark."
        ThemePreference.DARK -> "Theme: dark. Tap to follow system."
    }

    IconButton(onClick = onCycle) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
