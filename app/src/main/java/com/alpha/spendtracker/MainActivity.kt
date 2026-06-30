/**
 * The main entry point for the Spend Tracker application, handling navigation and theme management.
 */
package com.alpha.spendtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.AiConfirmationScreen
import com.alpha.spendtracker.ui.components.AiInputBottomSheet
import com.alpha.spendtracker.ui.components.AppNotification
import com.alpha.spendtracker.ui.components.BillTrackingBottomSheet
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.screens.AddSpendScreen
import com.alpha.spendtracker.ui.screens.DashboardScreen
import com.alpha.spendtracker.ui.screens.HistoryScreen
import com.alpha.spendtracker.ui.screens.LendBorrowScreen
import com.alpha.spendtracker.ui.screens.LoginScreen
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.ui.screens.RecurringBillsScreen
import com.alpha.spendtracker.ui.screens.SettingsScreen
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.alpha.spendtracker.ui.theme.isDark
import com.alpha.spendtracker.ui.theme.next
import com.alpha.spendtracker.ui.theme.rememberThemePreference
import com.alpha.spendtracker.ui.viewmodel.SpendViewModel
import com.alpha.spendtracker.ui.viewmodel.TimeFilter
import com.alpha.spendtracker.utils.UpdateChecker
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ActiveView { DASHBOARD, LEND_BORROW, HISTORY, ADD_SPEND, LEND_BORROW_HISTORY, RECURRING_BILLS, SETTINGS }

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val spendViewModel: SpendViewModel by viewModels()
    private lateinit var appUpdateManager: AppUpdateManager
    private val MY_UPDATE_REQUEST_CODE = 1001

    private var isBiometricPromptShowing = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notifications will show
        }
    }

    private var _intentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        _intentState.value = intent
        checkNotificationPermission()

        appUpdateManager = AppUpdateManagerFactory.create(applicationContext)
        checkPlayStoreUpdate()

        enableEdgeToEdge()
        setContent {
            val themePref = rememberThemePreference()
            MyApplicationTheme(darkTheme = themePref.value.isDark()) {
                MainContainer(
                    viewModel = spendViewModel,
                    themePreference = themePref.value,
                    onCycleTheme = { themePref.value = themePref.value.next() },
                    intent = _intentState.value,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _intentState.value = intent
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability()
                    == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    // If an in-app update is already running, resume the update.
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        this,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                        MY_UPDATE_REQUEST_CODE
                    )
                }
            }
    }

    private fun checkPlayStoreUpdate() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    this,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    MY_UPDATE_REQUEST_CODE
                )
            }
        }
    }

    fun showBiometricPrompt(onSuccess: () -> Unit) {
        if (isBiometricPromptShowing) return
        isBiometricPromptShowing = true

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isBiometricPromptShowing = false
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isBiometricPromptShowing = false
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    isBiometricPromptShowing = false
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for Spendly")
            .setSubtitle("Log in using your biometric credential")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun handleEmailLink(intent: Intent?, onShowNotification: (String, NotificationType) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val link = intent?.data?.toString()
        if (link != null && (auth.isSignInWithEmailLink(link))) {
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

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun LockedOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
            .clickable(enabled = true, onClick = {}), // Intercept clicks
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                modifier = Modifier.size(90.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                "Spendly Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "Authentication required to access your financial dashboard.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = onUnlock,
                modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Icon(Icons.Rounded.Fingerprint, null)
                Spacer(Modifier.width(12.dp))
                Text("Unlock with Biometrics")
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
    var isRegistering by remember { mutableStateOf(value = false) }
    
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
        val currentIsRegistering = isRegistering
        LoginScreen(
            onLoginSuccess = { 
                if (currentIsRegistering) {
                    isRegistering = false
                }
            },
            onShowNotification = { msg, type -> showNotification(msg, type) },
            onRegisteringStart = { 
                isRegistering = true 
            },
            onRegisteringFinished = { 
                isRegistering = false 
            }
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
    var historyTimeFilter by rememberSaveable { mutableStateOf(TimeFilter.ALL) }
    var editingSpend by remember { mutableStateOf<Spend?>(null) }
    var prefilledBillSpend by remember { mutableStateOf<NewSpend?>(null) }
    var showBillTrackingSheet by remember { mutableStateOf(false) }

    val allSpends by viewModel.allSpendsFlow.collectAsStateWithLifecycle()
    val analyticsState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val aiPrefs by viewModel.aiPreferences.collectAsStateWithLifecycle()
    val aiResult by viewModel.aiResult.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val historyStatus by viewModel.historyStatus.collectAsStateWithLifecycle()
    val deletedHistory by viewModel.deletedHistory.collectAsStateWithLifecycle()
    val updatedHistory by viewModel.updatedHistory.collectAsStateWithLifecycle()

    // Memoize derived spend lists so they aren't reallocated on every recomposition of
    // MainContainer. Reallocating would hand a new list instance to DashboardScreen/HistoryScreen
    // each frame, defeating Compose's skipping.
    val nonLendBorrowSpends = remember(allSpends) {
        allSpends.filter { it.purpose != "Lending" && it.purpose != "Borrowing" }
    }
    val recentSpends = remember(nonLendBorrowSpends) {
        nonLendBorrowSpends.take(5)
    }

    var pendingUpdate by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    val updateChecker = remember { UpdateChecker(context) }

    // Check once per signed-in session. Suppression is per-version (not time-based): we read the
    // dismissed version inside without keying on it, so dismissing the dialog doesn't re-fire the
    // network check — the user is only ever prompted once for a given release.
    LaunchedEffect(currentUser) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersion = packageInfo.versionName ?: "0.0.0"

        val update = updateChecker.checkForUpdates(currentVersion)
        // Only surface the dialog if a strictly newer version exists AND it isn't one the user
        // already dismissed or downloaded.
        if (update != null && update.version != aiPrefs.dismissedUpdateVersion) {
            pendingUpdate = update
        }
    }

    val update = pendingUpdate
    if (update != null) {
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = { Text("New Update Available") },
            text = { Text("Version ${update.version} of Spendly is available on GitHub. Would you like to download it now?") },
            confirmButton = {
                Button(onClick = {
                    // Remember this version so we don't prompt again after the user heads off to install it.
                    viewModel.dismissUpdateVersion(update.version)
                    updateChecker.openUpdateUrl(update.downloadUrl)
                    pendingUpdate = null
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissUpdateVersion(update.version)
                    pendingUpdate = null
                }) { Text("Later") }
            }
        )
    }

    val isBiometricAuthenticated by viewModel.isBiometricAuthenticated.collectAsStateWithLifecycle()
    val needsBiometric = aiPrefs.isBiometricEnabled

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (aiPrefs.isBiometricEnabled) {
            viewModel.setBiometricAuthenticated(false)
        }
    }

    LaunchedEffect(currentUser, aiPrefs.isBiometricEnabled, isBiometricAuthenticated) {
        if (needsBiometric && !isBiometricAuthenticated) {
            (context as? MainActivity)?.showBiometricPrompt {
                viewModel.setBiometricAuthenticated(true)
            }
        }
    }
    
    val aiInputSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiConfirmationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiHistorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val billTrackingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val recurringBills by viewModel.recurringBills.collectAsStateWithLifecycle()

    LaunchedEffect(intent) {
        if (intent?.hasExtra("BILL_UUID") == true) {
            if (com.alpha.spendtracker.BuildConfig.DEBUG) android.util.Log.d("MainActivity", "Processing bill notification: ${intent.getStringExtra("BILL_UUID")}")
            val billName = intent.getStringExtra("BILL_NAME") ?: ""
            val billApp = intent.getStringExtra("BILL_APP") ?: ""
            val billPurpose = intent.getStringExtra("BILL_PURPOSE") ?: ""
            val billCategory = intent.getStringExtra("BILL_CATEGORY") ?: ""
            val billNotes = intent.getStringExtra("BILL_NOTES") ?: ""

            val appPreset = com.alpha.spendtracker.ui.components.APP_PRESETS.find { it.displayName == billApp } ?: com.alpha.spendtracker.ui.components.APP_PRESETS.last()

            prefilledBillSpend = NewSpend(
                preset = appPreset,
                amount = 0.0,
                purpose = billPurpose,
                notes = billNotes,
                customAppName = if (appPreset.id == "other") billApp else "",
                timestamp = System.currentTimeMillis()
            )
            editingSpend = null
            showBillTrackingSheet = true
            
            // Clear extras to avoid re-triggering on rotation/recomposition
            intent.removeExtra("BILL_UUID")
        } else if (intent?.getBooleanExtra("SHOW_AI_INPUT", false) == true) {
            android.util.Log.d("MainActivity", "Showing AI input via widget.")
            showAiInput = true
            intent.removeExtra("SHOW_AI_INPUT")
        }
    }

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
            if (showBillTrackingSheet && !billTrackingSheetState.isVisible) {
                runCatching { billTrackingSheetState.show() }
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
                aiProcessingResult = null
                viewModel.clearAiResult()
            }
        }
    }

    val dismissBillTracking = {
        showDiscardDialog = true
        discardCallback = {
            showDiscardDialog = false
            scope.launch {
                runCatching {
                    if (billTrackingSheetState.isVisible) billTrackingSheetState.hide()
                }
                showBillTrackingSheet = false
                prefilledBillSpend = null
            }
        }
    }

    LaunchedEffect(intent) {
        (context as? MainActivity)?.handleEmailLink(intent) { msg, type -> showNotification(msg, type) }
    }

    LaunchedEffect(aiResult) {
        aiResult?.let { result ->
            if (showDiscardDialog && showAiInput) {
                viewModel.clearAiResult()
                return@let
            }
            if (result.isSuccess) {
                val extracted = result.getOrNull()
                
                // If biometric is enabled and user is not authenticated, prompt now
                if (aiPrefs.isBiometricEnabled && !isBiometricAuthenticated) {
                    (context as? MainActivity)?.showBiometricPrompt {
                        viewModel.setBiometricAuthenticated(true)
                        // Once authenticated, proceed to show confirmation
                        scope.launch {
                            runCatching {
                                if (aiInputSheetState.isVisible) aiInputSheetState.hide()
                            }
                            aiProcessingResult = extracted
                            showAiInput = false
                        }
                        showNotification("Authenticated! Check AI results.", NotificationType.SUCCESS)
                    }
                } else {
                    // Already authenticated or biometrics disabled
                    scope.launch {
                        runCatching {
                            if (aiInputSheetState.isVisible) aiInputSheetState.hide()
                        }
                        aiProcessingResult = extracted
                        showAiInput = false
                    }
                    showNotification("AI processed input successfully!", NotificationType.SUCCESS)
                }
            } else {
                showNotification(result.exceptionOrNull()?.message ?: "AI Error", NotificationType.ERROR)
                viewModel.clearAiResult()
            }
        }
    }

    BackHandler(enabled = activeView != ActiveView.DASHBOARD) { activeView = ActiveView.DASHBOARD }

    // Collapse the "Track Spend" FAB to an icon while scrolling down; expand on scroll up.
    var fabExpanded by remember { mutableStateOf(true) }
    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) fabExpanded = false
                else if (available.y > 1f) fabExpanded = true
                return Offset.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            expanded = fabExpanded || showFabMenu,
                            icon = {
                                Icon(Icons.Rounded.Add, null, modifier = Modifier.graphicsLayer { rotationZ = if (showFabMenu) 45f else 0f })
                            },
                            text = { Text(if (showFabMenu) "Close Tracking" else "Track Spend") },
                            containerColor = if (showFabMenu) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                            elevation = FloatingActionButtonDefaults.elevation(6.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(fabScrollConnection)
            ) {
                AnimatedContent(
                    targetState = activeView,
                    transitionSpec = {
                        // Shared-axis style: a subtle slide along X + fade. Direction follows the
                        // enum ordinal so moving "forward" slides in from the right, "back" from
                        // the left. The incoming screen enters from one side while the outgoing one
                        // exits to the other, giving navigation a spatial feel.
                        val forward = targetState.ordinal >= initialState.ordinal
                        // Slide ~30% of the screen width so it reads as motion without a full swipe.
                        val enter = slideInHorizontally(animationSpec = tween(280)) { fullWidth ->
                            if (forward) fullWidth / 3 else -fullWidth / 3
                        } + fadeIn(tween(280))
                        val exit = slideOutHorizontally(animationSpec = tween(250)) { fullWidth ->
                            if (forward) -fullWidth / 3 else fullWidth / 3
                        } + fadeOut(tween(200))
                        enter togetherWith exit
                    },
                    label = "screen-switch"
                ) { view ->
                    when (view) {
                        ActiveView.DASHBOARD -> DashboardScreen(
                            currentFilter = currentFilter,
                            analytics = analyticsState,
                            recentSpends = recentSpends,
                            themePreference = themePreference,
                            aiPreferences = aiPrefs,
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
                            onUpdateAiPreferences = viewModel::updateAiPreferences,
                            onToggleBiometrics = viewModel::updateBiometricEnabled,
                            onShareApp = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Take control of your finances with Spendly! 🚀\n\nDownload the latest version here: https://github.com/saikumar2882/spend-analyzer/releases/latest")
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Spendly via")
                                context.startActivity(shareIntent)
                            },
                            onRecurringBillsClick = { activeView = ActiveView.RECURRING_BILLS },
                            onSettingsClick = { activeView = ActiveView.SETTINGS }
                        )
                        ActiveView.LEND_BORROW -> LendBorrowScreen(
                            allSpends = allSpends,
                            deletedHistory = deletedHistory,
                            updatedHistory = updatedHistory,
                            onEditSpend = { spend ->
                                editingSpend = spend
                                activeView = ActiveView.ADD_SPEND
                            },
                            onDeleteSpend = { spend ->
                                viewModel.deleteSpend(spend)
                                showNotification("Record moved to trash", NotificationType.INFO)
                            },
                            onShowHistory = {
                                activeView = ActiveView.LEND_BORROW_HISTORY
                            }
                        )
                        ActiveView.LEND_BORROW_HISTORY -> com.alpha.spendtracker.ui.screens.LendBorrowHistoryScreen(
                            deletedHistory = deletedHistory,
                            updatedHistory = updatedHistory,
                            onRestoreHistory = { history ->
                                viewModel.restoreSpend(history)
                                showNotification("Record restored", NotificationType.SUCCESS)
                            },
                            onPermanentlyDeleteHistory = { history ->
                                viewModel.permanentlyDeleteHistory(history)
                                showNotification("Record deleted permanently", NotificationType.INFO)
                            },
                            onEmptyTrash = {
                                viewModel.emptyTrash()
                                showNotification("Trash emptied", NotificationType.INFO)
                            },
                            onClearUpdateHistory = {
                                viewModel.clearUpdateHistory()
                                showNotification("Update history cleared", NotificationType.INFO)
                            },
                            onBack = {
                                activeView = ActiveView.LEND_BORROW
                            }
                        )
                        ActiveView.RECURRING_BILLS -> RecurringBillsScreen(
                            bills = recurringBills,
                            onBack = { activeView = ActiveView.DASHBOARD },
                            onAddBill = viewModel::addRecurringBill,
                            onUpdateBill = viewModel::updateRecurringBill,
                            onDeleteBill = viewModel::deleteRecurringBill
                        )
                        ActiveView.SETTINGS -> SettingsScreen(
                            themePreference = themePreference,
                            aiPreferences = aiPrefs,
                            onBack = { activeView = ActiveView.DASHBOARD },
                            onCycleTheme = onCycleTheme,
                            onShowNotification = { msg, type -> showNotification(msg, type) },
                            onUpdateAiPreferences = viewModel::updateAiPreferences,
                            onToggleBiometrics = viewModel::updateBiometricEnabled,
                            onAiAssistantClick = { showAiHistoryAssistant = true },
                            onRecurringBillsClick = { activeView = ActiveView.RECURRING_BILLS },
                            onShareApp = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Take control of your finances with Spendly! 🚀\n\nDownload the latest version here: https://github.com/saikumar2882/spend-analyzer/releases/latest")
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Spendly via")
                                context.startActivity(shareIntent)
                            },
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                showNotification("Logged out successfully", NotificationType.INFO)
                            }
                        )
                        ActiveView.HISTORY -> HistoryScreen(
                            allSpends = nonLendBorrowSpends,
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
                            }
                        )
                        ActiveView.ADD_SPEND -> AddSpendScreen(
                            editingSpend = editingSpend,
                            prefilledSpend = prefilledBillSpend,
                            onDismiss = { 
                                editingSpend = null
                                prefilledBillSpend = null
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
                                    // No need to manually clear prefilledBillSpend here as it's done in onDismiss
                                    // and we also clear it below
                                    showNotification("Spending logged successfully!", NotificationType.SUCCESS)
                                }
                                editingSpend = null
                                prefilledBillSpend = null
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

        val currentAiConfirmationResult = aiProcessingResult
        if (currentAiConfirmationResult != null) {
            ModalBottomSheet(
                onDismissRequest = dismissAiConfirmation,
                sheetState = aiConfirmationSheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                AiConfirmationScreen(
                    extractedData = currentAiConfirmationResult,
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
                            aiProcessingResult = null
                            viewModel.clearAiResult()
                        }
                    },
                    onCancel = dismissAiConfirmation
                )
            }
        }

        val currentBillToTrack = prefilledBillSpend
        if (showBillTrackingSheet && currentBillToTrack != null) {
            BillTrackingBottomSheet(
                show = showBillTrackingSheet,
                sheetState = billTrackingSheetState,
                prefilledSpend = currentBillToTrack,
                onConfirm = { newSpend ->
                    viewModel.addSpend(
                        appName = if (newSpend.preset.id == "other") newSpend.customAppName else newSpend.preset.displayName,
                        amount = newSpend.amount,
                        purpose = newSpend.purpose,
                        category = newSpend.preset.category,
                        notes = newSpend.notes,
                        timestamp = newSpend.timestamp
                    )
                    showNotification("Bill payment logged!", NotificationType.SUCCESS)
                    scope.launch {
                        runCatching {
                            if (billTrackingSheetState.isVisible) billTrackingSheetState.hide()
                        }
                        showBillTrackingSheet = false
                        prefilledBillSpend = null
                    }
                },
                onCancel = dismissBillTracking,
                onDismissRequest = dismissBillTracking
            )
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

        if (needsBiometric && !isBiometricAuthenticated) {
            LockedOverlay {
                (context as? MainActivity)?.showBiometricPrompt {
                    viewModel.setBiometricAuthenticated(true)
                }
            }
        }
    }
}
