package com.alpha.spendtracker.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alpha.spendtracker.MainActivity
import com.alpha.spendtracker.data.AiResultIntent
import com.alpha.spendtracker.data.AiTransactionProcessor
import com.alpha.spendtracker.ui.components.AiInputBottomSheet
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.isDark
import com.alpha.spendtracker.ui.theme.rememberThemePreference
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * The home-screen widget's input surface: a translucent activity that floats the AI input
 * sheet over the wallpaper.
 *
 * An app widget can't host an editable field — `RemoteViews` only inflates `@RemoteView`
 * classes and `EditText` isn't one, and Glance has no `TextField` — so tapping the widget
 * opens this instead of the app. It shows nothing but the input, so it is deliberately
 * **not** behind the biometric lock: there is no spend data on screen to protect. The lock
 * applies at the next step, when [MainActivity] opens with the parsed result to confirm.
 */
@AndroidEntryPoint
class QuickAddInputActivity : ComponentActivity() {

    private val viewModel: QuickAddViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nothing can be logged without an account, and the confirmation step lives behind
        // the app's auth gate anyway — send them straight to the app to sign in.
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(mainActivityIntent().putExtra("SHOW_AI_INPUT", true))
            finish()
            return
        }

        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is QuickAddEffect.HandOff -> {
                            startActivity(AiResultIntent.put(mainActivityIntent(), effect.result))
                            finish()
                        }
                    }
                }
            }
        }

        setContent {
            val themePref = rememberThemePreference()
            MyApplicationTheme(darkTheme = themePref.value.isDark()) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val prefs by viewModel.aiPreferences.collectAsStateWithLifecycle()

                AiInputBottomSheet(
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    remainingRequests = AiTransactionProcessor.DAILY_LIMIT - prefs.dailyUsageCount,
                    errorMessage = uiState.errorMessage,
                    onProcess = viewModel::process,
                    onDismiss = {
                        viewModel.cancel()
                        finish()
                    }
                )
            }
        }
    }

    /**
     * CLEAR_TOP + SINGLE_TOP so an app already in the background receives this through
     * `onNewIntent` instead of stacking a second MainActivity on top of itself.
     */
    private fun mainActivityIntent() = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}
