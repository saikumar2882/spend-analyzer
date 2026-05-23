package com.alpha.spendtracker.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alpha.spendtracker.data.AiTransactionResponse
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AiInputBottomSheet(
    isVoiceMode: Boolean,
    onProcess: (String) -> Unit,
    onDismiss: () -> Unit,
    remainingRequests: Int
) {
    var textInput by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceTimeLeft by remember { mutableIntStateOf(30) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val speechRecognizer = remember { 
        try {
            val appContext = context.applicationContext
            if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                android.util.Log.d("AiInputBottomSheet", "SpeechRecognizer created successfully with AppContext")
                recognizer
            } else {
                android.util.Log.e("AiInputBottomSheet", "Speech recognition NOT available via isRecognitionAvailable")
                errorMessage = "Speech services not found. Please install the Google App."
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AiInputBottomSheet", "Failed to create SpeechRecognizer", e)
            null
        }
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            errorMessage = null
            if (speechRecognizer != null) {
                isListening = true
                speechRecognizer.startListening(speechIntent)
            } else {
                errorMessage = "Speech recognition service not found"
            }
        } else {
            Toast.makeText(context, "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    val startListening = {
        android.util.Log.d("AiInputBottomSheet", "startListening called. speechRecognizer available: ${speechRecognizer != null}")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (speechRecognizer != null) {
                isListening = true
                errorMessage = null
                speechRecognizer.startListening(speechIntent)
            } else {
                errorMessage = "Speech recognition is not available on this device"
            }
        } else {
            android.util.Log.d("AiInputBottomSheet", "Requesting RECORD_AUDIO permission")
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(isListening) {
        if (isListening) {
            voiceTimeLeft = 30
            while (voiceTimeLeft > 0 && isListening) {
                delay(1000)
                voiceTimeLeft--
            }
            if (isListening) {
                isListening = false
                speechRecognizer?.stopListening()
            }
        }
    }

    DisposableEffect(speechRecognizer) {
        if (speechRecognizer == null) return@DisposableEffect onDispose {}
        
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.d("AiInputBottomSheet", "onReadyForSpeech")
            }
            override fun onBeginningOfSpeech() { 
                errorMessage = null 
                android.util.Log.d("AiInputBottomSheet", "onBeginningOfSpeech")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { 
                isListening = false 
                android.util.Log.d("AiInputBottomSheet", "onEndOfSpeech")
            }
            override fun onError(error: Int) { 
                isListening = false 
                android.util.Log.e("AiInputBottomSheet", "Speech recognition error: $error")
                errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error: $error"
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                android.util.Log.d("AiInputBottomSheet", "onResults: ${matches?.getOrNull(0)}")
                if (!matches.isNullOrEmpty()) {
                    onProcess(matches[0])
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    textInput = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        
        speechRecognizer.setRecognitionListener(listener)
        onDispose { 
            speechRecognizer.destroy() 
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isVoiceMode) "Voice Tracking" else "Tell AI about your spend",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Daily limit: $remainingRequests requests left",
                style = MaterialTheme.typography.labelSmall,
                color = if (remainingRequests < 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )

            if (isVoiceMode) {
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    CircularProgressIndicator(
                        progress = { if (isListening) voiceTimeLeft / 30f else 0f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                    IconButton(
                        onClick = {
                            if (isListening) {
                                isListening = false
                                speechRecognizer?.stopListening()
                            } else {
                                startListening()
                            }
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Microphone",
                            tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                if (isListening) {
                    Text("Listening... (${voiceTimeLeft}s left)", fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Tap the mic to start speaking", style = MaterialTheme.typography.bodyMedium)
                }

                if (textInput.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = textInput,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Automatically start listening if in voice mode and first time
                LaunchedEffect(Unit) {
                    if (isVoiceMode && !isListening && errorMessage == null) {
                        startListening()
                    }
                }
            } else {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { if (it.length <= 500) textInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., Spend 300 on biryani using phone pay") },
                    supportingText = { Text("${textInput.length}/500") },
                    minLines = 2,
                    trailingIcon = {
                        IconButton(
                            onClick = { if (textInput.isNotBlank()) onProcess(textInput) },
                            enabled = textInput.isNotBlank()
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = "Send")
                        }
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                )

                Text(
                    "Try one of these:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                val examples = listOf(
                    "Spend 300 on biryani using phone pay",
                    "Paid 250 for uber ride via gpay",
                    "Lent 1000 to a friend",
                    "Bought medicines worth 450 at apollo"
                )
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    examples.forEach { ex ->
                        SuggestionChip(
                            onClick = { textInput = ex },
                            label = { Text(ex, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
