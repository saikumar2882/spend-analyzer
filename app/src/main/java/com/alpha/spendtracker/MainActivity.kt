/**
 * The main entry point for the Spend Tracker application, handling navigation and theme management.
 */
package com.alpha.spendtracker

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.screens.AddSpendScreen
import com.alpha.spendtracker.ui.screens.DashboardScreen
import com.alpha.spendtracker.ui.screens.HistoryScreen
import com.alpha.spendtracker.ui.screens.LoginScreen
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.ui.components.AiSettingsDialog
import com.alpha.spendtracker.ui.components.AiInputBottomSheet
import com.alpha.spendtracker.ui.components.AiConfirmationScreen
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.alpha.spendtracker.ui.theme.isDark
import com.alpha.spendtracker.ui.theme.next
import com.alpha.spendtracker.ui.theme.rememberThemePreference
import com.alpha.spendtracker.ui.viewmodel.SpendViewModel
import com.alpha.spendtracker.ui.viewmodel.SpendViewModelFactory
import com.google.firebase.auth.FirebaseAuth

enum class ActiveView { DASHBOARD, HISTORY, ADD_SPEND }

class MainActivity : ComponentActivity() {
    private val spendViewModel: SpendViewModel by viewModels {
        SpendViewModelFactory(applicationContext as Application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleEmailLink(intent)
        enableEdgeToEdge()
        setContent {
            val themePref = rememberThemePreference()
            MyApplicationTheme(darkTheme = themePref.value.isDark()) {
                MainContainer(
                    viewModel = spendViewModel,
                    themePreference = themePref.value,
                    onCycleTheme = { themePref.value = themePref.value.next() }
                )
            }
        }
    }

    private fun handleEmailLink(intent: Intent?) {
        val auth = FirebaseAuth.getInstance()
        val link = intent?.data?.toString()
        if (link != null && auth.isSignInWithEmailLink(link)) {
            // In a real app, you'd retrieve the email from local storage or ask the user
            val email = "" 
            auth.signInWithEmailLink(email, link)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Signed in with email link!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error signing in with link", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(
    viewModel: SpendViewModel,
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var isRegistering by remember { mutableStateOf(false) }

    // Single source of truth for auth state
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null || isRegistering) {
        LoginScreen(
            onLoginSuccess = { 
                isRegistering = false
            },
            onRegisteringStart = { isRegistering = true },
            onRegisteringFinished = { isRegistering = false }
        )
        return
    }

    // Authenticated UI starts here
    var activeView by rememberSaveable { mutableStateOf(ActiveView.DASHBOARD) }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var historyCategoryFilter by rememberSaveable { mutableStateOf("All") }
    var editingSpend by remember { mutableStateOf<Spend?>(null) }

    val allSpends by viewModel.allSpendsFlow.collectAsStateWithLifecycle()
    val analyticsState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val aiPrefs by viewModel.aiPreferences.collectAsStateWithLifecycle()
    val aiResult by viewModel.aiResult.collectAsStateWithLifecycle()
    
    // ... rest of the state declarations remain the same

    var showAiInput by remember { mutableStateOf<Boolean?>(null) } // true for voice, false for type
    var showAiSettings by remember { mutableStateOf(false) }
    var aiProcessingResult by remember { mutableStateOf<AiTransactionResponse?>(null) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle AI Results
    LaunchedEffect(aiResult) {
        aiResult?.let { result ->
            if (result.isSuccess) {
                aiProcessingResult = result.getOrNull()
                showAiInput = null
            } else {
                Toast.makeText(context, result.exceptionOrNull()?.message ?: "AI Error", Toast.LENGTH_LONG).show()
                viewModel.clearAiResult()
            }
        }
    }

    BackHandler(enabled = activeView != ActiveView.DASHBOARD) {
        activeView = ActiveView.DASHBOARD
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (activeView != ActiveView.ADD_SPEND) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = activeView == ActiveView.DASHBOARD,
                        onClick = { activeView = ActiveView.DASHBOARD },
                        icon = { Icon(Icons.Rounded.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = activeView == ActiveView.HISTORY,
                        onClick = {
                            historySearchQuery = ""
                            historyCategoryFilter = "All"
                            activeView = ActiveView.HISTORY
                        },
                        icon = { Icon(Icons.Rounded.History, contentDescription = "History") },
                        label = { Text("History") }
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeView == ActiveView.DASHBOARD || activeView == ActiveView.HISTORY) {
                var showFabMenu by remember { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(
                        visible = showFabMenu,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End, 
                            modifier = Modifier.padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // AI Voice Option
                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    if (!aiPrefs.isConfigured) showAiSettings = true
                                    else showAiInput = true
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                icon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
                                text = { Text("AI Voice") }
                            )
                            
                            // AI Type Option
                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    if (!aiPrefs.isConfigured) showAiSettings = true
                                    else showAiInput = false
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                                text = { Text("AI Type") }
                            )

                            // Manual Entry Option
                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    editingSpend = null
                                    activeView = ActiveView.ADD_SPEND
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                text = { Text("Manual") }
                            )
                        }
                    }
                    
                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        icon = { 
                            Icon(
                                Icons.Rounded.Add, 
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = if (showFabMenu) 45f else 0f }
                            ) 
                        },
                        text = { Text(if (showFabMenu) "Close Tracking" else "Track Spend") },
                        containerColor = if (showFabMenu) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (showFabMenu) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeView,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "screen-switch"
            ) { view ->
                when (view) {
                    ActiveView.DASHBOARD -> DashboardScreen(
                        currentFilter = currentFilter,
                        analytics = analyticsState,
                        recentSpends = allSpends.take(5),
                        themePreference = themePreference,
                        onCycleTheme = onCycleTheme,
                        onFilterSelect = viewModel::setFilter,
                        onCustomRangeSelect = viewModel::setCustomRange,
                        onShowAllClick = {
                            historySearchQuery = ""
                            historyCategoryFilter = "All"
                            activeView = ActiveView.HISTORY
                        },
                        onAppClick = { appName ->
                            historySearchQuery = appName
                            historyCategoryFilter = "All"
                            activeView = ActiveView.HISTORY
                        },
                        onLentClick = {
                            historySearchQuery = ""
                            historyCategoryFilter = "Friend Lending"
                            activeView = ActiveView.HISTORY
                        },
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                        }
                    )
                    ActiveView.HISTORY -> HistoryScreen(
                        allSpends = allSpends,
                        initialSearchQuery = historySearchQuery,
                        initialCategoryFilter = historyCategoryFilter,
                        onEditSpend = { spend ->
                            editingSpend = spend
                            activeView = ActiveView.ADD_SPEND
                        },
                        onDeleteSpend = viewModel::deleteSpend,
                        onBackClick = { activeView = ActiveView.DASHBOARD }
                    )
                    ActiveView.ADD_SPEND -> AddSpendScreen(
                        editingSpend = editingSpend,
                        onDismiss = { 
                            editingSpend = null
                            activeView = ActiveView.DASHBOARD 
                        },
                        onSave = { newSpend: NewSpend ->
                            val appName = if (newSpend.preset.id == "other")
                                newSpend.customAppName.trim() else newSpend.preset.displayName
                            
                            if (editingSpend != null) {
                                viewModel.updateSpend(
                                    editingSpend!!.copy(
                                        appName = appName,
                                        amount = newSpend.amount,
                                        purpose = newSpend.purpose,
                                        category = newSpend.preset.category,
                                        notes = newSpend.notes,
                                        timestamp = newSpend.timestamp
                                    )
                                )
                                Toast.makeText(context, "Spending updated successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addSpend(
                                    appName = appName,
                                    amount = newSpend.amount,
                                    purpose = newSpend.purpose,
                                    category = newSpend.preset.category,
                                    notes = newSpend.notes,
                                    timestamp = newSpend.timestamp
                                )
                                Toast.makeText(context, "Spending logged successfully!", Toast.LENGTH_SHORT).show()
                            }
                            editingSpend = null
                            activeView = ActiveView.DASHBOARD
                        }
                    )
                }
            }
        }
    }

    if (showAiSettings) {
        AiSettingsDialog(
            currentPrefs = aiPrefs,
            onSave = { currency, app, purpose ->
                viewModel.updateAiSettings(currency, app, purpose)
                showAiSettings = false
            },
            onDismiss = { showAiSettings = false }
        )
    }

    if (showAiInput != null) {
        AiInputBottomSheet(
            isVoiceMode = showAiInput == true,
            remainingRequests = 10 - aiPrefs.dailyUsageCount,
            onProcess = { viewModel.processAiInput(it) },
            onDismiss = { showAiInput = null }
        )
    }

    if (aiProcessingResult != null) {
        ModalBottomSheet(onDismissRequest = { 
            aiProcessingResult = null
            viewModel.clearAiResult()
        }) {
            AiConfirmationScreen(
                extractedData = aiProcessingResult!!,
                onConfirm = { newSpend ->
                    viewModel.addSpend(
                        appName = if (newSpend.preset.id == "other") newSpend.customAppName else newSpend.preset.displayName,
                        amount = newSpend.amount,
                        purpose = newSpend.purpose,
                        category = newSpend.preset.category,
                        notes = newSpend.notes,
                        timestamp = newSpend.timestamp
                    )
                    Toast.makeText(context, "Logged via AI!", Toast.LENGTH_SHORT).show()
                    aiProcessingResult = null
                    viewModel.clearAiResult()
                },
                onCancel = {
                    aiProcessingResult = null
                    viewModel.clearAiResult()
                }
            )
        }
    }
}
