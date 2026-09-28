/**
 * A dedicated settings screen consolidating appearance, security, AI defaults and account options.
 */
package com.alpha.spendtracker.ui.screens

import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.rounded.Language
import com.alpha.spendtracker.R
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Password
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
import com.alpha.spendtracker.data.AiPreferences
import com.alpha.spendtracker.ui.components.AiSettingsDialog
import com.alpha.spendtracker.ui.components.AppAvatar
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.components.ProfileDialog
import com.alpha.spendtracker.ui.icons.AppIcons
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

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
    onNotesClick: () -> Unit,
    onShareApp: () -> Unit,
    onLogout: () -> Unit
) {
    var showSecurityOptions by remember { mutableStateOf(false) }
    var showPasswordUpdateDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentLangTag = if (!currentLocales.isEmpty) currentLocales[0]?.language.orEmpty() else ""

    val currentLanguageDisplay = when (currentLangTag) {
        "te" -> stringResource(R.string.language_telugu)
        "hi" -> stringResource(R.string.language_hindi)
        "en" -> stringResource(R.string.language_english)
        else -> stringResource(R.string.language_system)
    }

    val auth = FirebaseAuth.getInstance()
    var displayName by remember { mutableStateOf(auth.currentUser?.displayName.orEmpty()) }
    val email = auth.currentUser?.email.orEmpty()
    val photoUrl = auth.currentUser?.photoUrl?.toString()

    val effectiveDisplayName = remember(displayName, email) {
        if (displayName.isNotBlank()) {
            displayName.trim()
        } else if (email.isNotBlank()) {
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

    if (showProfileDialog) {
        ProfileDialog(
            currentName = displayName,
            email = email,
            photoUrl = photoUrl,
            onDismiss = { showProfileDialog = false },
            onSave = { newName ->
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    showProfileDialog = false
                    onShowNotification("Please sign in to update your profile", NotificationType.ERROR)
                    return@ProfileDialog
                }
                val request = UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                currentUser.updateProfile(request)
                    .addOnCompleteListener { task ->
                        showProfileDialog = false
                        if (task.isSuccessful) {
                            displayName = newName
                            onShowNotification("Profile updated", NotificationType.SUCCESS)
                        } else {
                            onShowNotification(
                                "Error: ${task.exception?.message ?: "Could not save profile"}",
                                NotificationType.ERROR
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        showProfileDialog = false
                        onShowNotification(
                            "Error: ${e.message ?: "Could not save profile"}",
                            NotificationType.ERROR
                        )
                    }
            }
        )
    }

    if (showSecurityOptions) {
        AlertDialog(
            onDismissRequest = { showSecurityOptions = false },
            title = { Text(stringResource(R.string.account_security_title)) },
            text = { Text(stringResource(R.string.account_security_subtitle)) },
            confirmButton = {
                TextButton(onClick = {
                    showSecurityOptions = false
                    showPasswordUpdateDialog = true
                }) { Text(stringResource(R.string.change_password)) }
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
                }) { Text(stringResource(R.string.forgot_password)) }
            }
        )
    }

    if (showPasswordUpdateDialog) {
        var newPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var isUpdating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isUpdating) showPasswordUpdateDialog = false },
            title = { Text(stringResource(R.string.change_password)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.new_password)) },
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
                        singleLine = true,
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
                    else Text(stringResource(R.string.update))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordUpdateDialog = false }, enabled = !isUpdating) {
                    Text(stringResource(R.string.cancel))
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

    if (showLanguageDialog) {
        val languages = listOf(
            "" to stringResource(R.string.language_system),
            "en" to stringResource(R.string.language_english),
            "te" to stringResource(R.string.language_telugu),
            "hi" to stringResource(R.string.language_hindi)
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_language), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    languages.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLanguageDialog = false
                                    val localeList = if (code.isBlank()) {
                                        LocaleListCompat.getEmptyLocaleList()
                                    } else {
                                        LocaleListCompat.forLanguageTags(code)
                                    }
                                    AppCompatDelegate.setApplicationLocales(localeList)
                                    (context as? Activity)?.recreate()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentLangTag == code),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (currentLangTag == code) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Profile Card Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { showProfileDialog = true })
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppAvatar(
                            name = effectiveDisplayName,
                            color = MaterialTheme.colorScheme.primary,
                            size = 52.dp,
                            photoUrl = photoUrl
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = effectiveDisplayName.ifBlank { "User" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = email.ifBlank { "Not signed in" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Language, Theme & Biometrics Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Rounded.Language,
                            title = stringResource(R.string.language_title),
                            subtitle = currentLanguageDisplay,
                            onClick = { showLanguageDialog = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = when (themePreference) {
                                ThemePreference.SYSTEM -> AppIcons.ThemeAuto
                                ThemePreference.LIGHT -> AppIcons.ThemeLight
                                ThemePreference.DARK -> AppIcons.ThemeDark
                            },
                            title = stringResource(R.string.theme_title),
                            subtitle = when (themePreference) {
                                ThemePreference.SYSTEM -> stringResource(R.string.theme_system)
                                ThemePreference.LIGHT -> stringResource(R.string.theme_light)
                                ThemePreference.DARK -> stringResource(R.string.theme_dark)
                            },
                            onClick = onCycleTheme
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Fingerprint,
                            title = stringResource(R.string.biometric_title),
                            subtitle = stringResource(R.string.biometric_subtitle),
                            onClick = { onToggleBiometrics(!aiPreferences.isBiometricEnabled) },
                            trailing = {
                                Switch(
                                    checked = aiPreferences.isBiometricEnabled,
                                    onCheckedChange = null
                                )
                            }
                        )
                    }
                }
            }

            // Features & Preferences Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = stringResource(R.string.recurring_bills_title),
                            subtitle = stringResource(R.string.recurring_bills_subtitle),
                            onClick = onRecurringBillsClick
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = AppIcons.Notes,
                            title = stringResource(R.string.notes_title),
                            subtitle = stringResource(R.string.notes_subtitle),
                            onClick = onNotesClick
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = AppIcons.Ai,
                            title = stringResource(R.string.ai_assistant_title),
                            subtitle = stringResource(R.string.ai_assistant_subtitle),
                            onClick = onAiAssistantClick
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Tune,
                            title = stringResource(R.string.app_defaults_title),
                            subtitle = "${aiPreferences.defaultCurrency} · ${aiPreferences.defaultApp} · ${aiPreferences.defaultPurpose}",
                            onClick = { showAiSettingsDialog = true }
                        )
                    }
                }
            }

            // Account, Security & Sign Out Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Rounded.Password,
                            title = stringResource(R.string.account_security_title),
                            subtitle = stringResource(R.string.account_security_subtitle),
                            onClick = { showSecurityOptions = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Share,
                            title = stringResource(R.string.share_app_title),
                            subtitle = stringResource(R.string.share_app_subtitle),
                            onClick = onShareApp
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.AutoMirrored.Rounded.Logout,
                            title = stringResource(R.string.sign_out_title),
                            subtitle = stringResource(R.string.sign_out_subtitle),
                            iconTint = MaterialTheme.colorScheme.error,
                            titleColor = MaterialTheme.colorScheme.error,
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            onClick = onLogout
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
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
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(42.dp)
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    )
}
