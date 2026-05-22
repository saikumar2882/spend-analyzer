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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alpha.spendtracker.ui.screens.AddSpendScreen
import com.alpha.spendtracker.ui.screens.DashboardScreen
import com.alpha.spendtracker.ui.screens.HistoryScreen
import com.alpha.spendtracker.ui.screens.LoginScreen
import com.alpha.spendtracker.ui.screens.NewSpend
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
    var isAuthenticated by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }

    // Listen for auth changes
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            isAuthenticated = auth.currentUser != null
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(listener)
        }
    }

    if (!isAuthenticated) {
        LoginScreen(onLoginSuccess = { isAuthenticated = true })
        return
    }

    var activeView by rememberSaveable { mutableStateOf(ActiveView.DASHBOARD) }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }
    var historyCategoryFilter by rememberSaveable { mutableStateOf("All") }

    val allSpends by viewModel.allSpendsFlow.collectAsStateWithLifecycle()
    val analyticsState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
            if (activeView != ActiveView.ADD_SPEND) {
                ExtendedFloatingActionButton(
                    onClick = { activeView = ActiveView.ADD_SPEND },
                    icon = { Icon(Icons.Rounded.Add, "Add Spend") },
                    text = { Text("Track Spend") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
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
                        onDeleteSpend = viewModel::deleteSpend,
                        onBackClick = { activeView = ActiveView.DASHBOARD }
                    )
                    ActiveView.ADD_SPEND -> AddSpendScreen(
                        onDismiss = { activeView = ActiveView.DASHBOARD },
                        onSave = { newSpend: NewSpend ->
                            val appName = if (newSpend.preset.id == "other")
                                newSpend.customAppName.trim() else newSpend.preset.displayName
                            viewModel.addSpend(
                                appName = appName,
                                amount = newSpend.amount,
                                purpose = newSpend.purpose,
                                category = newSpend.preset.category,
                                notes = newSpend.notes,
                                timestamp = newSpend.timestamp
                            )
                            Toast.makeText(context, "Spending logged successfully!", Toast.LENGTH_SHORT).show()
                            activeView = ActiveView.DASHBOARD
                        }
                    )
                }
            }
        }
    }
}
