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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alpha.spendtracker.data.AiResultIntent
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.data.SyncStatus
import com.alpha.spendtracker.data.userMessageOrGeneric
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.AiConfirmationScreen
import com.alpha.spendtracker.ui.components.AiInputBottomSheet
import com.alpha.spendtracker.ui.components.AppNotification
import com.alpha.spendtracker.ui.components.BillTrackingBottomSheet
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.icons.AppIcons
import com.alpha.spendtracker.ui.screens.AddSpendScreen
import com.alpha.spendtracker.ui.screens.DashboardScreen
import com.alpha.spendtracker.ui.screens.HistoryScreen
import com.alpha.spendtracker.ui.screens.LendBorrowScreen
import com.alpha.spendtracker.ui.screens.LoginScreen
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.ui.screens.NotesHistoryScreen
import com.alpha.spendtracker.ui.screens.NotesScreen
import com.alpha.spendtracker.ui.screens.RecurringBillsScreen
import com.alpha.spendtracker.ui.screens.RegisterScreen
import com.alpha.spendtracker.ui.screens.SettingsScreen
import com.alpha.spendtracker.ui.screens.TransactionHistoryScreen
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.Spacing
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

enum class ActiveView { DASHBOARD, LEND_BORROW, HISTORY, HISTORY_TRASH, ADD_SPEND, LEND_BORROW_HISTORY, RECURRING_BILLS, NOTES, NOTES_HISTORY, SETTINGS }

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

    /**
     * Whether this device can actually authenticate (enrolled biometric or a device PIN/
     * pattern/password). Used to gate the one-time "enable app lock" offer so we never
     * pitch a feature the device can't back.
     */
    fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

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
                modifier = Modifier.fillMaxWidth(0.7f).heightIn(min = 56.dp),
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

    /**
     * Announces the outcome of a repository mutation. The confirmation is only shown when the write
     * actually landed — these call sites used to print "logged successfully!" the instant the
     * coroutine was launched, so a failed save looked identical to a successful one.
     *
     * A failure here means the *local* write failed and the action was lost. Cloud-sync trouble is
     * not an error: it surfaces separately, through the [SyncStatus] banner.
     */
    fun notifyResult(
        result: Result<Unit>,
        success: String,
        tone: NotificationType = NotificationType.SUCCESS
    ) {
        result.fold(
            onSuccess = { showNotification(success, tone) },
            onFailure = { showNotification(it.userMessageOrGeneric(), NotificationType.ERROR) }
        )
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

    // App-update check lives ABOVE the auth early-return so it runs regardless of
    // sign-in state — users on sideloaded/GitHub builds get prompted even on the
    // Sign In / Register screen. The dialog floats in its own window, so it overlays
    // whichever screen is showing. Runs once per app session; suppression is
    // per-version so a dismissed release is never re-prompted.
    val aiPrefs by viewModel.aiPreferences.collectAsStateWithLifecycle()
    var pendingUpdate by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    val updateChecker = remember { UpdateChecker(context) }

LaunchedEffect(Unit) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersion = packageInfo.versionName ?: "0.0.0"
        val update = updateChecker.checkForUpdates(currentVersion)
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

    if (currentUser == null || isRegistering) {
        val currentIsRegistering = isRegistering
        // Which auth screen is showing, and the email shared between them so it
        // carries over when the user switches Sign In <-> Register.
        var showRegister by rememberSaveable { mutableStateOf(false) }
        var authEmail by rememberSaveable { mutableStateOf("") }
        Box(modifier = Modifier.fillMaxSize()) {
            if (showRegister) {
                RegisterScreen(
                    initialEmail = authEmail,
                    onEmailChange = { authEmail = it },
                    onLoginSuccess = {
                        if (currentIsRegistering) {
                            isRegistering = false
                        }
                    },
                    onShowNotification = { msg, type -> showNotification(msg, type) },
                    onRegisteringStart = { isRegistering = true },
                    onRegisteringFinished = { isRegistering = false },
                    onNavigateToSignIn = { showRegister = false }
                )
            } else {
                LoginScreen(
                    initialEmail = authEmail,
                    onEmailChange = { authEmail = it },
                    onLoginSuccess = {
                        if (currentIsRegistering) {
                            isRegistering = false
                        }
                    },
                    onShowNotification = { msg, type -> showNotification(msg, type) },
                    onNavigateToRegister = { emailValue ->
                        authEmail = emailValue
                        showRegister = true
                    }
                )
            }

            // Notification overlay is also needed here — the auth screen returns
            // early, before the main content's notification banner is composed,
            // so without this, the auth screens' messages (validation errors,
            // "email sent", etc.) would be set but never displayed.
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
    // Where ADD_SPEND should return to on save/dismiss — the major screen it was opened from.
    var returnTo by rememberSaveable { mutableStateOf(ActiveView.DASHBOARD) }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var historyCategoryFilter by rememberSaveable { mutableStateOf("All") }
    var historyTimeFilter by rememberSaveable { mutableStateOf(TimeFilter.ALL) }
    var editingSpend by remember { mutableStateOf<Spend?>(null) }
    var prefilledBillSpend by remember { mutableStateOf<NewSpend?>(null) }
    var showBillTrackingSheet by remember { mutableStateOf(false) }
    // Set when a note-linked transaction is tapped in History; NotesScreen consumes it to
    // auto-open that note, then clears it.
    var pendingNoteUuid by rememberSaveable { mutableStateOf<String?>(null) }

    // Real back history across the major screens (Dashboard, Dues, History,
    // Recurring Bills, Notes, Settings) so system back retraces actual visits instead of
    // always jumping to Dashboard. Detail screens (ADD_SPEND and the trash/history
    // sub-screens) are not pushed here — they each have exactly one valid parent already.
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { it.map(ActiveView::name) },
            restore = { it.map(ActiveView::valueOf).toMutableStateList() }
        )
    ) { mutableStateListOf(ActiveView.DASHBOARD) }
    val goToMajor: (ActiveView) -> Unit = { view ->
        if (view == ActiveView.DASHBOARD) {
            backStack.clear()
            backStack.add(ActiveView.DASHBOARD)
        } else if (backStack.lastOrNull() != view) {
            // Remove previous occurrence of this view to keep the stack flat and unique.
            // This prevents "too much back" by ensuring a visit to a screen only appears once.
            backStack.removeAll { it == view }
            backStack.add(view)
            // Safety cap
            if (backStack.size > 15) backStack.removeAt(0)
        }
        activeView = view
    }
    val goBackMajor: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            activeView = backStack.last()
        }
    }

    val allSpends by viewModel.allSpendsFlow.collectAsStateWithLifecycle()
    val analyticsState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val aiResult by viewModel.aiResult.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val historyStatus by viewModel.historyStatus.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
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

    // Split trash/update history so the lend/borrow screen and the main-history trash each
    // show only their own records (SpendHistory carries the purpose used for lend/borrow).
    val lendBorrowDeleted = remember(deletedHistory) { deletedHistory.filter { it.purpose == "Lending" || it.purpose == "Borrowing" } }
    val lendBorrowUpdated = remember(updatedHistory) { updatedHistory.filter { it.purpose == "Lending" || it.purpose == "Borrowing" } }
    val regularDeleted = remember(deletedHistory) { deletedHistory.filter { it.purpose != "Lending" && it.purpose != "Borrowing" } }
    val regularUpdated = remember(updatedHistory) { updatedHistory.filter { it.purpose != "Lending" && it.purpose != "Borrowing" } }

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

    // One-time offer to turn on app lock, shown once after sign-in (like Google Pay's
    // first-run security prompt). Only for signed-in users who haven't already enabled it
    // and haven't been asked before, and only on devices that can actually authenticate.
    // The 600ms delay lets DataStore emit the real (possibly already-prompted) prefs first,
    // so a returning user never sees a flash of this dialog.
    var showEnableBiometricPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(currentUser, aiPrefs.isBiometricEnabled, aiPrefs.hasPromptedBiometric, pendingUpdate) {
        if (currentUser != null && !aiPrefs.isBiometricEnabled &&
            !aiPrefs.hasPromptedBiometric && pendingUpdate == null &&
            (context as? MainActivity)?.canAuthenticate() == true
        ) {
            delay(600)
            showEnableBiometricPrompt = true
        } else {
            showEnableBiometricPrompt = false
        }
    }

    val aiInputSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiConfirmationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiHistorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val billTrackingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val recurringBills by viewModel.recurringBills.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val noteEntries by viewModel.noteEntries.collectAsStateWithLifecycle()
    val noteDeletedHistory by viewModel.noteDeletedHistory.collectAsStateWithLifecycle()
    val noteUpdatedHistory by viewModel.noteUpdatedHistory.collectAsStateWithLifecycle()

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
        } else if (intent != null && AiResultIntent.isPresent(intent)) {
            // Parsed by the widget's overlay before the app was opened — go straight to
            // confirmation. The sheet stays gated behind the app lock below.
            aiProcessingResult = AiResultIntent.read(intent)
            AiResultIntent.clear(intent)
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

    BackHandler(enabled = activeView == ActiveView.ADD_SPEND || backStack.size > 1) {
        if (activeView == ActiveView.ADD_SPEND) {
            editingSpend = null
            prefilledBillSpend = null
            activeView = returnTo
        } else {
            goBackMajor()
        }
    }

    // Collapse the "Track Spend" FAB to an icon while scrolling down; expand on scroll up.
    var fabExpanded by remember { mutableStateOf(true) }
    // Hoisted out of the FAB slot so system back and a tap outside can dismiss the speed dial —
    // while it lived inside the slot there was no way to close it except tapping the FAB again.
    var showFabMenu by remember { mutableStateOf(false) }

    // Composed after the navigation handler, so it wins while the speed dial is open (BackHandlers
    // resolve last-registered-first).
    BackHandler(enabled = showFabMenu) { showFabMenu = false }
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
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (activeView != ActiveView.ADD_SPEND) {
                    NavigationBar(tonalElevation = 8.dp) {
                        NavigationBarItem(
                            selected = activeView == ActiveView.DASHBOARD,
                            onClick = { goToMajor(ActiveView.DASHBOARD) },
                            icon = { Icon(AppIcons.Dashboard, contentDescription = "Dashboard") },
                            label = { Text("Dashboard", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                        NavigationBarItem(
                            selected = activeView == ActiveView.LEND_BORROW,
                            onClick = { goToMajor(ActiveView.LEND_BORROW) },
                            icon = { Icon(Icons.Outlined.Handshake, contentDescription = "Dues — money lent and borrowed") },
                            label = { Text("Dues", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                        NavigationBarItem(
                            selected = activeView == ActiveView.HISTORY,
                            onClick = {
                                historySearchQuery = ""
                                historyCategoryFilter = "All"
                                historyTimeFilter = TimeFilter.ALL
                                goToMajor(ActiveView.HISTORY)
                            },
                            icon = { Icon(AppIcons.History, contentDescription = "Spending History") },
                            label = { Text("History", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                        NavigationBarItem(
                            selected = activeView == ActiveView.SETTINGS,
                            onClick = { goToMajor(ActiveView.SETTINGS) },
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                            label = { Text("Settings", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (activeView == ActiveView.DASHBOARD || activeView == ActiveView.HISTORY || activeView == ActiveView.LEND_BORROW || activeView == ActiveView.SETTINGS) {
                    Column(horizontalAlignment = Alignment.End) {
                        AnimatedVisibility(
                            visible = showFabMenu,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        showFabMenu = false
                                        showAiInput = true
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shadowElevation = 4.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(AppIcons.Ai, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Text("AI log", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                }

                                Surface(
                                    onClick = {
                                        showFabMenu = false
                                        editingSpend = null
                                        returnTo = activeView
                                        activeView = ActiveView.ADD_SPEND
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shadowElevation = 4.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Text("Manual", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }

                        val fabRotation by animateFloatAsState(
                            targetValue = if (showFabMenu) 45f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "fab_rotation"
                        )

                        val haptic = LocalHapticFeedback.current
                        Surface(
                            onClick = {
                                runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                showFabMenu = !showFabMenu
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (showFabMenu) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                            contentColor = if (showFabMenu) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = if (showFabMenu) "Close tracking menu" else "Track spend",
                                    modifier = Modifier
                                        .size(26.dp)
                                        .graphicsLayer { rotationZ = fabRotation }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
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
                            onCycleTheme = onCycleTheme,
                            onFilterSelect = viewModel::setFilter,
                            onCustomRangeSelect = viewModel::setCustomRange,
                            onShowNotification = { msg, type -> showNotification(msg, type) },
                            onShowAllClick = {
                                historySearchQuery = ""
                                historyCategoryFilter = "All"
                                historyTimeFilter = TimeFilter.ALL
                                goToMajor(ActiveView.HISTORY)
                            },
                            onAppClick = { appName ->
                                historySearchQuery = appName
                                historyCategoryFilter = "All"
                                historyTimeFilter = TimeFilter.ALL
                                goToMajor(ActiveView.HISTORY)
                            },
                            onLentClick = {
                                goToMajor(ActiveView.LEND_BORROW)
                            },
                            onTransactionsClick = {
                                historySearchQuery = ""
                                historyCategoryFilter = "All"
                                historyTimeFilter = currentFilter
                                goToMajor(ActiveView.HISTORY)
                            },
                            onAiAssistantClick = { showAiHistoryAssistant = true },
                            onNotesClick = { goToMajor(ActiveView.NOTES) },
                            onEditSpend = { spend ->
                                editingSpend = spend
                                returnTo = activeView
                                activeView = ActiveView.ADD_SPEND
                            },
                            onDeleteSpend = { spend ->
                                viewModel.deleteSpend(spend) {
                                    notifyResult(it, "Record moved to trash", NotificationType.INFO)
                                }
                            }
                        )
                        ActiveView.LEND_BORROW -> LendBorrowScreen(
                            allSpends = allSpends,
                            deletedHistory = lendBorrowDeleted,
                            updatedHistory = lendBorrowUpdated,
                            onEditSpend = { spend ->
                                editingSpend = spend
                                returnTo = activeView
                                activeView = ActiveView.ADD_SPEND
                            },
                            onDeleteSpend = { spend ->
                                viewModel.deleteSpend(spend) {
                                    notifyResult(it, "Record moved to trash", NotificationType.INFO)
                                }
                            },
                            onShowHistory = {
                                activeView = ActiveView.LEND_BORROW_HISTORY
                            }
                        )
                        ActiveView.LEND_BORROW_HISTORY -> com.alpha.spendtracker.ui.screens.LendBorrowHistoryScreen(
                            deletedHistory = lendBorrowDeleted,
                            updatedHistory = lendBorrowUpdated,
                            onRestoreHistory = { history ->
                                viewModel.restoreSpend(history) { notifyResult(it, "Record restored") }
                            },
                            onPermanentlyDeleteHistory = { history ->
                                viewModel.permanentlyDeleteHistory(history) {
                                    notifyResult(it, "Record deleted permanently", NotificationType.INFO)
                                }
                            },
                            onEmptyTrash = {
                                viewModel.emptyTrash(lendBorrow = true) {
                                    notifyResult(it, "Trash emptied", NotificationType.INFO)
                                }
                            },
                            onClearUpdateHistory = {
                                viewModel.clearUpdateHistory(lendBorrow = true) {
                                    notifyResult(it, "Update history cleared", NotificationType.INFO)
                                }
                            },
                            onBack = {
                                activeView = ActiveView.LEND_BORROW
                            }
                        )
                        ActiveView.HISTORY_TRASH -> TransactionHistoryScreen(
                            title = "Transaction History",
                            deletedHistory = regularDeleted,
                            updatedHistory = regularUpdated,
                            onRestoreHistory = { history ->
                                viewModel.restoreSpend(history) { notifyResult(it, "Record restored") }
                            },
                            onPermanentlyDeleteHistory = { history ->
                                viewModel.permanentlyDeleteHistory(history) {
                                    notifyResult(it, "Record deleted permanently", NotificationType.INFO)
                                }
                            },
                            onEmptyTrash = {
                                viewModel.emptyTrash(lendBorrow = false) {
                                    notifyResult(it, "Trash emptied", NotificationType.INFO)
                                }
                            },
                            onClearUpdateHistory = {
                                viewModel.clearUpdateHistory(lendBorrow = false) {
                                    notifyResult(it, "Update history cleared", NotificationType.INFO)
                                }
                            },
                            onBack = {
                                activeView = ActiveView.HISTORY
                            }
                        )
                        ActiveView.RECURRING_BILLS -> RecurringBillsScreen(
                            bills = recurringBills,
                            onBack = goBackMajor,
                            onAddBill = viewModel::addRecurringBill,
                            onUpdateBill = viewModel::updateRecurringBill,
                            onDeleteBill = viewModel::deleteRecurringBill
                        )
                        ActiveView.NOTES -> NotesScreen(
                            notes = notes,
                            entries = noteEntries,
                            // Prefer the user's default currency symbol; fall back to ₹ when it's
                            // blank or a multi-char code (e.g. "INR") so tiles stay clean.
                            currencySymbol = aiPrefs.defaultCurrency.let { if (it.isBlank() || it.length > 2) "₹" else it },
                            onBack = goBackMajor,
                            onAddNote = viewModel::addNote,
                            onUpdateNote = viewModel::updateNote,
                            onDeleteNote = viewModel::deleteNote,
                            onAddEntry = viewModel::addNoteEntry,
                            onUpdateEntry = viewModel::updateNoteEntry,
                            onDeleteEntry = viewModel::deleteNoteEntry,
                            onShowHistory = { activeView = ActiveView.NOTES_HISTORY },
                            onLogAsTransaction = { note ->
                                viewModel.logNoteAsTransaction(note, aiPrefs.defaultApp) { result ->
                                    result.fold(
                                        onSuccess = { outcome ->
                                            when (outcome) {
                                                SpendViewModel.LogNoteResult.EMPTY ->
                                                    showNotification("Add an entry with an amount first", NotificationType.INFO)
                                                SpendViewModel.LogNoteResult.CREATED ->
                                                    showNotification("Logged '${note.title}' to transactions", NotificationType.SUCCESS)
                                                SpendViewModel.LogNoteResult.UPDATED ->
                                                    showNotification("Updated '${note.title}' transaction", NotificationType.SUCCESS)
                                            }
                                        },
                                        onFailure = { showNotification(it.userMessageOrGeneric(), NotificationType.ERROR) }
                                    )
                                }
                            },
                            initialNoteUuid = pendingNoteUuid,
                            onInitialNoteConsumed = { pendingNoteUuid = null }
                        )
                        ActiveView.NOTES_HISTORY -> NotesHistoryScreen(
                            deletedHistory = noteDeletedHistory,
                            updatedHistory = noteUpdatedHistory,
                            currencySymbol = aiPrefs.defaultCurrency.let { if (it.isBlank() || it.length > 2) "₹" else it },
                            onRestore = { h ->
                                viewModel.restoreNoteHistory(h) { notifyResult(it, "Restored") }
                            },
                            onPermanentlyDelete = { h ->
                                viewModel.permanentlyDeleteNoteHistory(h) {
                                    notifyResult(it, "Deleted permanently", NotificationType.INFO)
                                }
                            },
                            onEmptyTrash = {
                                viewModel.emptyNoteTrash { notifyResult(it, "Trash emptied", NotificationType.INFO) }
                            },
                            onClearUpdateHistory = {
                                viewModel.clearNoteUpdateHistory {
                                    notifyResult(it, "Update history cleared", NotificationType.INFO)
                                }
                            },
                            onBack = { activeView = ActiveView.NOTES }
                        )
                        ActiveView.SETTINGS -> SettingsScreen(
                            themePreference = themePreference,
                            aiPreferences = aiPrefs,
                            onBack = goBackMajor,
                            onCycleTheme = onCycleTheme,
                            onShowNotification = { msg, type -> showNotification(msg, type) },
                            onUpdateAiPreferences = viewModel::updateAiPreferences,
                            onToggleBiometrics = viewModel::updateBiometricEnabled,
                            onAiAssistantClick = { showAiHistoryAssistant = true },
                            onRecurringBillsClick = { goToMajor(ActiveView.RECURRING_BILLS) },
                            onNotesClick = { goToMajor(ActiveView.NOTES) },
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
                                returnTo = activeView
                                activeView = ActiveView.ADD_SPEND
                            },
                            onDeleteSpend = { spend ->
                                viewModel.deleteSpend(spend) {
                                    notifyResult(it, "Spend deleted", NotificationType.INFO)
                                }
                            },
                            onShowHistory = { activeView = ActiveView.HISTORY_TRASH },
                            onOpenNote = { noteUuid ->
                                pendingNoteUuid = noteUuid
                                goToMajor(ActiveView.NOTES)
                            },
                            onShowNotification = { msg, type -> showNotification(msg, type) }
                        )
                        ActiveView.ADD_SPEND -> AddSpendScreen(
                            editingSpend = editingSpend,
                            prefilledSpend = prefilledBillSpend,
                            onDismiss = {
                                editingSpend = null
                                prefilledBillSpend = null
                                activeView = returnTo
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
                                    ) { notifyResult(it, "Spending updated successfully!") }
                                } else {
                                    viewModel.addSpend(
                                        appName = appName,
                                        amount = newSpend.amount,
                                        purpose = newSpend.purpose,
                                        category = newSpend.preset.category,
                                        notes = newSpend.notes,
                                        timestamp = newSpend.timestamp
                                    ) { notifyResult(it, "Spending logged successfully!") }
                                    // No need to manually clear prefilledBillSpend here as it's done in onDismiss
                                    // and we also clear it below
                                }
                                editingSpend = null
                                prefilledBillSpend = null
                                activeView = returnTo
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showFabMenu,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showFabMenu = false }
                    )
                }

                // Cloud-sync trouble is a standing condition, not a moment, so it gets a
                // persistent bar rather than a 3-second toast. Deliberately non-alarming: the
                // user's data is safe locally, it just isn't mirrored yet.
                AnimatedVisibility(
                    visible = syncStatus.isDegraded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SyncDegradedBar(syncStatus)
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
        // ModalBottomSheet renders in its own window, so it would float *above* LockedOverlay
        // and leak the parsed amount to whoever picked the phone up. Hold it until unlocked.
        if (currentAiConfirmationResult != null && (!needsBiometric || isBiometricAuthenticated)) {
            ModalBottomSheet(
                onDismissRequest = dismissAiConfirmation,
                sheetState = aiConfirmationSheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                AiConfirmationScreen(
                    extractedData = currentAiConfirmationResult,
                    defaultApp = aiPrefs.defaultApp,
                    defaultPurpose = aiPrefs.defaultPurpose,
                    currencySymbol = aiPrefs.defaultCurrency.let { if (it.isBlank() || it.length > 2) "₹" else it },
                    onShowNotification = { msg, type -> showNotification(msg, type) },
                    onConfirm = { newSpend ->
                        viewModel.addSpend(
                            appName = if (newSpend.preset.id == "other") newSpend.customAppName else newSpend.preset.displayName,
                            amount = newSpend.amount,
                            purpose = newSpend.purpose,
                            category = newSpend.preset.category,
                            notes = newSpend.notes,
                            timestamp = newSpend.timestamp
                        ) { notifyResult(it, "Logged via AI!") }
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
                    ) { notifyResult(it, "Bill payment logged!") }
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

        // First-run "enable app lock" offer (see the trigger effect above).
        if (showEnableBiometricPrompt) {
            AlertDialog(
                onDismissRequest = {
                    // Treat an outside tap / back as "Not now" — one-time offer, so record it.
                    viewModel.setBiometricPrompted()
                    showEnableBiometricPrompt = false
                },
                icon = { Icon(Icons.Rounded.Fingerprint, contentDescription = null) },
                title = { Text("Enable App Lock?") },
                text = {
                    Text(
                        "Protect your expenses with your fingerprint, face, or device PIN. " +
                        "Spendly will ask you to unlock it each time you open the app. " +
                        "You can change this anytime in Settings."
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showEnableBiometricPrompt = false
                        viewModel.setBiometricPrompted()
                        // Verify once before turning the lock on, and mark this session
                        // authenticated so the lock overlay doesn't immediately re-prompt.
                        (context as? MainActivity)?.showBiometricPrompt {
                            viewModel.setBiometricAuthenticated(true)
                            viewModel.updateBiometricEnabled(true)
                            showNotification("App lock enabled", NotificationType.SUCCESS)
                        }
                    }) { Text("Enable") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.setBiometricPrompted()
                        showEnableBiometricPrompt = false
                    }) { Text("Not Now") }
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

/**
 * "Saved on this device" bar shown while the Firestore push is failing.
 *
 * Uses the tertiary container rather than the error colours on purpose: nothing has been lost, so
 * treating it as an error would train people to ignore the real ones.
 */
@Composable
private fun SyncDegradedBar(status: SyncStatus) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(Sizes.iconInline)
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Text(
                text = status.cloudError?.userMessage ?: "Changes are saved on this device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
