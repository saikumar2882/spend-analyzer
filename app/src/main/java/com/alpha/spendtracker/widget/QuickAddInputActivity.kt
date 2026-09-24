package com.alpha.spendtracker.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alpha.spendtracker.MainActivity
import com.alpha.spendtracker.data.AiResultIntent
import com.alpha.spendtracker.data.AiTransactionProcessor
import com.alpha.spendtracker.ui.components.AiInputBottomSheet
import com.alpha.spendtracker.ui.components.VoiceInputOverlay
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.isDark
import com.alpha.spendtracker.ui.theme.rememberThemePreference
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * The home-screen widget's input surface: a translucent activity that floats the AI input
 * sheet or Voice input overlay over the wallpaper.
 *
 * An app widget can't host an editable field or interactive audio listener directly,
 * so tapping the widget opens this translucent overlay without opening the main app UI.
 */
@AndroidEntryPoint
class QuickAddInputActivity : ComponentActivity() {

    private val viewModel: QuickAddViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isVoiceLaunch = intent.getBooleanExtra("SHOW_VOICE_INPUT", false) || intent.extras?.containsKey("SHOW_VOICE_INPUT") == true

        // Nothing can be logged without an account, and the confirmation step lives behind
        // the app's auth gate anyway — send them straight to the app to sign in.
        if (FirebaseAuth.getInstance().currentUser == null) {
            val redirectKey = if (isVoiceLaunch) "SHOW_VOICE_INPUT" else "SHOW_AI_INPUT"
            startActivity(mainActivityIntent().putExtra(redirectKey, true))
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
                var isVoiceMode by remember { mutableStateOf(isVoiceLaunch) }

                if (isVoiceMode) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            viewModel.cancel()
                            finish()
                        },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        if (uiState.isProcessing) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                                Text(
                                    "AI is reading your voice input…",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            VoiceInputOverlay(
                                selectedLanguage = prefs.lastVoiceLanguage,
                                onLanguageChanged = viewModel::updateVoiceLanguage,
                                onFinalTranscript = { transcript ->
                                    if (transcript.isNotBlank()) {
                                        viewModel.process(transcript)
                                    }
                                },
                                onDismiss = {
                                    viewModel.cancel()
                                    finish()
                                }
                            )
                        }
                    }
                } else {
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
