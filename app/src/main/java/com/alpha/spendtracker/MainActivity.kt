/**
 * The main entry point for the Spend Tracker application, handling navigation and theme management.
 */
package com.alpha.spendtracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.AiConfirmationScreen
import com.alpha.spendtracker.ui.components.AiInputBottomSheet
import com.alpha.spendtracker.ui.components.AiSettingsDialog
import com.alpha.spendtracker.ui.components.AppNotification
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.screens.AddSpendScreen
import com.alpha.spendtracker.ui.screens.DashboardScreen
import com.alpha.spendtracker.ui.screens.HistoryScreen
import com.alpha.spendtracker.ui.screens.LendBorrowScreen
import com.alpha.spendtracker.ui.screens.LoginScreen
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.alpha.spendtracker.ui.theme.isDark
import com.alpha.spendtracker.ui.theme.next
import com.alpha.spendtracker.ui.theme.rememberThemePreference
import com.alpha.spendtracker.ui.viewmodel.SpendViewModel
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ActiveView { DASHBOARD, LEND_BORROW, HISTORY, ADD_SPEND }

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val spendViewModel: SpendViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        showBiometricPrompt()

        enableEdgeToEdge()
        setContent {
            val themePref = rememberThemePreference()
            MyApplicationTheme(darkTheme = themePref.value.isDark()) {
                MainContainer(
                    viewModel = spendViewModel,
                    themePreference = themePref.value,
                    onCycleTheme = { themePref.value = themePref.value.next() },
                    intent = intent
                )
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user cancels or error occurs, we might want to close the app 
                    // or show a backup password screen. For now, we'll just log it.
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for Spendly")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use account password")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun handleEmailLink(intent: Intent?, onShowNotification: (String, NotificationType) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val link = intent?.data?.toString()
        if (link != null && auth.isSignInWithEmailLink(link)) {
            val email = "" 
            auth.signInWithEmailLink(email, link)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onShowNotification("Signed in with email link!", NotificationType.SUCCESS)
                    } else {
                        onShowNotification("Error signing in with link", NotificationType.ERROR)
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
    onCycleTheme: () -> Unit,
    intent: Intent? = null
) {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var isRegistering by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var currentNotification by remember { mutableStateOf<Pair<String, NotificationType>?>(null) }

    fun showNotification(message: String, type: NotificationType = NotificationType.INFO) {
        currentNotification = message to type
    }

    LaunchedEffect(currentNotification) {
        if (currentNotification != null) {
            delay(3000)
            currentNotification = null
        }
    }

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
            onLoginSuccess = { isRegistering = false },
            onShowNotification = { msg, type -> showNotification(msg, type) },
            onRegisteringStart = { isRegistering = true },
            onRegisteringFinished = { isRegistering = false }
        )
        return
    }

    // AI Flow State
    var showAiInput by remember { mutableStateOf(false) } 
    var showAiHistoryAssistant by remember { mutableStateOf(false) }
    var aiProcessingResult by remember { mutableStateOf<AiTransactionResponse?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var discardCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Navigation State
    var activeView by rememberSaveable { mutableStateOf(ActiveView.DASHBOARD) }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var historyCategoryFilter by rememberSaveable { mutableStateOf("All") }
    var historyTimeFilter by rememberSaveable { mutableStateOf<TimeFilter>(TimeFilter.ALL) }
    var editingSpend by remember { mutableStateOf<Spend?>(null) }

    val allSpends by viewModel.allSpendsFlow.collectAsStateWithLifecycle()
    val analyticsState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val aiPrefs by viewModel.aiPreferences.collectAsStateWithLifecycle()
    val aiResult by viewModel.aiResult.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val historyStatus by viewModel.historyStatus.collectAsStateWithLifecycle()
    
    val aiInputSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiConfirmationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiHistorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Closes the discard dialog. If the underlying sheet was hidden by a user
    // gesture (swipe / scrim tap) before the dialog appeared, re-expand it so
    // we never leave a stale scrim on screen without its sheet.
    val dismissDiscardDialog = {
        showDiscardDialog = false
        discardCallback = null
        scope.launch {
            if (showAiInput && !aiInputSheetState.isVisible) {
                runCatching { aiInputSheetState.show() }
            }
            if (aiProcessingResult != null && !aiConfirmationSheetState.isVisible) {
                runCatching { aiConfirmationSheetState.show() }
            }
        }
        Unit
    }

    // Handlers for closing sheets with safety
    val dismissAiInput = {
        showDiscardDialog = true
        discardCallback = {
            showDiscardDialog = false
            // Cancel any in-flight AI request so a late result can't surface
            // a confirmation sheet after the user has chosen to discard.
            viewModel.cancelAiInput()
            scope.launch {
                runCatching {
                    if (aiInputSheetState.isVisible) aiInputSheetState.hide()
                }
            }.invokeOnCompletion {
                // Remove the sheet from composition after the hide animation
                // settles. We deliberately don't null `discardCallback` here:
                // a fresh dismiss flow may have taken over by the time this
                // fires, and clobbering it leaves the next Discard tap a no-op.
                showAiInput = false
            }
        }
    }

    val dismissAiConfirmation = {
        showDiscardDialog = true
        discardCallback = {
            showDiscardDialog = false
            scope.launch {
                runCatching {
                    if (aiConfirmationSheetState.isVisible) aiConfirmationSheetState.hide()
                }
            }.invokeOnCompletion {
                aiProcessingResult = null
                viewModel.clearAiResult()
            }
        }
    }

    LaunchedEffect(intent) {
        (context as? MainActivity)?.handleEmailLink(intent) { msg, type -> showNotification(msg, type) }
    }

    LaunchedEffect(aiResult) {
        aiResult?.let { result ->
            // Race guard: if the user is mid-discard of the input sheet, drop
            // the late AI result silently. Surfacing a confirmation sheet on
            // top of an open discard dialog leaves the user with a stale
            // dialog over a fresh sheet — the path that produced the
            // "stuck screen" report.
            if (showDiscardDialog && showAiInput) {
                viewModel.clearAiResult()
                return@let
            }
            if (result.isSuccess) {
                // Animate the input sheet out before removing it from composition.
                // Otherwise the scrim is left behind while the next sheet swaps in.
                runCatching {
                    if (aiInputSheetState.isVisible) aiInputSheetState.hide()
                }
                aiProcessingResult = result.getOrNull()
                showAiInput = false
                showNotification("AI processed input successfully!", NotificationType.SUCCESS)
            } else {
                showNotification(result.exceptionOrNull()?.message ?: "AI Error", NotificationType.ERROR)
                viewModel.clearAiResult()
            }
        }
    }

    BackHandler(enabled = activeView != ActiveView.DASHBOARD) { activeView = ActiveView.DASHBOARD }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        bottomBar = {
            if (activeView != ActiveView.ADD_SPEND) {
                NavigationBar(modifier = Modifier.navigationBarsPadding(), tonalElevation = 8.dp) {
                    NavigationBarItem(
                        selected = activeView == ActiveView.DASHBOARD,
                        onClick = { activeView = ActiveView.DASHBOARD },
                        icon = { Icon(Icons.Rounded.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = activeView == ActiveView.LEND_BORROW,
                        onClick = { activeView = ActiveView.LEND_BORROW },
                        icon = { Icon(Icons.Rounded.Handshake, contentDescription = "Lend & Borrow") },
                        label = { Text("Lend/Borrow") }
                    )
                    NavigationBarItem(
                        selected = activeView == ActiveView.HISTORY,
                        onClick = {
                            historySearchQuery = ""
                            historyCategoryFilter = "All"
                            historyTimeFilter = TimeFilter.ALL
                            activeView = ActiveView.HISTORY
                        },
                        icon = { Icon(Icons.Rounded.History, contentDescription = "Spending History") },
                        label = { Text("History") }
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeView == ActiveView.DASHBOARD || activeView == ActiveView.HISTORY || activeView == ActiveView.LEND_BORROW) {
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
                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    showAiInput = true
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                icon = { Icon(Icons.Rounded.AutoAwesome, "Track with AI") },
                                text = { Text("AI Track") }
                            )
                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    editingSpend = null
                                    activeView = ActiveView.ADD_SPEND
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                icon = { Icon(Icons.Rounded.Edit, "Track Manually") },
                                text = { Text("Manual") }
                            )
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        icon = { 
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.graphicsLayer { rotationZ = if (showFabMenu) 45f else 0f }) 
                        },
                        text = { Text(if (showFabMenu) "Close Tracking" else "Track Spend") },
                        containerColor = if (showFabMenu) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(6.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedContent(
                targetState = activeView,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "screen-switch"
            ) { view ->
                when (view) {
                    ActiveView.DASHBOARD -> DashboardScreen(
                        currentFilter = currentFilter,
                        analytics = analyticsState,
                        recentSpends = allSpends.filter { it.purpose != "Lending" && it.purpose != "Borrowing" }.take(5),
                        themePreference = themePreference,
                        onCycleTheme = onCycleTheme,
                        onFilterSelect = viewModel::setFilter,
                        onCustomRangeSelect = viewModel::setCustomRange,
                        onShowNotification = { msg, type -> showNotification(msg, type) },
                        onShowAllClick = {
                            historySearchQuery = ""
                            historyCategoryFilter = "All"
                            historyTimeFilter = TimeFilter.ALL
                            activeView = ActiveView.HISTORY
                        },
                        onAppClick = { appName ->
                            historySearchQuery = appName
                            historyCategoryFilter = "All"
                            historyTimeFilter = TimeFilter.ALL
                            activeView = ActiveView.HISTORY
                        },
                        onLentClick = {
                            activeView = ActiveView.LEND_BORROW
                        },
                        onTransactionsClick = {
                            historySearchQuery = ""
                            historyCategoryFilter = "All"
                            historyTimeFilter = currentFilter
                            activeView = ActiveView.HISTORY
                        },
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            showNotification("Logged out successfully", NotificationType.INFO)
                        },
                        onAiAssistantClick = { showAiHistoryAssistant = true },
                    )
                    ActiveView.LEND_BORROW -> LendBorrowScreen(
                        allSpends = allSpends,
                        onEditSpend = { spend ->
                            editingSpend = spend
                            activeView = ActiveView.ADD_SPEND
                        },
                        onDeleteSpend = { spend ->
                            viewModel.deleteSpend(spend)
                            showNotification("Record deleted", NotificationType.INFO)
                        },
                        onAiAssistantClick = { showAiHistoryAssistant = true },
                    )
                    ActiveView.HISTORY -> HistoryScreen(
                        allSpends = allSpends.filter { it.purpose != "Lending" && it.purpose != "Borrowing" },
                        initialSearchQuery = historySearchQuery,
                        initialCategoryFilter = historyCategoryFilter,
                        initialTimeFilter = historyTimeFilter,
                        initialDateRange = customDateRange,
                        onEditSpend = { spend ->
                            editingSpend = spend
                            activeView = ActiveView.ADD_SPEND
                        },
                        onDeleteSpend = { spend ->
                            viewModel.deleteSpend(spend)
                            showNotification("Spend deleted", NotificationType.INFO)
                        },
                        onBackClick = { activeView = ActiveView.DASHBOARD },
                        onAiAssistantClick = { showAiHistoryAssistant = true },
                    )
                    ActiveView.ADD_SPEND -> AddSpendScreen(
                        editingSpend = editingSpend,
                        onDismiss = { 
                            editingSpend = null
                            activeView = ActiveView.DASHBOARD 
                        },
                        onShowNotification = { msg, type -> showNotification(msg, type) },
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
                                showNotification("Spending updated successfully!", NotificationType.SUCCESS)
                            } else {
                                viewModel.addSpend(
                                    appName = appName,
                                    amount = newSpend.amount,
                                    purpose = newSpend.purpose,
                                    category = newSpend.preset.category,
                                    notes = newSpend.notes,
                                    timestamp = newSpend.timestamp
                                )
                                showNotification("Spending logged successfully!", NotificationType.SUCCESS)
                            }
                            editingSpend = null
                            activeView = ActiveView.DASHBOARD
                        }
                    )
                }
            }

            // Notification Banner Overlay
            AnimatedVisibility(
                visible = currentNotification != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                currentNotification?.let { (msg, type) ->
                    AppNotification(message = msg, type = type)
                }
            }
        }
    }

    // AI Sheets
    if (showAiInput) {
        AiInputBottomSheet(
            sheetState = aiInputSheetState,
            remainingRequests = 15 - aiPrefs.dailyUsageCount,
            onProcess = { viewModel.processAiInput(it) },
            onDismiss = dismissAiInput
        )
    }

    if (aiProcessingResult != null) {
        ModalBottomSheet(
            onDismissRequest = dismissAiConfirmation,
            sheetState = aiConfirmationSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AiConfirmationScreen(
                extractedData = aiProcessingResult!!,
                onShowNotification = { msg, type -> showNotification(msg, type) },
                onConfirm = { newSpend ->
                    viewModel.addSpend(
                        appName = if (newSpend.preset.id == "other") newSpend.customAppName else newSpend.preset.displayName,
                        amount = newSpend.amount,
                        purpose = newSpend.purpose,
                        category = newSpend.preset.category,
                        notes = newSpend.notes,
                        timestamp = newSpend.timestamp
                    )
                    showNotification("Logged via AI!", NotificationType.SUCCESS)
                    scope.launch {
                        runCatching {
                            if (aiConfirmationSheetState.isVisible) aiConfirmationSheetState.hide()
                        }
                    }.invokeOnCompletion {
                        aiProcessingResult = null
                        viewModel.clearAiResult()
                    }
                },
                onCancel = dismissAiConfirmation
            )
        }
    }

    if (showAiHistoryAssistant) {
        com.alpha.spendtracker.ui.components.AiHistoryAssistantSheet(
            messages = chatHistory,
            status = historyStatus,
            onSendMessage = { viewModel.askAiAboutHistory(it) },
            onDismiss = { showAiHistoryAssistant = false },
            sheetState = aiHistorySheetState
        )
    }

    // Discard Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = dismissDiscardDialog,
            title = { Text("Discard Spend?") },
            text = { Text("Are you sure you want to discard this spend? Your input will not be saved.") },
            confirmButton = {
                TextButton(
                    onClick = { discardCallback?.invoke() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = dismissDiscardDialog) { Text("Cancel") }
            }
        )
    }
}
