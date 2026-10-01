package com.alpha.spendtracker.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.alpha.spendtracker.MainActivity
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.SharedPaymentText
import com.alpha.spendtracker.data.AiResultIntent
import com.alpha.spendtracker.data.AiTransactionProcessor
import com.alpha.spendtracker.ui.components.AiInputBottomSheet
import com.alpha.spendtracker.ui.components.VoiceInputOverlay
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.isDark
import com.alpha.spendtracker.ui.theme.rememberThemePreference
import com.alpha.spendtracker.util.VoiceInputHelper
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

    // Activity-level (not `remember`ed) so the permission callback can flip it.
    private var isVoiceMode by mutableStateOf(false)
    // True while the system mic-permission dialog is up, so the typed sheet doesn't flash behind it.
    private var awaitingMic by mutableStateOf(false)

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        awaitingMic = false
        if (granted) {
            isVoiceMode = true
        } else {
            Toast.makeText(this, R.string.voice_needs_mic, Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isVoiceLaunch = intent.getBooleanExtra("SHOW_VOICE_INPUT", false) || intent.extras?.containsKey("SHOW_VOICE_INPUT") == true
        // "Share → Spendly" from a payment app's receipt (via the ShareToSpendly alias).
        val sharedText = if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        } else null

        // Nothing can be logged without an account, and the confirmation step lives behind
        // the app's auth gate anyway — send them straight to the app to sign in.
        if (FirebaseAuth.getInstance().currentUser == null) {
            val redirectKey = if (isVoiceLaunch) "SHOW_VOICE_INPUT" else "SHOW_AI_INPUT"
            startActivity(mainActivityIntent().putExtra(redirectKey, true))
            finish()
            return
        }

        enableEdgeToEdge()

        // The widget's mic button and the "Voice" launcher shortcut both land here. Unlike the
        // Dashboard FAB, nothing asked for the microphone first, so a first-time user would just
        // see a speech error. Ask now; a "no" falls back to the typed sheet.
        if (isVoiceLaunch && sharedText == null) {
            val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            when {
                !VoiceInputHelper.isAvailable(this) ->
                    Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
                hasMic -> isVoiceMode = true
                else -> {
                    awaitingMic = true
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        if (sharedText != null && savedInstanceState == null) {
            val input = SharedPaymentText.toParserInput(sharedText, referrer?.host)
            if (input.isBlank()) {
                Toast.makeText(this, R.string.share_nothing_to_track, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            viewModel.processShared(input)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is QuickAddEffect.HandOff -> {
                            startActivity(AiResultIntent.put(mainActivityIntent(), effect.results))
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

                if (awaitingMic) {
                    // Nothing to draw: the system permission dialog is in front.
                } else if (sharedText != null && (uiState.isProcessing || uiState.errorMessage == null)) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            viewModel.cancel()
                            finish()
                        },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        ProcessingContent(stringResource(R.string.share_reading_payment))
                    }
                } else if (sharedText != null) {
                    // Parsing failed: fall back to the normal input sheet so they can type it.
                    AiInputBottomSheet(
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        remainingRequests = AiTransactionProcessor.DAILY_LIMIT - prefs.dailyUsageCount,
                        errorMessage = uiState.errorMessage?.let {
                            if (it == SHARE_NO_AMOUNT) stringResource(R.string.share_no_amount) else it
                        },
                        onProcess = viewModel::process,
                        onDismiss = {
                            viewModel.cancel()
                            finish()
                        }
                    )
                } else if (isVoiceMode) {
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
                            ProcessingContent(stringResource(R.string.ai_reading_input))
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

    @Composable
    private fun ProcessingContent(label: String) {
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
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
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
